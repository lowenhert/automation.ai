package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.DeviceReservations;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Runs user-approved scripts on a selected device. Safeguards:
 * <ul>
 *   <li>only APPROVED scripts whose content still matches the approved and validated hash, and only when the caller
 *       confirms the exact content hash they reviewed; the script is re-validated right before it starts</li>
 *   <li>one controlling activity per device (shared with exploration)</li>
 *   <li>a fixed interpreter command (no shell), a per-run working directory, a scrubbed environment without backend
 *       secrets, a hard timeout and a log size limit; the whole process tree is killed on stop/timeout</li>
 * </ul>
 * Generated code is never started automatically: execution always needs an explicit request from a user.
 */
@Slf4j
@Service
public class ScriptExecutionService {

    static final Pattern ARTIFACT_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,100}\\.png$");
    static final int LOG_TAIL_CHARS = 20_000;
    static final int MAX_LOG_CHUNK = 64 * 1024;
    static final int MAX_MESSAGE = 1000;
    static final String LOG_FILE = "output.log";
    static final String ARTIFACT_DIR = "artifacts";
    private static final List<String> INHERITED_ENV = List.of("PATH", "PATHEXT", "SYSTEMROOT", "SYSTEMDRIVE", "WINDIR",
            "COMSPEC", "NUMBER_OF_PROCESSORS", "PROCESSOR_ARCHITECTURE", "OS", "LANG");

    private final ScriptExecutionRepository executionRepository;
    private final TestScriptRepository scriptRepository;
    private final ScriptValidator validator;
    private final ScreenLearningAdbService adbService;
    private final DeviceReservations reservations;
    private final TestEngineSettings settings;
    private final ObjectMapper objectMapper;

    private final Map<Long, Running> running = new ConcurrentHashMap<>();
    private final ExecutorService monitors = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "script-execution");
        t.setDaemon(true);
        return t;
    });

    private enum Outcome { STOP_REQUESTED, TIMED_OUT, LOG_LIMIT, SHUTDOWN }

    private record Running(Process process, String serial, String owner, AtomicReference<Outcome> forced) {
    }

    public ScriptExecutionService(ScriptExecutionRepository executionRepository, TestScriptRepository scriptRepository,
                                  ScriptValidator validator, ScreenLearningAdbService adbService,
                                  DeviceReservations reservations, TestEngineSettings settings, ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.scriptRepository = scriptRepository;
        this.validator = validator;
        this.adbService = adbService;
        this.reservations = reservations;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    // ── Start / stop ────────────────────────────────────────────────────────

    public ExecutionDto start(ExecuteRequest request, String username) {
        if (request == null || request.scriptId() == null) throw new BadRequestException("scriptId is required");
        if (!Boolean.TRUE.equals(request.confirm())) {
            throw new BadRequestException("Execution must be confirmed explicitly (confirm=true)");
        }
        if (request.deviceId() == null || request.deviceId().isBlank()) throw new BadRequestException("deviceId is required");
        int timeout = request.timeoutSeconds() == null ? settings.getDefaultTimeoutSeconds() : request.timeoutSeconds();
        if (timeout < 10 || timeout > settings.getMaxTimeoutSeconds()) {
            throw new BadRequestException("timeoutSeconds must be between 10 and " + settings.getMaxTimeoutSeconds());
        }
        TestScript script = scriptRepository.findById(request.scriptId())
                .orElseThrow(() -> new ResourceNotFoundException("Script " + request.scriptId() + " not found"));
        if (request.contentHash() == null || !request.contentHash().equals(script.getContentHash())) {
            throw new ConflictException("Script content differs from the version you reviewed; reload it before executing");
        }
        if (!script.isExecutable()) {
            throw new BadRequestException("Only validated scripts approved in their current form can be executed");
        }
        Validation validation = validator.validate(script.getLanguage(), script.getContent());
        if (!validation.valid()) {
            String first = validation.errors().isEmpty() ? "invalid" : "line " + validation.errors().get(0).line()
                    + ": " + validation.errors().get(0).message();
            throw new BadRequestException("Script failed validation and was not executed (" + first + ")");
        }
        String serial = adbService.requireOnlineDevice(request.deviceId()).getSerial();

        String owner = "script execution " + UUID.randomUUID();
        if (!reservations.tryReserve(serial, owner)) {
            throw new ConflictException("Device " + serial + " is busy (" + reservations.owner(serial).orElse("in use")
                    + "); wait for it to finish or stop it");
        }
        ScriptExecution execution = null;
        try {
            execution = executionRepository.save(ScriptExecution.builder()
                    .scriptId(script.getId())
                    .deviceSerial(serial)
                    .language(script.getLanguage())
                    .contentHash(script.getContentHash())
                    .status(ScriptExecution.Status.RUNNING)
                    .timeoutSeconds(timeout)
                    .startedBy(username)
                    .startedAt(Instant.now())
                    .build());
            Path runDir = prepareRunDir(execution.getId(), script);
            ProcessBuilder pb = new ProcessBuilder(command(script.getLanguage(), runDir))
                    .directory(runDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(runDir.resolve(LOG_FILE).toFile());
            configureEnvironment(pb.environment(), serial, runDir);
            Process process = pb.start();
            process.getOutputStream().close();
            Running run = new Running(process, serial, owner, new AtomicReference<>());
            running.put(execution.getId(), run);
            log.info("Execution {} of script {} started on {} by {} (timeout {}s)", execution.getId(), script.getId(),
                    serial, username, timeout);
            Long id = execution.getId();
            monitors.submit(() -> monitor(id, run, runDir, timeout));
            return toDto(execution);
        } catch (IOException | RuntimeException e) {
            reservations.release(serial, owner);
            if (execution != null && execution.getId() != null) {
                running.remove(execution.getId());
                execution.setStatus(ScriptExecution.Status.ERROR);
                execution.setErrorMessage(truncate("Could not start: " + e.getMessage()));
                execution.setEndedAt(Instant.now());
                executionRepository.save(execution);
            }
            if (e instanceof RuntimeException re) throw re;
            log.error("Unable to start execution: {}", e.getMessage());
            throw new ServiceUnavailableException("Could not start the test run. Check 'app.screen-learning.python-path'.");
        }
    }

    public ExecutionDto stop(Long id) {
        ScriptExecution execution = find(id);
        Running run = running.get(id);
        if (run != null) {
            run.forced().compareAndSet(null, Outcome.STOP_REQUESTED);
            killTree(run.process());
            log.info("Execution {} stop requested", id);
        } else if (execution.getStatus() == ScriptExecution.Status.RUNNING) {
            execution.setStatus(ScriptExecution.Status.INTERRUPTED);
            execution.setErrorMessage("No running process found for this execution");
            execution.setEndedAt(Instant.now());
            executionRepository.save(execution);
        }
        return toDto(find(id));
    }

    private void monitor(Long id, Running run, Path runDir, int timeoutSeconds) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        Path logFile = runDir.resolve(LOG_FILE);
        try {
            while (!run.process().waitFor(500, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) {
                    run.forced().compareAndSet(null, Outcome.TIMED_OUT);
                    killTree(run.process());
                } else if (sizeOf(logFile) > settings.getMaxLogBytes()) {
                    run.forced().compareAndSet(null, Outcome.LOG_LIMIT);
                    killTree(run.process());
                }
            }
            finish(id, run, runDir);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killTree(run.process());
        } catch (RuntimeException e) {
            log.error("Execution {} monitor failed", id, e);
            killTree(run.process());
            executionRepository.findById(id).ifPresent(ex -> {
                ex.setStatus(ScriptExecution.Status.ERROR);
                ex.setErrorMessage(truncate("Internal error: " + e.getMessage()));
                ex.setEndedAt(Instant.now());
                executionRepository.save(ex);
            });
        } finally {
            running.remove(id);
            reservations.release(run.serial(), run.owner());
        }
    }

    private void finish(Long id, Running run, Path runDir) {
        ScriptExecution execution = executionRepository.findById(id).orElse(null);
        if (execution == null) return;
        int exit = run.process().exitValue();
        List<TestResult> results = parseResults(execution.getLanguage(), runDir);
        int passed = (int) results.stream().filter(r -> "PASSED".equals(r.status())).count();
        int failed = (int) results.stream().filter(r -> "FAILED".equals(r.status()) || "ERROR".equals(r.status())).count();
        int skipped = (int) results.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        execution.setExitCode(exit);
        execution.setTestsTotal(results.size());
        execution.setTestsPassed(passed);
        execution.setTestsFailed(failed);
        execution.setTestsSkipped(skipped);
        execution.setResultsJson(write(results));
        execution.setArtifactsJson(write(listScreenshots(runDir)));
        execution.setLogTail(tail(runDir.resolve(LOG_FILE)));
        execution.setEndedAt(Instant.now());

        Outcome forced = run.forced().get();
        if (forced == Outcome.STOP_REQUESTED) {
            execution.setStatus(ScriptExecution.Status.STOPPED);
            execution.setErrorMessage("Stopped by user");
        } else if (forced == Outcome.TIMED_OUT) {
            execution.setStatus(ScriptExecution.Status.TIMED_OUT);
            execution.setErrorMessage("Timed out after " + execution.getTimeoutSeconds() + " s; the process was killed");
        } else if (forced == Outcome.LOG_LIMIT) {
            execution.setStatus(ScriptExecution.Status.ERROR);
            execution.setErrorMessage("Log output exceeded " + settings.getMaxLogBytes() + " bytes; the process was killed");
        } else if (forced == Outcome.SHUTDOWN) {
            execution.setStatus(ScriptExecution.Status.INTERRUPTED);
            execution.setErrorMessage("Backend shut down during execution");
        } else if (results.isEmpty()) {
            execution.setStatus(ScriptExecution.Status.ERROR);
            execution.setErrorMessage(truncate("No test results were produced (exit code " + exit + "). " + lastLines(execution.getLogTail())));
        } else if (failed > 0) {
            execution.setStatus(ScriptExecution.Status.FAILED);
            execution.setErrorMessage(results.stream().filter(r -> r.message() != null && !"PASSED".equals(r.status()))
                    .map(r -> truncate(r.name() + ": " + r.message())).findFirst().orElse(failed + " test(s) failed"));
        } else if (exit == 0) {
            execution.setStatus(passed > 0 ? ScriptExecution.Status.PASSED : ScriptExecution.Status.ERROR);
            if (passed == 0) execution.setErrorMessage("All tests were skipped");
        } else {
            execution.setStatus(ScriptExecution.Status.ERROR);
            execution.setErrorMessage(truncate("Test runner exited with code " + exit + ". " + lastLines(execution.getLogTail())));
        }
        executionRepository.save(execution);
        log.info("Execution {} finished: {} (exit {}, {}/{} passed)", id, execution.getStatus(), exit, passed, results.size());
    }

    // ── Process setup ───────────────────────────────────────────────────────

    private Path prepareRunDir(Long id, TestScript script) throws IOException {
        Path runDir = runDir(id);
        Files.createDirectories(runDir.resolve(ARTIFACT_DIR));
        Files.createDirectories(runDir.resolve("tmp"));
        Files.writeString(runDir.resolve(scriptFile(script.getLanguage())), script.getContent(), StandardCharsets.UTF_8);
        if (script.getLanguage() == TestScript.Language.PYTEST) {
            // Stops pytest from picking up ini files or conftest.py from parent directories
            Files.writeString(runDir.resolve("pytest.ini"), "[pytest]\n", StandardCharsets.UTF_8);
        }
        return runDir;
    }

    List<String> command(TestScript.Language language, Path runDir) {
        List<String> cmd = new ArrayList<>(List.of(settings.getPythonPath(), "-B", "-s", "-m"));
        if (language == TestScript.Language.PYTEST) {
            cmd.addAll(List.of("pytest", scriptFile(language), "-p", "automation_ai.pytest_plugin", "-p", "no:cacheprovider",
                    "-v", "--color=no", "--junitxml=results.xml", "--rootdir=" + runDir, "--confcutdir=" + runDir,
                    "-c", "pytest.ini", "-o", "addopts="));
        } else {
            cmd.addAll(List.of("robot", "--outputdir", runDir.toString(), "--output", "output.xml", "--log", "NONE",
                    "--report", "NONE", "--consolecolors", "off", "--pythonpath", settings.getTestEngineDir().toString(),
                    scriptFile(language)));
        }
        return cmd;
    }

    /** Only what Python, adb and the device API need. No database, JWT, Ollama or other backend secrets. */
    void configureEnvironment(Map<String, String> env, String serial, Path runDir) {
        env.clear();
        for (String key : INHERITED_ENV) {
            String value = System.getenv(key); // case-insensitive on Windows
            if (value != null) env.put(key, value);
        }
        String tmp = runDir.resolve("tmp").toString();
        env.put("TEMP", tmp);
        env.put("TMP", tmp);
        env.put("HOME", runDir.toString());
        env.put("USERPROFILE", runDir.toString());
        env.put("PYTHONPATH", settings.getTestEngineDir().toString());
        env.put("PYTHONNOUSERSITE", "1");
        env.put("PYTHONDONTWRITEBYTECODE", "1");
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");
        env.put("PYTHONUNBUFFERED", "1");
        env.put("PYTEST_DISABLE_PLUGIN_AUTOLOAD", "1");
        env.put("AUTOMATION_DEVICE_ID", serial);
        env.put("AUTOMATION_ADB_PATH", settings.getAdbPath());
        env.put("AUTOMATION_ARTIFACT_DIR", runDir.resolve(ARTIFACT_DIR).toString());
        env.put("AUTOMATION_CAPTURE_DIR", settings.getCaptureDir().toString());
        if (!settings.getTesseractPath().isBlank()) env.put("TESSERACT_PATH", settings.getTesseractPath());
    }

    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    static String scriptFile(TestScript.Language language) {
        return language == TestScript.Language.PYTEST ? "test_script.py" : "script.robot";
    }

    Path runDir(Long id) {
        return settings.getExecutionsDir().resolve(String.valueOf(id)).normalize();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public ExecutionDto get(Long id) {
        return toDto(find(id));
    }

    public PageResponse<ExecutionDto> list(Long scriptId, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = scriptId == null ? executionRepository.findAll(pageable) : executionRepository.findByScriptId(scriptId, pageable);
        return PageResponse.of(result, this::toDto);
    }

    /** Incremental log polling: returns bytes from {@code offset} (at most 64 KB). */
    public LogChunk log(Long id, long offset) {
        if (offset < 0) throw new BadRequestException("offset must be >= 0");
        ScriptExecution execution = find(id);
        Path file = runDir(id).resolve(LOG_FILE);
        boolean done = execution.getStatus().isFinal();
        if (!Files.isRegularFile(file)) {
            String text = offset == 0 && execution.getLogTail() != null ? execution.getLogTail() : "";
            return new LogChunk(id, offset, offset + text.length(), text, done, execution.getStatus());
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();
            if (offset >= size) return new LogChunk(id, offset, offset, "", done, execution.getStatus());
            int len = (int) Math.min(MAX_LOG_CHUNK, size - offset);
            byte[] buf = new byte[len];
            raf.seek(offset);
            raf.readFully(buf);
            int usable = completeUtf8Length(buf, len);
            long next = offset + usable;
            return new LogChunk(id, offset, next, new String(buf, 0, usable, StandardCharsets.UTF_8),
                    done && next >= size, execution.getStatus());
        } catch (IOException e) {
            throw new ServiceUnavailableException("Log unavailable: " + e.getMessage());
        }
    }

    public byte[] artifact(Long id, String name) {
        if (name == null || !ARTIFACT_NAME.matcher(name).matches() || name.contains("..")) {
            throw new BadRequestException("Invalid artifact name");
        }
        find(id);
        Path dir = runDir(id).resolve(ARTIFACT_DIR).normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) throw new ResourceNotFoundException("Artifact not found: " + name);
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ServiceUnavailableException("Artifact unavailable: " + e.getMessage());
        }
    }

    // ── Deletion / recovery ─────────────────────────────────────────────────

    public void delete(Long id, boolean confirm) {
        if (!confirm) throw new BadRequestException("Deleting an execution requires confirm=true");
        ScriptExecution execution = find(id);
        if (execution.getStatus() == ScriptExecution.Status.RUNNING) {
            throw new ConflictException("Execution is running; stop it before deleting it");
        }
        ScriptValidator.deleteQuietly(runDir(id));
        executionRepository.delete(execution);
    }

    /** Removes the executions (records and run directories) of the given scripts. Callers check none is running. */
    void deleteExecutionsOf(Collection<Long> scriptIds) {
        List<ScriptExecution> executions = executionRepository.findByScriptIdIn(scriptIds);
        if (executions.stream().anyMatch(e -> running.containsKey(e.getId()))) {
            throw new ConflictException("An execution is running; stop it first");
        }
        executions.forEach(e -> ScriptValidator.deleteQuietly(runDir(e.getId())));
        executionRepository.deleteAll(executions);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterrupted() {
        for (ScriptExecution e : executionRepository.findByStatus(ScriptExecution.Status.RUNNING)) {
            if (running.containsKey(e.getId())) continue;
            e.setStatus(ScriptExecution.Status.INTERRUPTED);
            e.setErrorMessage("Backend restarted during execution");
            e.setEndedAt(Instant.now());
            executionRepository.save(e);
        }
    }

    @PreDestroy
    void shutdown() {
        running.values().forEach(r -> {
            r.forced().compareAndSet(null, Outcome.SHUTDOWN);
            killTree(r.process());
        });
        monitors.shutdown();
        try {
            monitors.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning(Long executionId) {
        return running.containsKey(executionId);
    }

    // ── Results ─────────────────────────────────────────────────────────────

    List<TestResult> parseResults(TestScript.Language language, Path runDir) {
        Path file = runDir.resolve(language == TestScript.Language.PYTEST ? "results.xml" : "output.xml");
        if (!Files.isRegularFile(file)) return List.of();
        try {
            Document doc = secureBuilder().parse(file.toFile());
            return language == TestScript.Language.PYTEST ? parseJunit(doc) : parseRobot(doc);
        } catch (Exception e) {
            log.warn("Could not parse results {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    private static List<TestResult> parseJunit(Document doc) {
        List<TestResult> results = new ArrayList<>();
        NodeList cases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element tc = (Element) cases.item(i);
            String status = "PASSED";
            String message = null;
            for (String tag : List.of("failure", "error", "skipped")) {
                Element child = firstChild(tc, tag);
                if (child != null) {
                    status = switch (tag) {
                        case "failure" -> "FAILED";
                        case "error" -> "ERROR";
                        default -> "SKIPPED";
                    };
                    message = child.getAttribute("message");
                    if (message.isBlank()) message = child.getTextContent();
                    break;
                }
            }
            results.add(new TestResult(tc.getAttribute("name"), status, parseDouble(tc.getAttribute("time")), truncate(message)));
        }
        return results;
    }

    private static List<TestResult> parseRobot(Document doc) {
        List<TestResult> results = new ArrayList<>();
        NodeList tests = doc.getElementsByTagName("test");
        for (int i = 0; i < tests.getLength(); i++) {
            Element test = (Element) tests.item(i);
            Element statusEl = null;
            for (Node n = test.getLastChild(); n != null; n = n.getPreviousSibling()) {
                if (n instanceof Element el && "status".equals(el.getTagName())) {
                    statusEl = el;
                    break;
                }
            }
            if (statusEl == null) continue;
            String status = switch (statusEl.getAttribute("status")) {
                case "PASS" -> "PASSED";
                case "FAIL" -> "FAILED";
                case "SKIP", "NOT RUN" -> "SKIPPED";
                default -> "ERROR";
            };
            String message = statusEl.getTextContent();
            results.add(new TestResult(test.getAttribute("name"), status, parseDouble(statusEl.getAttribute("elapsed")),
                    message == null || message.isBlank() ? null : truncate(message)));
        }
        return results;
    }

    static DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    private static javax.xml.parsers.DocumentBuilder secureBuilder() throws Exception {
        return secureFactory().newDocumentBuilder();
    }

    private static Element firstChild(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && tag.equals(el.getTagName())) return el;
        }
        return null;
    }

    private List<String> listScreenshots(Path runDir) {
        Path dir = runDir.resolve(ARTIFACT_DIR);
        if (!Files.isDirectory(dir)) return List.of();
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (ARTIFACT_NAME.matcher(name).matches() && Files.isRegularFile(p)) names.add(name);
            }
        } catch (IOException e) {
            log.warn("Could not list artifacts in {}: {}", dir, e.getMessage());
        }
        Collections.sort(names);
        return names;
    }

    // ── Mapping / helpers ───────────────────────────────────────────────────

    ExecutionDto toDto(ScriptExecution e) {
        List<TestResult> results = read(e.getResultsJson(), new TypeReference<List<TestResult>>() { });
        List<String> screenshots = read(e.getArtifactsJson(), new TypeReference<List<String>>() { });
        if (e.getStatus() == ScriptExecution.Status.RUNNING) screenshots = listScreenshots(runDir(e.getId()));
        return new ExecutionDto(e.getId(), e.getScriptId(), e.getDeviceSerial(), e.getLanguage(), e.getContentHash(),
                e.getStatus(), e.getTimeoutSeconds(), e.getExitCode(), e.getTestsTotal(), e.getTestsPassed(),
                e.getTestsFailed(), e.getTestsSkipped(), e.getErrorMessage(), results, screenshots, e.getStartedBy(),
                e.getStartedAt(), e.getEndedAt());
    }

    private ScriptExecution find(Long id) {
        return executionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Execution " + id + " not found"));
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String tail(Path file) {
        if (!Files.isRegularFile(file)) return "";
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();
            int len = (int) Math.min(LOG_TAIL_CHARS, size);
            byte[] buf = new byte[len];
            raf.seek(size - len);
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String lastLines(String log) {
        if (log == null || log.isBlank()) return "";
        String[] lines = log.strip().split("\\R");
        return String.join(" | ", Arrays.copyOfRange(lines, Math.max(0, lines.length - 3), lines.length));
    }

    /** Length of {@code buf[0..len)} without a trailing incomplete UTF-8 sequence. */
    static int completeUtf8Length(byte[] buf, int len) {
        int i = len - 1;
        int continuation = 0;
        while (i >= 0 && (buf[i] & 0xC0) == 0x80 && continuation < 3) {
            i--;
            continuation++;
        }
        if (i < 0) return len;
        int lead = buf[i] & 0xFF;
        int expected = lead >= 0xF0 ? 4 : lead >= 0xE0 ? 3 : lead >= 0xC0 ? 2 : 1;
        return expected > continuation + 1 ? i : len;
    }

    private static Double parseDouble(String s) {
        try {
            return s == null || s.isBlank() ? null : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String truncate(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.length() > MAX_MESSAGE ? t.substring(0, MAX_MESSAGE) + "..." : t;
    }
}
