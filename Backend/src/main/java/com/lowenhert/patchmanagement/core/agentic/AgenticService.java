package com.lowenhert.patchmanagement.core.agentic;

import com.lowenhert.patchmanagement.core.agentic.AgenticDtos.*;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlan;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlanRepository;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlannerService;
import com.lowenhert.patchmanagement.core.ai.planner.TestPlannerDtos.GenerateRequest;
import com.lowenhert.patchmanagement.core.ai.planner.TestPlannerDtos.PlanDetail;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbCommandExecutor;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbCommandResult;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbInputValidator;
import com.lowenhert.patchmanagement.core.testlibrary.*;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgenticService {

    private final AiTestPlannerService plannerService;
    private final TestCaseService testCaseService;
    private final TestScriptService testScriptService;
    private final ScriptExecutionService executionService;
    private final ScriptExecutionRepository executionRepository;
    private final AiTestPlanRepository planRepository;
    private final AdbCommandExecutor adbExecutor;

    public AgenticRunResult run(AgenticRunRequest request, String username) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.deviceId() == null || request.deviceId().isBlank()) throw new BadRequestException("deviceId is required");
        if (request.goal() == null || request.goal().isBlank()) throw new BadRequestException("goal is required");

        String serial = AdbInputValidator.requireSerial(request.deviceId().strip());
        TestScript.Language lang = request.language() != null ? request.language() : TestScript.Language.PYTEST;
        int maxSteps = request.maxSteps() != null ? request.maxSteps() : 15;
        int timeout = request.timeoutSeconds() != null ? request.timeoutSeconds() : 120;
        String user = username != null ? username : "agentic";

        log.info("Agentic run requested for device={}, goal='{}', language={}", serial, request.goal(), lang);

        // 1. Generate plan using AI planner
        GenerateRequest planReq = new GenerateRequest(request.goal().strip(), serial, null, maxSteps);
        PlanDetail plan = plannerService.generate(planReq, user);

        // If the plan was already auto-executed during generate, return it immediately
        if (plan.executionId() != null) {
            ScriptExecution exec = executionRepository.findById(plan.executionId()).orElse(null);
            Long scriptId = exec != null ? exec.getScriptId() : null;
            return new AgenticRunResult(plan.id(), null, scriptId, plan.executionId(),
                    exec != null ? exec.getStatus().name() : "RUNNING");
        }

        // 2. Convert plan to test case via internal TestCaseService.fromPlan()
        FromPlanRequest caseReq = new FromPlanRequest(plan.id(), plan.title());
        TestCaseDetail testCase = testCaseService.fromPlan(caseReq, user);

        // 3. Generate script via TestCaseService.generateScript()
        TestLibraryDtos.GenerateRequest scriptGenReq = new TestLibraryDtos.GenerateRequest(lang, null);
        ScriptDetail script = testCaseService.generateScript(testCase.id(), scriptGenReq, user);

        // 4. Validate and auto-approve the script (bypassing manual approve step)
        ScriptDetail approvedScript = testScriptService.validateAndAutoApprove(script.id(), user);

        if (approvedScript.validation() != null && !approvedScript.validation().valid()) {
            log.warn("Script {} failed validation; cannot auto-execute", script.id());
            return new AgenticRunResult(plan.id(), testCase.id(), script.id(), null, "VALIDATION_FAILED");
        }

        // 5. Directly execute via ScriptExecutionService.start()
        ExecuteRequest execReq = new ExecuteRequest(script.id(), serial, approvedScript.contentHash(), timeout, true);
        ExecutionDto execution = executionService.start(execReq, user);

        // Update plan with execution tracking
        AiTestPlan planEntity = planRepository.findById(plan.id()).orElse(null);
        if (planEntity != null) {
            planEntity.setStatus(AiTestPlan.Status.EXECUTING);
            planEntity.setExecutionId(execution.id());
            planRepository.save(planEntity);
        }

        log.info("Agentic execution {} started for plan {}, script {}", execution.id(), plan.id(), script.id());
        return new AgenticRunResult(plan.id(), testCase.id(), script.id(), execution.id(), execution.status().name());
    }

    public AgenticMetrics getMetrics(Long executionId) {
        ScriptExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution " + executionId + " not found"));
        String serial = execution.getDeviceSerial();
        double cpuPercent = 0.0;
        double memMb = 0.0;

        try {
            AdbCommandResult cpuResult = adbExecutor.execute(List.of("-s", serial, "shell", "dumpsys", "cpuinfo"));
            if (cpuResult.isSuccess() && cpuResult.output() != null) {
                cpuPercent = parseCpuPercent(cpuResult.output());
            }
        } catch (Exception e) {
            log.warn("Failed to get CPU metrics for {}: {}", serial, e.getMessage());
        }

        try {
            AdbCommandResult memResult = adbExecutor.execute(List.of("-s", serial, "shell", "dumpsys", "meminfo"));
            if (memResult.isSuccess() && memResult.output() != null) {
                memMb = parseMemMb(memResult.output());
            }
        } catch (Exception e) {
            log.warn("Failed to get Mem metrics for {}: {}", serial, e.getMessage());
        }

        return new AgenticMetrics(
                Math.round(cpuPercent * 10.0) / 10.0,
                Math.round(memMb * 10.0) / 10.0,
                Instant.now()
        );
    }

    public AgenticRunResult reExecute(AgenticReExecuteRequest request, String username) {
        if (request == null || request.scriptId() == null) throw new BadRequestException("scriptId is required");
        if (request.deviceId() == null || request.deviceId().isBlank()) throw new BadRequestException("deviceId is required");

        String serial = AdbInputValidator.requireSerial(request.deviceId().strip());
        int timeout = request.timeoutSeconds() != null ? request.timeoutSeconds() : 120;
        String user = username != null ? username : "agentic";

        // Save updated content if provided
        if (request.content() != null && !request.content().isBlank()) {
            testScriptService.update(request.scriptId(), new ScriptUpdateRequest(null, request.content(), null));
        }

        // Validate & auto-approve
        ScriptDetail script = testScriptService.validateAndAutoApprove(request.scriptId(), user);
        if (script.validation() != null && !script.validation().valid()) {
            return new AgenticRunResult(null, script.testCaseId(), script.id(), null, "VALIDATION_FAILED");
        }

        // Execute
        ExecuteRequest execReq = new ExecuteRequest(script.id(), serial, script.contentHash(), timeout, true);
        ExecutionDto execution = executionService.start(execReq, user);

        return new AgenticRunResult(null, script.testCaseId(), script.id(), execution.id(), execution.status().name());
    }

    static double parseCpuPercent(String output) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)%\\s*TOTAL", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        Matcher m2 = Pattern.compile("TOTAL:\\s*(\\d+(?:\\.\\d+)?)%", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m2.find()) {
            return Double.parseDouble(m2.group(1));
        }
        Matcher m3 = Pattern.compile("Load:\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m3.find()) {
            return Math.min(100.0, Double.parseDouble(m3.group(1)) * 25.0);
        }
        return 0.0;
    }

    static double parseMemMb(String output) {
        Matcher m = Pattern.compile("Used\\s+RAM:\\s*([0-9,]+)\\s*K", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m.find()) {
            String val = m.group(1).replace(",", "");
            return Double.parseDouble(val) / 1024.0;
        }
        Matcher m2 = Pattern.compile("Total\\s+PSS\\s+by\\s+OOM\\s+adjustment:\\s*([0-9,]+)\\s*K", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m2.find()) {
            String val = m2.group(1).replace(",", "");
            return Double.parseDouble(val) / 1024.0;
        }
        Matcher m3 = Pattern.compile("Total\\s+RAM:\\s*([0-9,]+)\\s*K", Pattern.CASE_INSENSITIVE).matcher(output);
        if (m3.find()) {
            String val = m3.group(1).replace(",", "");
            return (Double.parseDouble(val) / 1024.0) * 0.5;
        }
        return 0.0;
    }
}
