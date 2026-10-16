package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdbOutputParserTest {

    @Test
    void parsesEmulatorNetworkUsbAndUnauthorizedDevices() {
        String output = """
                * daemon not running; starting now at tcp:5037
                * daemon started successfully
                List of devices attached
                emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:1
                192.168.1.20:5555      device product:stb_prod model:Tata_Play_STB device:stb transport_id:2
                R58M123ABC             unauthorized usb:1-1 transport_id:3
                0123456789             no permissions (user in plugdev group); see [http://developer.android.com/tools/device.html] usb:1-2 transport_id:4

                """;

        List<AdbDeviceDto> devices = AdbOutputParser.parseDevices(output);

        assertEquals(4, devices.size());

        AdbDeviceDto emulator = devices.get(0);
        assertEquals("emulator-5554", emulator.getSerial());
        assertEquals("device", emulator.getState());
        assertEquals("EMULATOR", emulator.getConnectionType());
        assertEquals("sdk gphone64 x86 64", emulator.getModel());
        assertEquals("1", emulator.getTransportId());

        AdbDeviceDto stb = devices.get(1);
        assertEquals("NETWORK", stb.getConnectionType());
        assertEquals("Tata Play STB", stb.getModel());

        assertEquals("unauthorized", devices.get(2).getState());
        assertEquals("USB", devices.get(2).getConnectionType());

        assertEquals("no permissions", devices.get(3).getState());
    }

    @Test
    void emptyListWhenNoDevices() {
        assertTrue(AdbOutputParser.parseDevices("List of devices attached\n\n").isEmpty());
        assertTrue(AdbOutputParser.parseDevices(null).isEmpty());
    }

    @Test
    void parsesGetprop() {
        String output = """
                [ro.build.version.release]: [14]
                [ro.build.version.sdk]: [34]
                [ro.product.model]: [Pixel 7]
                [ro.product.manufacturer]: [Google]
                [empty.prop]: []
                garbage line
                """;
        Map<String, String> props = AdbOutputParser.parseGetprop(output);
        assertEquals("14", props.get("ro.build.version.release"));
        assertEquals("Pixel 7", props.get("ro.product.model"));
        assertEquals("", props.get("empty.prop"));
        assertEquals(5, props.size());
    }

    @Test
    void wmSizePrefersOverride() {
        assertEquals("1080x2400", AdbOutputParser.parseWmSize("Physical size: 1080x2400"));
        assertEquals("720x1280", AdbOutputParser.parseWmSize("Physical size: 1920x1080\nOverride size: 720x1280"));
        assertNull(AdbOutputParser.parseWmSize("error: closed"));
    }

    @Test
    void detectsMdnsWirelessDebuggingAsNetwork() {
        assertEquals("NETWORK", AdbOutputParser.connectionType("adb-R58M123-AbCdEf._adb-tls-connect._tcp"));
    }
}
