package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Script lifecycle: DRAFT → (validate) → APPROVED. Any content change returns the script to DRAFT; only the exact
 * validated and approved content can be executed.
 */
@Slf4j
@Service
public class TestScriptService {

    static final int MAX_CONTENT_CHARS = 200_000;
    static final int MAX_NAME = 150;

    private final TestScriptRepository scriptRepository;
    private final ScriptExecutionRepository executionRepository;
    private final ScriptValidator validator;
    private final ScriptExecutionService executionService;
    private final ObjectMapper objectMapper;

    public TestScriptService(TestScriptRepository scriptRepository, ScriptExecutionRepository executionRepository,
                             ScriptValidator validator, ScriptExecutionService executionService, ObjectMapper objectMapper) {
        this.scriptRepository = scriptRepository;
        this.executionRepository = executionRepository;
        this.validator = validator;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ScriptSummary> list(int page, int size, Long testCaseId) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        var result = testCaseId == null ? scriptRepository.findAllBy(pageable) : scriptRepository.findByTestCaseId(testCaseId, pageable);
        return PageResponse.of(result, this::summary);
    }

    public ScriptDetail get(Long id) {
        return detail(find(id));
    }

    public ScriptDetail create(ScriptRequest request, String username) {
        if (request == null) throw new BadRequestException("Request body is required");
        if (request.language() == null) throw new BadRequestException("language is required (PYTEST or ROBOT)");
        TestScript script = TestScript.builder()
                .testCaseId(request.testCaseId())
                .name(requireName(request.name()))
                .language(request.language())
                .content(requireContent(request.content()))
                .origin(TestScript.Origin.MANUAL)
                .status(TestScript.Status.DRAFT)
                .createdBy(username)
                .build();
        script.setContentHash(hash(script.getContent()));
        return detail(scriptRepository.save(script));
    }

    /** Saves a generated script as a DRAFT. Generated code is never executed without validation and approval. */
    TestScript saveGenerated(Long testCaseId, String name, TestScript.Language language, String content, String username) {
        TestScript script = TestScript.builder()
                .testCaseId(testCaseId)
                .name(requireName(name))
                .language(language)
                .content(requireContent(content))
                .origin(TestScript.Origin.GENERATED)
                .status(TestScript.Status.DRAFT)
                .createdBy(username)
                .build();
        script.setContentHash(hash(script.getContent()));
        return scriptRepository.save(script);
    }

    public ScriptDetail update(Long id, ScriptUpdateRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        TestScript script = find(id);
        if (request.expectedVersion() != null && !request.expectedVersion().equals(script.getVersion())) {
            throw new ConflictException("Script was modified by someone else; reload it before saving");
        }
        if (request.name() != null) script.setName(requireName(request.name()));
        if (request.content() != null) {
            String content = requireContent(request.content());
            String newHash = hash(content);
            if (!newHash.equals(script.getContentHash())) {
                requireNotRunning(List.of(id), "edit");
                script.setContent(content);
                script.setContentHash(newHash);
                script.setValidationJson(null);
                script.setValidatedHash(null);
                script.setValidatedAt(null);
                revokeApproval(script);
            }
        }
        return detail(scriptRepository.save(script));
    }

    public ScriptDetail rename(Long id, String name) {
        TestScript script = find(id);
        script.setName(requireName(name));
        return detail(scriptRepository.save(script));
    }

    /** Copies content only; the copy is a DRAFT that needs its own validation and approval. */
    public ScriptDetail duplicate(Long id, String username) {
        TestScript source = find(id);
        return detail(scriptRepository.save(copyAsDraft(source, source.getTestCaseId(), copyName(source.getName()), username)));
    }

    TestScript copyAsDraft(TestScript source, Long testCaseId, String name, String username) {
        TestScript copy = TestScript.builder()
                .testCaseId(testCaseId)
                .name(name)
                .language(source.getLanguage())
                .content(source.getContent())
                .contentHash(source.getContentHash())
                .origin(source.getOrigin())
                .status(TestScript.Status.DRAFT)
                .createdBy(username)
                .build();
        return copy;
    }

    public ScriptDetail validate(Long id) {
        TestScript script = find(id);
        Validation validation = validator.validate(script.getLanguage(), script.getContent());
        script.setValidationJson(write(validation));
        script.setValidatedAt(Instant.now());
        script.setValidatedHash(validation.valid() ? script.getContentHash() : null);
        if (!validation.valid()) revokeApproval(script);
        return detail(scriptRepository.save(script));
    }

