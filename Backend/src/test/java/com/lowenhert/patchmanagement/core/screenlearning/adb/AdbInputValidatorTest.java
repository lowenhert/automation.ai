package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdbInputValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.10", "10.0.2.2", "localhost", "stb-01.lab.local"})
    void acceptsValidHosts(String host) {
        assertEquals(host, AdbInputValidator.requireHost(" " + host + " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "256.1.1.1", "192.168.1.10;rm -rf /", "host name", "$(whoami)",
            "-a", "a&b", "1.2.3.4|calc", "`id`", "host\nname"
    })
    void rejectsInvalidHosts(String host) {
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireHost(host));
    }

    @Test
    void portDefaultsAndRange() {
        assertEquals(5555, AdbInputValidator.requirePort(null));
        assertEquals(5037, AdbInputValidator.requirePort(5037));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requirePort(0));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requirePort(70000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"emulator-5554", "R58M123ABC", "192.168.1.10:5555", "adb-R58M-AbC._adb-tls-connect._tcp"})
    void acceptsValidSerials(String serial) {
        assertEquals(serial, AdbInputValidator.requireSerial(serial));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-s", "--help", "abc def", "abc;ls", "a/b", "a\\b", "x&y"})
    void rejectsInvalidSerials(String serial) {
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireSerial(serial));
    }

    @Test
    void networkSerialValidation() {
        assertEquals("192.168.1.10:5555", AdbInputValidator.requireNetworkSerial("192.168.1.10:5555"));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireNetworkSerial("emulator-5554"));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireNetworkSerial("192.168.1.10:"));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireNetworkSerial("192.168.1.10:99999"));
        assertThrows(BadRequestException.class, () -> AdbInputValidator.requireNetworkSerial("192.168.1.10:abc"));
    }
}
