package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdbCommandExecutorTest {

    @Test
    void missingExecutableIsReportedAsServiceUnavailable() {
        AdbCommandExecutor executor = new AdbCommandExecutor(new ProcessRunner(), "this-adb-does-not-exist-12345", 2000);
        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> executor.execute(List.of("devices", "-l")));
        assertTrue(ex.getMessage().contains("app.adb.path"));
    }

    @Test
    void blankPathIsRejected() {
        assertThrows(IllegalStateException.class, () -> new AdbCommandExecutor(new ProcessRunner(), " ", 1000));
    }
}
