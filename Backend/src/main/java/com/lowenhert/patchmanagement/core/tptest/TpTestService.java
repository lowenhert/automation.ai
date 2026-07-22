package com.lowenhert.patchmanagement.core.tptest;

import com.lowenhert.patchmanagement.core.tptest.entity.*;
import com.lowenhert.patchmanagement.core.tptest.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TpTestService {

    private final TpProjectRepository projectRepo;
    private final TpSuiteRepository suiteRepo;
    private final TpSectionRepository sectionRepo;
    private final TpTestCaseRepository caseRepo;
    private final TpTestRunRepository runRepo;
    private final TpRunCaseRepository runCaseRepo;
    private final TpResultRepository resultRepo;

    // ═══════════════════════════════════════════════════════════════════════
    // PROJECTS
    // ═══════════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> getAllProjects() {
        List<TpProject> projects = projectRepo.findAllByOrderByCreatedAtDesc();
        return projects.stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("description", p.getDescription());
            map.put("category", p.getCategory());
            map.put("createdBy", p.getCreatedBy());
            map.put("createdAt", p.getCreatedAt());
            map.put("updatedAt", p.getUpdatedAt());

            // Compute stats
            List<TpSuite> suites = suiteRepo.findByProjectIdOrderByCreatedAtAsc(p.getId());
            long suitesCount = suites.size();
            List<Long> suiteIds = suites.stream().map(TpSuite::getId).collect(Collectors.toList());
            long casesCount = 0;
            if (!suiteIds.isEmpty()) {
                List<Long> sectionIds = sectionRepo.findBySuiteIdOrderByCreatedAtAsc(suiteIds.get(0)).stream()
                        .map(TpSection::getId).collect(Collectors.toList());
                // Get all sections across all suites
                List<Long> allSectionIds = new ArrayList<>();
                for (Long suiteId : suiteIds) {
                    sectionRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId)
                            .forEach(s -> allSectionIds.add(s.getId()));
                }
                if (!allSectionIds.isEmpty()) {
                    casesCount = caseRepo.countBySectionIdIn(allSectionIds);
                }
            }
            long runsCount = runRepo.countByProjectId(p.getId());

            // Pass rate from runs
            List<TpTestRun> runs = runRepo.findByProjectIdOrderByCreatedAtDesc(p.getId());
            long totalPassed = 0;
            long totalExecuted = 0;
            for (TpTestRun run : runs) {
                long passed = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "PASSED");
                long failed = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "FAILED");
                long blocked = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "BLOCKED");
                totalPassed += passed;
                totalExecuted += passed + failed + blocked;
            }

            map.put("suitesCount", suitesCount);
            map.put("casesCount", casesCount);
            map.put("runsCount", runsCount);
            map.put("passedCount", totalPassed);
            map.put("executedCount", totalExecuted);
            return map;
        }).collect(Collectors.toList());
    }

    public TpProject getProject(Long id) {
        return projectRepo.findById(id).orElseThrow(() -> new RuntimeException("Project not found: " + id));
    }

    public TpProject createProject(TpProject project) {
        return projectRepo.save(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        // Cascade delete: runs → runCases → results, suites → sections → cases
        List<TpTestRun> runs = runRepo.findByProjectIdOrderByCreatedAtDesc(id);
        for (TpTestRun run : runs) {
            deleteRunCascade(run.getId());
        }
        runRepo.deleteByProjectId(id);

        List<TpSuite> suites = suiteRepo.findByProjectIdOrderByCreatedAtAsc(id);
        for (TpSuite suite : suites) {
            deleteSuiteCascade(suite.getId());
        }
        suiteRepo.deleteByProjectId(id);
        projectRepo.deleteById(id);
        log.info("Deleted project and all children: id={}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUITES
    // ═══════════════════════════════════════════════════════════════════════

    public List<TpSuite> getSuitesByProject(Long projectId) {
        return suiteRepo.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public TpSuite createSuite(TpSuite suite) {
        return suiteRepo.save(suite);
    }

    @Transactional
    public void deleteSuite(Long id) {
        deleteSuiteCascade(id);
        suiteRepo.deleteById(id);
        log.info("Deleted suite: id={}", id);
    }

    private void deleteSuiteCascade(Long suiteId) {
        List<TpSection> sections = sectionRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId);
        List<Long> sectionIds = sections.stream().map(TpSection::getId).collect(Collectors.toList());
        if (!sectionIds.isEmpty()) {
            caseRepo.deleteBySectionIdIn(sectionIds);
        }
        sectionRepo.deleteBySuiteId(suiteId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECTIONS
    // ═══════════════════════════════════════════════════════════════════════

    public List<TpSection> getSectionsBySuite(Long suiteId) {
        return sectionRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId);
    }

    public TpSection createSection(TpSection section) {
        return sectionRepo.save(section);
    }

    @Transactional
    public void deleteSection(Long id) {
        caseRepo.deleteBySectionId(id);
        sectionRepo.deleteById(id);
        log.info("Deleted section: id={}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST CASES
    // ═══════════════════════════════════════════════════════════════════════

    public List<TpTestCase> getCasesBySuite(Long suiteId) {
        List<TpSection> sections = sectionRepo.findBySuiteIdOrderByCreatedAtAsc(suiteId);
        List<Long> sectionIds = sections.stream().map(TpSection::getId).collect(Collectors.toList());
        if (sectionIds.isEmpty()) return Collections.emptyList();
        return caseRepo.findBySectionIdIn(sectionIds);
    }

    public List<TpTestCase> getCasesBySection(Long sectionId) {
        return caseRepo.findBySectionIdOrderByCreatedAtAsc(sectionId);
    }

    public TpTestCase getCase(Long id) {
        return caseRepo.findById(id).orElseThrow(() -> new RuntimeException("TestCase not found: " + id));
    }

    public TpTestCase createCase(TpTestCase testCase) {
        return caseRepo.save(testCase);
    }

    public TpTestCase updateCase(Long id, TpTestCase updated) {
        TpTestCase existing = getCase(id);
        if (updated.getTitle() != null) existing.setTitle(updated.getTitle());
        if (updated.getPreconditions() != null) existing.setPreconditions(updated.getPreconditions());
        if (updated.getSteps() != null) existing.setSteps(updated.getSteps());
        if (updated.getExpectedResult() != null) existing.setExpectedResult(updated.getExpectedResult());
        if (updated.getPriority() != null) existing.setPriority(updated.getPriority());
        if (updated.getType() != null) existing.setType(updated.getType());
        if (updated.getAutomationStatus() != null) existing.setAutomationStatus(updated.getAutomationStatus());
        if (updated.getActualResult() != null) existing.setActualResult(updated.getActualResult());
        if (updated.getRemark() != null) existing.setRemark(updated.getRemark());
        if (updated.getAssignedTo() != null) existing.setAssignedTo(updated.getAssignedTo());
        return caseRepo.save(existing);
    }

    public void deleteCase(Long id) {
        caseRepo.deleteById(id);
        log.info("Deleted test case: id={}", id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST RUNS
    // ═══════════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> getRunsByProject(Long projectId) {
        List<TpTestRun> runs = runRepo.findByProjectIdOrderByCreatedAtDesc(projectId);
        return enrichRuns(runs);
    }

    public List<Map<String, Object>> getAllRuns() {
        List<TpTestRun> runs = runRepo.findAllByOrderByCreatedAtDesc();
        return enrichRuns(runs);
    }

    public List<Map<String, Object>> getRunsByAssignedTo(String assignedTo) {
        List<TpTestRun> runs = runRepo.findByAssignedToOrderByCreatedAtDesc(assignedTo);
        return enrichRuns(runs);
    }

    private List<Map<String, Object>> enrichRuns(List<TpTestRun> runs) {
        return runs.stream().map(run -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", run.getId());
            map.put("projectId", run.getProjectId());
            map.put("suiteId", run.getSuiteId());
            map.put("name", run.getName());
            map.put("status", run.getStatus());
            map.put("createdBy", run.getCreatedBy());
            map.put("createdAt", run.getCreatedAt());
            map.put("assignedTo", run.getAssignedTo());
            map.put("deadline", run.getDeadline());
            map.put("testType", run.getTestType());
            map.put("stbBoxName", run.getStbBoxName());

            long total = runCaseRepo.countByRunId(run.getId());
            long passed = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "PASSED");
            long failed = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "FAILED");
            long blocked = runCaseRepo.countByRunIdAndLatestStatus(run.getId(), "BLOCKED");

            map.put("totalCases", total);
            map.put("passedCount", passed);
            map.put("failedCount", failed);
            map.put("blockedCount", blocked);
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public TpTestRun createRun(TpTestRun run) {
        TpTestRun saved = runRepo.save(run);

        // Auto-populate run cases from suite
        List<TpTestCase> cases = getCasesBySuite(run.getSuiteId());
        for (TpTestCase tc : cases) {
            TpRunCase rc = TpRunCase.builder()
                    .runId(saved.getId())
                    .caseId(tc.getId())
                    .build();
            runCaseRepo.save(rc);
        }
        log.info("Created run '{}' with {} cases from suite {}", saved.getName(), cases.size(), run.getSuiteId());
        return saved;
    }

    @Transactional
    public void deleteRun(Long id) {
        deleteRunCascade(id);
        runRepo.deleteById(id);
        log.info("Deleted run: id={}", id);
    }

    private void deleteRunCascade(Long runId) {
        List<TpRunCase> runCases = runCaseRepo.findByRunId(runId);
        List<Long> runCaseIds = runCases.stream().map(TpRunCase::getId).collect(Collectors.toList());
        if (!runCaseIds.isEmpty()) {
            resultRepo.deleteByRunCaseIdIn(runCaseIds);
        }
        runCaseRepo.deleteByRunId(runId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RUN CASES
    // ═══════════════════════════════════════════════════════════════════════

    public List<Map<String, Object>> getRunCases(Long runId) {
        List<TpRunCase> runCases = runCaseRepo.findByRunId(runId);
        return runCases.stream().map(rc -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rc.getId());
            map.put("runId", rc.getRunId());
            map.put("caseId", rc.getCaseId());
            map.put("latestStatus", rc.getLatestStatus());

            // Enrich with case details
            caseRepo.findById(rc.getCaseId()).ifPresent(tc -> {
                map.put("testCaseTitle", tc.getTitle());
                map.put("preconditions", tc.getPreconditions());
                map.put("steps", tc.getSteps());
                map.put("expectedResult", tc.getExpectedResult());
                map.put("actualResult", tc.getActualResult());
                map.put("remark", tc.getRemark());
                map.put("assignedTo", tc.getAssignedTo());
            });
            return map;
        }).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESULTS
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public TpResult addResult(Long runCaseId, TpResult result) {
        result.setRunCaseId(runCaseId);
        TpResult saved = resultRepo.save(result);

        // Update latest status on run case
        TpRunCase rc = runCaseRepo.findById(runCaseId)
                .orElseThrow(() -> new RuntimeException("RunCase not found: " + runCaseId));
        rc.setLatestStatus(result.getStatus());
        runCaseRepo.save(rc);

        log.info("Added result for runCase {}: status={}", runCaseId, result.getStatus());
        return saved;
    }

    public List<TpResult> getResults(Long runCaseId) {
        return resultRepo.findByRunCaseIdOrderByExecutedAtDesc(runCaseId);
    }
}
