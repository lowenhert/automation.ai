package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic stand-in for an Android TV device: a few menu screens with a focus cursor.
 * Captures are stored through the real Phase 1 repository so identification runs on real entities.
 */
class FakeTvDevice {

    enum Screen { HOME, SETTINGS, NETWORK, DEVICE_PREFS }

    private static final Map<Screen, List<String>> TEXT = Map.of(
            Screen.HOME, List.of("Home", "Apps", "Movies", "Settings"),
            Screen.SETTINGS, List.of("Settings", "Network & internet", "Device preferences", "Display"),
            Screen.NETWORK, List.of("Network & internet", "Wi-Fi", "Ethernet", "Proxy"),
            Screen.DEVICE_PREFS, List.of("Device preferences", "Factory reset", "Erase all data", "About"));
    private static final Map<Screen, Long> BASE_HASH = Map.of(
            Screen.HOME, 0x0F0F0F0F0F0F0F0FL,
            Screen.SETTINGS, 0x00FF00FF00FF00FFL,
            Screen.NETWORK, 0x3333333333333333L,
            Screen.DEVICE_PREFS, 0x5555555555555555L);

    Screen screen = Screen.HOME;
    int focus = 0;
    final List<RemoteKey> keysSent = Collections.synchronizedList(new ArrayList<>());
    volatile long keyDelayMs = 0;

    synchronized void press(RemoteKey key) {
        keysSent.add(key);
        if (keyDelayMs > 0) {
            try {
                Thread.sleep(keyDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        switch (key) {
            case DPAD_DOWN -> focus = Math.min(focus + 1, 2);
            case DPAD_UP -> focus = Math.max(focus - 1, 0);
            case HOME -> go(Screen.HOME);
            case BACK -> go(switch (screen) {
                case HOME, SETTINGS -> Screen.HOME;
                case NETWORK, DEVICE_PREFS -> Screen.SETTINGS;
            });
            case SELECT -> {
                if (screen == Screen.HOME && focus == 0) go(Screen.SETTINGS);
                else if (screen == Screen.SETTINGS && focus == 0) go(Screen.NETWORK);
                else if (screen == Screen.SETTINGS && focus == 1) go(Screen.DEVICE_PREFS);
                else if (screen == Screen.DEVICE_PREFS) throw new AssertionError("SELECT on a destructive screen reached the device");
            }
            default -> { }
        }
    }

    private void go(Screen next) {
        screen = next;
        focus = 0;
    }

    /** Stores a capture of the current state through the real CapturedScreen repository. */
    synchronized ScreenCaptureResponse capture(String serial, CapturedScreenRepository repository) {
        String id = UUID.randomUUID().toString();
        // Focus moves flip a few low bits, like a highlight changing position
        long hash = BASE_HASH.get(screen) ^ ((long) focus * 0x7);
        CapturedScreen shot = CapturedScreen.builder()
                .captureId(id).deviceSerial(serial).deviceModel("Fake TV").deviceType("EMULATOR")
                .capturedAt(Instant.now()).imageFile(id + ".png").width(1920).height(1080)
                .phash(String.format("%016x", hash)).analysisStatus("SUCCESS").ocrStatus("SUCCESS")
                .extractedText(String.join("\n", TEXT.get(screen)))
                .build();
        int y = 200;
        int index = 1;
        for (String line : TEXT.get(screen)) {
            shot.addTextRegion(ScreenTextRegion.builder().regionIndex(index++).regionType("OCR_TEXT").text(line)
                    .confidence(95.0).x(100).y(y).width(300).height(40).build());
            y += 100;
        }
        shot.setTextRegionCount(shot.getTextRegions().size());
        repository.save(shot);
        return ScreenCaptureResponse.builder().captureId(id).deviceId(serial).status("SUCCESS")
                .width(1920).height(1080).resolution("1920x1080").analysisStatus("SUCCESS").build();
    }
}
