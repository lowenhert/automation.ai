package com.lowenhert.patchmanagement.core.script.service;

import com.lowenhert.patchmanagement.core.script.dto.ScriptExecutionResponseDTO;
import com.lowenhert.patchmanagement.core.script.dto.ScriptLogDTO;
import com.lowenhert.patchmanagement.core.script.mapper.ScriptExecutionMapper;
import com.lowenhert.patchmanagement.core.script.model.*;
import com.lowenhert.patchmanagement.core.script.repository.ScriptExecutionRepository;
import com.lowenhert.patchmanagement.core.script.repository.ScriptRepository;
import com.lowenhert.patchmanagement.core.script.util.AnsibleExecutor;
import com.lowenhert.patchmanagement.core.script.util.PowerShellExecutor;
import com.lowenhert.patchmanagement.core.script.util.PythonExecutor;
import com.lowenhert.patchmanagement.core.script.util.ScriptExecutor;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class ScriptExecutorServiceImpl implements ScriptExecutorService {

    private final ScriptRepository scriptRepository;
    private final ScriptExecutionRepository executionRepository;
    private final ScriptExecutionMapper mapper;
    private final Map<ScriptType, ScriptExecutor> executors = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    // Map<ExecutionId, Process> for cancellation support
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    public ScriptExecutorServiceImpl(
            ScriptRepository scriptRepository,
            ScriptExecutionRepository executionRepository,
            ScriptExecutionMapper mapper,
            SimpMessagingTemplate messagingTemplate,
            PythonExecutor pythonExecutor,
            PowerShellExecutor powerShellExecutor,
            AnsibleExecutor ansibleExecutor
    ) {
        this.scriptRepository = scriptRepository;
        this.executionRepository = executionRepository;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;

        // Explicitly register executors for each ScriptType
        executors.put(ScriptType.PYTHON, pythonExecutor);
        executors.put(ScriptType.POWERSHELL, powerShellExecutor);
        executors.put(ScriptType.ANSIBLE, ansibleExecutor);
    }

    @Override
    @Transactional
    public ScriptExecutionResponseDTO executeScript(Long scriptId) {
        Script script = scriptRepository.findById(scriptId).orElseThrow(() -> new RuntimeException("Script not found with id: " + scriptId));

        ScriptExecution execution = new ScriptExecution();
        execution.setScript(script);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setExecutedAt(java.time.LocalDateTime.now());
        execution = executionRepository.save(execution);

        try {
            ScriptExecutor executor = executors.get(script.getType());
            if (executor == null) {
                throw new RuntimeException("No executor registered for script type: " + script.getType());
            }

            ExecutionResult result = executor.execute(
                    script.getContent(),
                    createLogConsumer(execution.getId())
            );
            execution.updateFromResult(result);
        } catch (Exception e) {
            handleExecutionFailure(execution, e);
        }

        return mapper.toDto(executionRepository.save(execution));
    }
  
    @Override
    @Transactional
    public void executeScriptStream(Long scriptId, OutputStream outputStream) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new RuntimeException("Script not found with id: " + scriptId));

        ScriptExecution execution = new ScriptExecution();
        execution.setScript(script);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setExecutedAt(java.time.LocalDateTime.now());
        execution = executionRepository.save(execution);

        try {
            ScriptExecutor executor = executors.get(script.getType());
            if (executor == null) {
                safeWrite(outputStream, "No executor for type: " + script.getType() + "\n");
                return;
            }

            // Pass a logConsumer that stops if client disconnected
            executor.execute(script.getContent(), logLine -> {
                if (!isStreamOpen(outputStream)) {
                    throw new ClientDisconnectedException();
                }
                safeWrite(outputStream, logLine + "\n");
            });

            execution.setStatus(ExecutionStatus.COMPLETED);
            safeWrite(outputStream, "Execution Completed!\n");

        } catch (ClientDisconnectedException e) {
            System.err.println("Client disconnected — stopping stream early.");
            execution.setStatus(ExecutionStatus.CANCELLED);

        } catch (Exception e) {
            handleExecutionFailure(execution, e);
            safeWrite(outputStream, "Error: " + e.getMessage() + "\n");

        } finally {
            executionRepository.save(execution);
            closeStream(outputStream);
        }
    }

    /**
     * Writes and flushes text to the stream.
     * Throws ClientDisconnectedException if stream is broken.
     */
    private void safeWrite(OutputStream outputStream, String text) {
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new ClientDisconnectedException();
        }
    }

    /**
     * Checks if the stream is still open by attempting a flush.
     */
    private boolean isStreamOpen(OutputStream outputStream) {
        try {
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Flushes and closes stream quietly.
     */
    private void closeStream(OutputStream outputStream) {
        try {
            outputStream.flush();
        } catch (IOException ignored) {}
        try {
            outputStream.close();
        } catch (IOException ignored) {}
    }

    /**
     * Exception used to stop execution when client disconnects.
     */
    private static class ClientDisconnectedException extends RuntimeException {}


    @Override
    public ScriptExecutionResponseDTO getExecutionStatus(Long executionId) {
        return mapper.toDto(executionRepository.findById(executionId)
                .orElseThrow(() -> new RuntimeException("Execution not found with id: " + executionId)));
    }

    @Override
    public void cancelExecution(Long executionId) {
        Process process = runningProcesses.get(executionId);
        if (process != null) {
            process.destroy();
            executionRepository.findById(executionId).ifPresent(exec -> {
                exec.setStatus(ExecutionStatus.CANCELLED);
                executionRepository.save(exec);
            });
        }
    }

    // Sends logs via WebSocket topic so frontend can subscribe and get live logs
    private Consumer<String> createLogConsumer(Long executionId) {
        return log -> messagingTemplate.convertAndSend(
                "/topic/script/" + executionId + "/logs",
                new ScriptLogDTO(log, Instant.now())
        );
    }

    private void handleExecutionFailure(ScriptExecution execution, Exception e) {
        execution.setStatus(ExecutionStatus.FAILED);
        execution.setError("Execution error: " + e.getMessage());
    }
}
