package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlan;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlanRepository;
import com.lowenhert.patchmanagement.core.ai.planner.GeneratedTestPlan;
import com.lowenhert.patchmanagement.core.ai.planner.PlannerKnowledgeService;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbInputValidator;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

/** Test case library: CRUD, events, duplication, safe deletion, import from plans and code generation. */
@Slf4j
@Service
public class TestCaseService {

    static final int MAX_EVENTS = 200;
    static final int MAX_DESCRIPTION = 2000;
    static final int MAX_TARGET = 400;
    static final int MAX_EXPECTED = 300;
    static final int DEFAULT_TIMEOUT_MS = 10_000;
    static final int MAX_WAIT_MS = 30_000;
    static final int MAX_ASSERT_TIMEOUT_MS = 120_000;
    static final int SCREEN_CHECK_TEXTS = 3;

    private final TestCaseRepository caseRepository;
    private final TestScriptRepository scriptRepository;
    private final ScriptExecutionRepository executionRepository;
    private final TestScriptService scriptService;
    private final AiTestPlanRepository planRepository;
    private final PlannerKnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public TestCaseService(TestCaseRepository caseRepository, TestScriptRepository scriptRepository,
                           ScriptExecutionRepository executionRepository, TestScriptService scriptService,
                           AiTestPlanRepository planRepository, PlannerKnowledgeService knowledgeService,
                           ObjectMapper objectMapper) {
        this.caseRepository = caseRepository;
        this.scriptRepository = scriptRepository;
        this.executionRepository = executionRepository;
        this.scriptService = scriptService;
        this.planRepository = planRepository;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    // ── Cases ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<TestCaseSummary> list(int page, int size, String query) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = query == null || query.isBlank() ? caseRepository.findAllBy(pageable)
                : caseRepository.findByNameContainingIgnoreCase(query.strip(), pageable);
        return PageResponse.of(result, c -> new TestCaseSummary(c.getId(), c.getName(), c.getDescription(),
                c.getSourcePlanId(), c.getDeviceSerial(), c.getEvents().size(), scriptRepository.countByTestCaseId(c.getId()),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public TestCaseDetail get(Long id) {
        return detail(find(id));
    }

    @Transactional
    public TestCaseDetail create(TestCaseRequest request, String username) {
        if (request == null) throw new BadRequestException("Request body is required");
        TestCase testCase = TestCase.builder()
                .name(TestScriptService.requireName(request.name()))
                .description(description(request.description()))
                .deviceSerial(serial(request.deviceSerial()))
                .createdBy(username)
                .build();
        return detail(caseRepository.save(testCase));
    }

    @Transactional
    public TestCaseDetail update(Long id, TestCaseRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        TestCase testCase = find(id);
        if (request.name() != null) testCase.setName(TestScriptService.requireName(request.name()));
        testCase.setDescription(description(request.description()));
        testCase.setDeviceSerial(serial(request.deviceSerial()));
        return detail(caseRepository.save(testCase));
    }

    @Transactional
    public TestCaseDetail rename(Long id, String name) {
        TestCase testCase = find(id);
        testCase.setName(TestScriptService.requireName(name));
        return detail(caseRepository.save(testCase));
    }

    /** Deep copy of events and scripts. Copied scripts are DRAFTs; executions are not copied. */
    @Transactional
    public TestCaseDetail duplicate(Long id, String username) {
        TestCase source = find(id);
        TestCase copy = TestCase.builder()
                .name(TestScriptService.copyName(source.getName()))
                .description(source.getDescription())
                .sourcePlanId(source.getSourcePlanId())
                .deviceSerial(source.getDeviceSerial())
                .createdBy(username)
                .build();
        for (TestCaseEvent e : source.getEvents()) copy.addEvent(copyEvent(e));
        copy = caseRepository.save(copy);
        for (TestScript script : scriptRepository.findByTestCaseId(source.getId())) {
            scriptRepository.save(scriptService.copyAsDraft(script, copy.getId(), script.getName(), username));
        }
        return detail(copy);
    }

    @Transactional(readOnly = true)
    public DeleteImpact deleteImpact(Long id) {
        TestCase testCase = find(id);
        List<Long> scriptIds = scriptRepository.findByTestCaseId(id).stream().map(TestScript::getId).toList();
        long executions = scriptIds.isEmpty() ? 0 : executionRepository.countByScriptIdIn(scriptIds);
        boolean running = !scriptIds.isEmpty() && executionRepository.existsByScriptIdInAndStatus(scriptIds, ScriptExecution.Status.RUNNING);
        return new DeleteImpact("TEST_CASE", id, testCase.getName(), testCase.getEvents().size(), scriptIds.size(), executions, running);
    }

    /** Deletes the case with its events, scripts, executions and run artifacts. Refuses while anything is running. */
    @Transactional
    public void delete(Long id, boolean confirm) {
        if (!confirm) throw new BadRequestException("Deleting a test case requires confirm=true");
        TestCase testCase = find(id);
        scriptService.deleteScripts(scriptRepository.findByTestCaseId(id));
        caseRepository.delete(testCase);
        log.info("Deleted test case {} ({})", id, testCase.getName());
    }

    // ── Events ──────────────────────────────────────────────────────────────

    /** Replaces the ordered event list (add, edit, reorder and remove in one save). */
    @Transactional
    public TestCaseDetail replaceEvents(Long id, List<EventRequest> requests) {
        if (requests == null) throw new BadRequestException("events are required");
        if (requests.size() > MAX_EVENTS) throw new BadRequestException("A test case can have at most " + MAX_EVENTS + " events");
        List<TestCaseEvent> events = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) events.add(toEvent(requests.get(i), i + 1));
        TestCase testCase = find(id);
        testCase.getEvents().clear();
        caseRepository.saveAndFlush(testCase);
        events.forEach(testCase::addEvent);
        return detail(caseRepository.save(testCase));
    }

    @Transactional
    public TestCaseDetail deleteEvent(Long id, Long eventId) {
        TestCase testCase = find(id);
        boolean removed = testCase.getEvents().removeIf(e -> e.getId().equals(eventId));
        if (!removed) throw new ResourceNotFoundException("Event " + eventId + " not found in test case " + id);
        renumber(testCase.getEvents());
        return detail(caseRepository.save(testCase));
    }

    // ── Plans and code generation ───────────────────────────────────────────

    /** Creates a test case from a plan with a deterministic step mapping (no LLM involved). */
    @Transactional
    public TestCaseDetail fromPlan(FromPlanRequest request, String username) {
        if (request == null || request.planId() == null) throw new BadRequestException("planId is required");
        AiTestPlan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Test plan " + request.planId() + " not found"));
        if (plan.getStatus() == AiTestPlan.Status.REJECTED) {
            throw new BadRequestException("Rejected test plans cannot be turned into test cases");
        }
        GeneratedTestPlan generated;
        try {
            generated = objectMapper.readValue(plan.getPlanJson(), GeneratedTestPlan.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Stored plan cannot be read: " + e.getOriginalMessage());
        }
        List<TestCaseEvent> events = eventsFromPlan(generated, knowledgeService::screenTexts);
        if (events.size() > MAX_EVENTS) throw new BadRequestException("Plan produces more than " + MAX_EVENTS + " events");
        String name = request.name() == null || request.name().isBlank() ? plan.getTitle() : request.name();
        TestCase testCase = TestCase.builder()
                .name(TestScriptService.requireName(name))
                .description(description(plan.getGoal()))
                .sourcePlanId(plan.getId())
                .deviceSerial(plan.getDeviceSerial())
                .createdBy(username)
                .build();
        events.forEach(testCase::addEvent);
        return detail(caseRepository.save(testCase));
    }

    static List<TestCaseEvent> eventsFromPlan(GeneratedTestPlan plan, Function<Long, List<String>> screenTexts) {
        List<GeneratedTestPlan.Step> steps = plan.steps().stream().sorted(Comparator.comparingInt(GeneratedTestPlan.Step::order)).toList();
        Map<Integer, List<GeneratedTestPlan.Assertion>> byStep = new HashMap<>();
        for (GeneratedTestPlan.Assertion a : Optional.ofNullable(plan.assertions()).orElse(List.of())) {
            byStep.computeIfAbsent(a.afterStep(), k -> new ArrayList<>()).add(a);
        }
        List<TestCaseEvent> events = new ArrayList<>();
        byStep.getOrDefault(0, List.of()).forEach(a -> events.add(assertionEvent(a, screenTexts)));
        for (GeneratedTestPlan.Step step : steps) {
            GeneratedTestPlan.Action action = step.action();
            if (action == GeneratedTestPlan.Action.UNSUPPORTED) {
                throw new BadRequestException("Plan step " + step.order() + " is UNSUPPORTED (" + step.requestedAction()
                        + "); create the test case manually instead");
            }
            if (action.isNavigation()) {
                events.add(event(TestCaseEvent.Action.valueOf(action.name()), null, step.expectedState(), DEFAULT_TIMEOUT_MS));
            } else if (action == GeneratedTestPlan.Action.WAIT) {
                events.add(event(TestCaseEvent.Action.WAIT, null, step.expectedState(),
                        Math.min(Math.max(step.timeoutMs(), 100), MAX_WAIT_MS)));
            } else if (action == GeneratedTestPlan.Action.ASSERT_TEXT_VISIBLE) {
                events.add(event(TestCaseEvent.Action.ASSERT_TEXT_VISIBLE, step.target(), step.expectedState(), assertTimeout(step.timeoutMs())));
            } else if (action == GeneratedTestPlan.Action.ASSERT_SCREEN) {
                Long screen = step.expectedScreenId() != null ? step.expectedScreenId() : step.fromScreenId();
                events.add(screenCheck(screen, step.expectedState(), assertTimeout(step.timeoutMs()), screenTexts));
            }
            byStep.getOrDefault(step.order(), List.of()).forEach(a -> events.add(assertionEvent(a, screenTexts)));
        }
        events.add(event(TestCaseEvent.Action.SCREENSHOT, "final", "Final screen", DEFAULT_TIMEOUT_MS));
        renumber(events);
        // Same rules as manually edited events (targets present, lengths and timeouts in range)
        return events.stream().map(e -> toEvent(new EventRequest(e.getAction(), e.getTarget(), e.getExpectedState(),
                e.getTimeoutMs()), e.getPosition())).toList();
    }

    private static TestCaseEvent assertionEvent(GeneratedTestPlan.Assertion a, Function<Long, List<String>> screenTexts) {
        return switch (a.type()) {
            case TEXT_VISIBLE -> event(TestCaseEvent.Action.ASSERT_TEXT_VISIBLE, a.text(), a.description(), DEFAULT_TIMEOUT_MS);
            case TEXT_NOT_VISIBLE -> event(TestCaseEvent.Action.ASSERT_TEXT_NOT_VISIBLE, a.text(), a.description(), DEFAULT_TIMEOUT_MS);
            case SCREEN_IS -> screenCheck(a.screenId(), a.description(), DEFAULT_TIMEOUT_MS, screenTexts);
        };
    }

    /** A screen is verified by its most distinctive known OCR texts; without any, a screenshot documents it instead. */
    private static TestCaseEvent screenCheck(Long screenId, String expected, int timeoutMs, Function<Long, List<String>> screenTexts) {
        List<String> texts = screenTexts.apply(screenId).stream()
                .filter(t -> t.chars().filter(Character::isLetter).count() >= 3)
        .filter(t -> !t.contains(TestCaseEvent.ANY_SEPARATOR.strip()))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(SCREEN_CHECK_TEXTS)
                .toList();
        String label = "Screen #" + screenId + (expected == null || expected.isBlank() ? "" : ": " + expected);
        if (texts.isEmpty()) {
            return event(TestCaseEvent.Action.SCREENSHOT, "screen_" + screenId,
                    truncate("Not verified automatically (no OCR text known). " + label, MAX_EXPECTED), timeoutMs);
        }
        return event(TestCaseEvent.Action.ASSERT_ANY_TEXT_VISIBLE, truncate(String.join(TestCaseEvent.ANY_SEPARATOR, texts), MAX_TARGET),
                truncate(label, MAX_EXPECTED), timeoutMs);
    }

    @Transactional
    public ScriptDetail generateScript(Long id, GenerateRequest request, String username) {
        if (request == null || request.language() == null) throw new BadRequestException("language is required (PYTEST or ROBOT)");
        TestCase testCase = find(id);
        if (testCase.getEvents().isEmpty()) throw new BadRequestException("Add events to the test case before generating code");
        String content = ScriptCodeGenerator.generate(request.language(), testCase.getName(), testCase.getDescription(), testCase.getEvents());
        String name = request.name() == null || request.name().isBlank()
                ? truncate(testCase.getName() + (request.language() == TestScript.Language.PYTEST ? " (pytest)" : " (robot)"), TestScriptService.MAX_NAME)
                : request.name();
        TestScript script = scriptService.saveGenerated(testCase.getId(), name, request.language(), content, username);
        return scriptService.get(script.getId());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static TestCaseEvent toEvent(EventRequest r, int position) {
        if (r == null || r.action() == null) throw new BadRequestException("Event " + position + ": action is required");
        String target = clean(r.target(), MAX_TARGET, "Event " + position + ": target");
        String expected = clean(r.expectedState(), MAX_EXPECTED, "Event " + position + ": expectedState");
        TestCaseEvent.Action action = r.action();
        if (action.needsTarget() && (target == null || target.isEmpty())) {
            throw new BadRequestException("Event " + position + ": " + action + " needs a target text");
        }
        if (action == TestCaseEvent.Action.ASSERT_ANY_TEXT_VISIBLE && ScriptCodeGenerator.anyOptions(target).isEmpty()) {
            throw new BadRequestException("Event " + position + ": separate alternatives with '" + TestCaseEvent.ANY_SEPARATOR.strip() + "'");
        }
        if (action.isKey()) target = null;
        int timeout = r.timeoutMs() == null ? DEFAULT_TIMEOUT_MS : r.timeoutMs();
        if (action == TestCaseEvent.Action.WAIT && (timeout < 100 || timeout > MAX_WAIT_MS)) {
            throw new BadRequestException("Event " + position + ": WAIT must be between 100 and " + MAX_WAIT_MS + " ms");
        }
        if (action.needsTarget() && (timeout < 1000 || timeout > MAX_ASSERT_TIMEOUT_MS)) {
            throw new BadRequestException("Event " + position + ": assertion timeout must be between 1000 and " + MAX_ASSERT_TIMEOUT_MS + " ms");
        }
        if (timeout < 0 || timeout > MAX_ASSERT_TIMEOUT_MS) {
            throw new BadRequestException("Event " + position + ": timeoutMs must be between 0 and " + MAX_ASSERT_TIMEOUT_MS);
        }
        return TestCaseEvent.builder().position(position).action(action).target(target).expectedState(expected)
                .timeoutMs(timeout).build();
    }

    private static String clean(String value, int max, String field) {
        if (value == null) return null;
        String v = value.strip();
        if (v.chars().anyMatch(Character::isISOControl)) throw new BadRequestException(field + " must not contain control characters");
        if (v.length() > max) throw new BadRequestException(field + " must be at most " + max + " characters");
        return v.isEmpty() ? null : v;
    }

    private static TestCaseEvent event(TestCaseEvent.Action action, String target, String expected, int timeoutMs) {
        return TestCaseEvent.builder().action(action).target(target)
                .expectedState(expected == null ? null : truncate(expected.replaceAll("\\p{Cntrl}", " "), MAX_EXPECTED))
                .timeoutMs(timeoutMs).build();
    }

    private static TestCaseEvent copyEvent(TestCaseEvent e) {
        return TestCaseEvent.builder().position(e.getPosition()).action(e.getAction()).target(e.getTarget())
                .expectedState(e.getExpectedState()).timeoutMs(e.getTimeoutMs()).build();
    }

    private static void renumber(List<TestCaseEvent> events) {
        for (int i = 0; i < events.size(); i++) events.get(i).setPosition(i + 1);
    }

    private static int assertTimeout(int ms) {
        return Math.min(Math.max(ms, 1000), MAX_ASSERT_TIMEOUT_MS);
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String description(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.strip();
        if (v.length() > MAX_DESCRIPTION) throw new BadRequestException("description must be at most " + MAX_DESCRIPTION + " characters");
        return v;
    }

    private static String serial(String value) {
        return value == null || value.isBlank() ? null : AdbInputValidator.requireSerial(value.strip());
    }

    private TestCase find(Long id) {
        return caseRepository.findWithEventsById(id).orElseThrow(() -> new ResourceNotFoundException("Test case " + id + " not found"));
    }

    private TestCaseDetail detail(TestCase c) {
        List<ScriptSummary> scripts = c.getId() == null ? List.of()
                : scriptRepository.findByTestCaseId(c.getId()).stream().map(scriptService::summary).toList();
        return new TestCaseDetail(c.getId(), c.getName(), c.getDescription(), c.getSourcePlanId(), c.getDeviceSerial(),
                c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedAt(), c.getEvents().stream().map(EventDto::of).toList(), scripts);
    }
}
