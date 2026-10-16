package com.lowenhert.patchmanagement.core.adb;

import com.lowenhert.patchmanagement.core.adb.dto.AdbResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Injection guard for the legacy /api/adb/command endpoint; rejected commands never reach adb. */
class AdbServiceCommandGuardTest {

    private final AdbService service = new AdbService();

    @ParameterizedTest
    @ValueSource(strings = {
            "getprop; reboot", "getprop && rm -rf /sdcard", "dumpsys | sh", "getprop `reboot`",
            "getprop $(reboot)", "settings get x > /sdcard/a", "pm list packages\nreboot", "input text a\\;b",
            "reboot", "rm -rf /", ""
    })
    void rejectsUnsafeCommands(String command) {
        AdbResponse response = service.runCommand("192.168.1.10", command);
        assertFalse(response.isSuccess());
        assertEquals("Command not allowed", response.getError());
    }
}
