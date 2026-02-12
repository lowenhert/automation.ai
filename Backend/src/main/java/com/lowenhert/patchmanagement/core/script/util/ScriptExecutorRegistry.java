package com.lowenhert.patchmanagement.core.script.util;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
public class ScriptExecutorRegistry {
    private final Map<String, ScriptExecutor> executors = new HashMap<>();
    
    public ScriptExecutorRegistry(
            PythonExecutor pythonExecutor,
            PowerShellExecutor powerShellExecutor,
            AnsibleExecutor ansibleExecutor) {
        
        executors.put("python", pythonExecutor);
        executors.put("powershell", powerShellExecutor);
        executors.put("ansible", ansibleExecutor);
    }
    
    public ScriptExecutor getExecutor(String type) {
        return executors.get(type.toLowerCase());
    }
}