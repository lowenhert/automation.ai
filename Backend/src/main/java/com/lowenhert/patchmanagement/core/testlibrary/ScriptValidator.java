package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/** Static validation of a script through test-engine/validate_script.py. Never executes the script. */
@Slf4j
@Component
public class ScriptValidator {

    private final ProcessRunner processRunner;
    private final TestEngineSettings settings;
    private final ObjectMapper objectMapper;

    public ScriptValidator(ProcessRunner processRunner, TestEngineSettings settings, ObjectMapper objectMapper) {
        this.processRunner = processRunner;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    public TestLibraryDtos.Validation validate(TestScript.Language language, String content) {
        if (!Files.isRegularFile(settings.getValidatorScript())) {
            throw new ServiceUnavailableException("Script validator not found at " + settings.getValidatorScript()
                    + ". Check 'app.test-engine.dir'.");
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("aai-validate-");
            Path file = dir.resolve(language == TestScript.Language.PYTEST ? "test_script.py" : "script.robot");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            ProcessRunner.ProcessResult result = processRunner.run(List.of(settings.getPythonPath(), "-B", "-I",
                    settings.getValidatorScript().toString(), "--language", language == TestScript.Language.PYTEST ? "pytest" : "robot",
                    "--file", file.toString()), dir.toFile(), settings.getValidationTimeoutMs());
            if (result.exitCode() != 0 || result.stdout().isBlank()) {
                log.warn("Validator failed (exit {}): {}", result.exitCode(), result.stderr());
                throw new ServiceUnavailableException("Script validator failed: "
                        + abbreviate(result.stderr().isBlank() ? "exit " + result.exitCode() : result.stderr()));
            }
            String json = result.stdout().lines().reduce((a, b) -> b).orElse("");
            return objectMapper.readValue(json, TestLibraryDtos.Validation.class);
        } catch (TimeoutException e) {
            throw new ServiceUnavailableException("Script validation timed out");
        } catch (IOException e) {
            throw new ServiceUnavailableException("Script validation unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Script validation interrupted");
        } finally {
            deleteQuietly(dir);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(s.length() - 300) : s;
    }

    static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