    /** Approves exactly the content the reviewer saw (identified by its hash). */
    public ScriptDetail approve(Long id, ApproveRequest request, String username) {
        TestScript script = find(id);
        if (request == null || request.contentHash() == null || !request.contentHash().equals(script.getContentHash())) {
            throw new ConflictException("Script content changed since it was reviewed; reload and review it again");
        }
        if (!script.getContentHash().equals(script.getValidatedHash())) {
            throw new BadRequestException("Validate the current content successfully before approving it");
        }
        script.setStatus(TestScript.Status.APPROVED);
        script.setApprovedHash(script.getContentHash());
        script.setApprovedBy(username);
        script.setApprovedAt(Instant.now());
        log.info("Script {} approved by {} (hash {})", id, username, script.getContentHash());
        return detail(scriptRepository.save(script));
    }

    public ScriptDetail revoke(Long id) {
        TestScript script = find(id);
        revokeApproval(script);
        return detail(scriptRepository.save(script));
    }

    /** Read-only view of what would run, with a fresh validation (never executes anything). */
    public Preview preview(Long id) {
        TestScript script = find(id);
        Validation validation = validator.validate(script.getLanguage(), script.getContent());
        return new Preview(script.getId(), script.getName(), script.getLanguage(), script.getContent(),
                script.getContentHash(), validation, script.isExecutable() && validation.valid());
    }

    public DeleteImpact deleteImpact(Long id) {
        TestScript script = find(id);
        List<Long> ids = List.of(id);
        return new DeleteImpact("SCRIPT", id, script.getName(), 0, 1, executionRepository.countByScriptIdIn(ids),
                executionRepository.existsByScriptIdInAndStatus(ids, ScriptExecution.Status.RUNNING));
    }

    public void delete(Long id, boolean confirm) {
        if (!confirm) throw new BadRequestException("Deleting a script requires confirm=true");
        TestScript script = find(id);
        deleteScripts(List.of(script));
    }

    /** Removes scripts with their executions and run artifacts. Refuses while any of them is running. */
    void deleteScripts(List<TestScript> scripts) {
        if (scripts.isEmpty()) return;
        List<Long> ids = scripts.stream().map(TestScript::getId).toList();
        requireNotRunning(ids, "delete");
        executionService.deleteExecutionsOf(ids);
        scriptRepository.deleteAll(scripts);
        log.info("Deleted scripts {}", ids);
    }

    void requireNotRunning(List<Long> scriptIds, String verb) {
        if (!scriptIds.isEmpty() && executionRepository.existsByScriptIdInAndStatus(scriptIds, ScriptExecution.Status.RUNNING)) {
            throw new ConflictException("Cannot " + verb + " while an execution is running; stop it first");
        }
    }

    ScriptSummary summary(TestScript s) {
        Validation v = readValidation(s);
        return ScriptSummary.of(s, v == null ? null : v.valid());
    }

    private ScriptDetail detail(TestScript s) {
        Validation v = readValidation(s);
        // validation_json is cleared whenever the content changes, so a stored validation is always current
        boolean current = v != null;
        return new ScriptDetail(s.getId(), s.getTestCaseId(), s.getName(), s.getLanguage(), s.getOrigin(), s.getStatus(),
                s.isExecutable(), s.getContent(), s.getContentHash(), s.getVersion(), v, current, s.getApprovedHash(),
                s.getApprovedBy(), s.getApprovedAt(), s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private static void revokeApproval(TestScript script) {
        script.setStatus(TestScript.Status.DRAFT);
        script.setApprovedHash(null);
        script.setApprovedBy(null);
        script.setApprovedAt(null);
    }

    TestScript find(Long id) {
        return scriptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Script " + id + " not found"));
    }

    private Validation readValidation(TestScript s) {
        if (s.getValidationJson() == null) return null;
        try {
            return objectMapper.readValue(s.getValidationJson(), Validation.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String write(Validation v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String requireName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) throw new BadRequestException("name is required");
        if (n.length() > MAX_NAME) throw new BadRequestException("name must be at most " + MAX_NAME + " characters");
        if (n.chars().anyMatch(Character::isISOControl)) throw new BadRequestException("name must not contain control characters");
        return n;
    }

    static String requireContent(String content) {
        if (content == null || content.isBlank()) throw new BadRequestException("content is required");
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new BadRequestException("content must be at most " + MAX_CONTENT_CHARS + " characters");
        }
        if (content.indexOf('\0') >= 0) throw new BadRequestException("content must not contain NUL characters");
        return content.replace("\r\n", "\n");
    }

    static String copyName(String name) {
        String n = name + " (copy)";
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    public static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
