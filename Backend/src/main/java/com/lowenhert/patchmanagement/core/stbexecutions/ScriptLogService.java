package com.lowenhert.patchmanagement.core.stbexecutions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Stores real-time log lines per execution and supports SSE subscribers.
 */
@Slf4j
@Service
public class ScriptLogService {

    // executionId → list of log lines
    private final Map<Long, List<String>> logBuffers = new ConcurrentHashMap<>();

    // executionId → list of live subscribers (SSE consumers)
    private final Map<Long, List<Consumer<String>>> subscribers = new ConcurrentHashMap<>();

    public void initExecution(Long executionId) {
        logBuffers.put(executionId, new CopyOnWriteArrayList<>());
    }

    public void appendLog(Long executionId, String line) {
        List<String> buffer = logBuffers.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>());
        buffer.add(line);

        // Notify subscribers
        List<Consumer<String>> subs = subscribers.get(executionId);
        if (subs != null) {
            for (Consumer<String> sub : subs) {
                try {
                    sub.accept(line);
                } catch (Exception e) {
                    // subscriber disconnected
                }
            }
        }
    }

    public List<String> getLogs(Long executionId) {
        return logBuffers.getOrDefault(executionId, List.of());
    }

    public void subscribe(Long executionId, Consumer<String> consumer) {
        subscribers.computeIfAbsent(executionId, k -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    public void unsubscribe(Long executionId, Consumer<String> consumer) {
        List<Consumer<String>> subs = subscribers.get(executionId);
        if (subs != null) {
            subs.remove(consumer);
        }
    }

    public void cleanup(Long executionId) {
        // Keep logs but remove subscribers
        subscribers.remove(executionId);
        // Optionally trim old logs after a while — for now keep them
    }
}
