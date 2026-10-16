package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbActionResponse;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ScreenLearningAdbServiceTest {

    private static final String DEVICES = """
            List of devices attached
            emulator-5554          device product:sdk model:sdk_gphone device:emu transport_id:1
            192.168.1.20:5555      offline transport_id:2
            """;

    private AdbCommandExecutor executor;
    private ScreenLearningAdbService service;

    @BeforeEach
    void setUp() {
        executor = mock(AdbCommandExecutor.class);
        service = new ScreenLearningAdbService(executor);
    }

    @Test
    void listDevicesEnrichesOnlineDevicesOnly() {
        when(executor.execute(List.of("devices", "-l"))).thenReturn(new AdbCommandResult(0, DEVICES));
        when(executor.execute(List.of("-s", "emulator-5554", "shell", "getprop"))).thenReturn(new AdbCommandResult(0, """
                [ro.product.model]: [sdk_gphone64]
                [ro.product.manufacturer]: [Google]
                [ro.build.version.release]: [14]
                [ro.build.version.sdk]: [34]
                """));
        when(executor.execute(List.of("-s", "emulator-5554", "shell", "wm", "size")))
                .thenReturn(new AdbCommandResult(0, "Physical size: 1080x2400"));

        List<AdbDeviceDto> devices = service.listDevices();

        assertEquals(2, devices.size());
        AdbDeviceDto emu = devices.get(0);
        assertEquals("sdk_gphone64", emu.getModel());
        assertEquals("Google", emu.getManufacturer());
        assertEquals("14", emu.getAndroidVersion());
        assertEquals("34", emu.getSdkVersion());
        assertEquals("1080x2400", emu.getResolution());

        AdbDeviceDto offline = devices.get(1);
        assertEquals("offline", offline.getState());
        assertNull(offline.getAndroidVersion());
        verify(executor, never()).execute(List.of("-s", "192.168.1.20:5555", "shell", "getprop"));
    }

    @Test
    void listDevicesKeepsDeviceWhenInfoFails() {
        when(executor.execute(List.of("devices", "-l"))).thenReturn(new AdbCommandResult(0, DEVICES));
        when(executor.execute(List.of("-s", "emulator-5554", "shell", "getprop")))
                .thenThrow(new ServiceUnavailableException("ADB command timed out"));

        List<AdbDeviceDto> devices = service.listDevices();

        assertEquals("ADB command timed out", devices.get(0).getInfoError());
    }

    @Test
    void listDevicesFailsOnNonZeroExit() {
        when(executor.execute(anyList())).thenReturn(new AdbCommandResult(1, "error: cannot connect to daemon"));
        assertThrows(ServiceUnavailableException.class, () -> service.listDevices());
    }

    @Test
    void connectSuccess() {
        when(executor.execute(List.of("connect", "192.168.1.20:5555")))
                .thenReturn(new AdbCommandResult(0, "connected to 192.168.1.20:5555"));

        AdbActionResponse response = service.connect("192.168.1.20", null);

        assertTrue(response.isSuccess());
        assertEquals("192.168.1.20:5555", response.getSerial());
    }

    @Test
    void connectAlreadyConnectedIsSuccess() {
        when(executor.execute(List.of("connect", "10.0.0.5:5037")))
                .thenReturn(new AdbCommandResult(0, "already connected to 10.0.0.5:5037"));
        assertTrue(service.connect("10.0.0.5", 5037).isSuccess());
    }

    @Test
    void connectFailureWithZeroExitCodeIsReported() {
        when(executor.execute(List.of("connect", "192.168.1.99:5555")))
                .thenReturn(new AdbCommandResult(0, "failed to connect to '192.168.1.99:5555': Connection refused"));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.connect("192.168.1.99", 5555));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void connectRejectsInjectionWithoutRunningAdb() {
        assertThrows(BadRequestException.class, () -> service.connect("1.2.3.4 && calc", 5555));
        verifyNoInteractions(executor);
    }

    @Test
    void disconnectSuccess() {
        when(executor.execute(List.of("disconnect", "192.168.1.20:5555")))
                .thenReturn(new AdbCommandResult(0, "disconnected 192.168.1.20:5555"));
        assertTrue(service.disconnect("192.168.1.20:5555").isSuccess());
    }

    @Test
    void disconnectFailure() {
        when(executor.execute(List.of("disconnect", "192.168.1.20:5555")))
                .thenReturn(new AdbCommandResult(1, "error: no such device '192.168.1.20:5555'"));
        assertThrows(BadRequestException.class, () -> service.disconnect("192.168.1.20:5555"));
    }

    @Test
    void disconnectRejectsEmulatorSerial() {
        assertThrows(BadRequestException.class, () -> service.disconnect("emulator-5554"));
        verifyNoInteractions(executor);
    }

    @Test
    void getDeviceRequiresKnownSerial() {
        when(executor.execute(List.of("devices", "-l"))).thenReturn(new AdbCommandResult(0, DEVICES));
        assertThrows(ResourceNotFoundException.class, () -> service.getDevice("unknown-serial"));
    }

    @Test
    void getDeviceRejectsOfflineDevice() {
        when(executor.execute(List.of("devices", "-l"))).thenReturn(new AdbCommandResult(0, DEVICES));
        assertThrows(BadRequestException.class, () -> service.getDevice("192.168.1.20:5555"));
    }
}
