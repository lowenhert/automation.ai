package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScreenFingerprintTest {

    static CapturedScreen screen(String phash, String... lines) {
        CapturedScreen s = CapturedScreen.builder().captureId("00000000-0000-4000-8000-000000000001")
                .width(1080).height(2340).phash(phash).build();
        // Status bar line (clock) at the top, then the given lines
        s.addTextRegion(region("12:49", 40));
        int y = 300;
        for (String line : lines) {
            s.addTextRegion(region(line, y));
            y += 150;
        }
        return s;
    }

    static ScreenTextRegion region(String text, int y) {
        return ScreenTextRegion.builder().text(text).confidence(95.0).x(100).y(y).width(400).height(40)
                .regionIndex(y).regionType("OCR_TEXT").build();
    }

    @Test
    void tokensIgnoreStatusBarNumbersAndShortNoise() {
        ScreenFingerprint fp = ScreenFingerprint.of(screen("c5ffd59b90a02ae0", "Network & internet", "Wi-Fi 5 GHz", "A 100%"));
        assertEquals(Set.of("network", "internet", "wi", "fi", "ghz"), fp.tokens());
    }

    @Test
    void phashDistance() {
        assertEquals(0, ScreenFingerprint.phashDistance("c5ffd59b90a02ae0", "c5ffd59b90a02ae0"));
        assertEquals(4, ScreenFingerprint.phashDistance("000000000000000f", "0000000000000000"));
        assertEquals(64, ScreenFingerprint.phashDistance("ffffffffffffffff", "0000000000000000"));
        assertEquals(-1, ScreenFingerprint.phashDistance(null, "0000000000000000"));
        assertEquals(-1, ScreenFingerprint.phashDistance("xyz", "0000000000000000"));
    }

    @Test
    void focusChangeOnSameScreenMatches() {
        ScreenFingerprint a = ScreenFingerprint.of(screen("c5ffd59b90a02ae0", "Settings", "Network & internet", "Display", "Sound"));
        // Focus highlight moved: hash differs by a few bits, clock changed, same menu text
        ScreenFingerprint b = ScreenFingerprint.of(screen("c5ffd59b90a02000", "Settings", "Network & internet", "Display", "Sound"));
        assertTrue(a.matchScore(b) >= 0);
    }

    @Test
    void differentMenusDoNotMatchEvenWithSharedHeader() {
        ScreenFingerprint settings = ScreenFingerprint.of(screen("c5ffd59b90a02ae0", "Settings", "Network & internet", "Display", "Sound"));
        ScreenFingerprint network = ScreenFingerprint.of(screen("c5ffd59b90a02ae0", "Network & internet", "Wi-Fi", "Ethernet", "Proxy"));
        assertEquals(-1, settings.matchScore(network));
    }

    @Test
    void textRichButVisuallyUnrelatedDoesNotMatch() {
        ScreenFingerprint a = ScreenFingerprint.of(screen("0000000000000000", "Settings", "Network internet", "Display"));
        ScreenFingerprint b = ScreenFingerprint.of(screen("ffffffff00000000", "Settings", "Network internet", "Display", "Sound", "Apps"));
        // 32-bit hash difference and only partial text overlap
        assertEquals(-1, a.matchScore(b));
    }

    @Test
    void sparseTextScreensUseStrictHash() {
        ScreenFingerprint video1 = ScreenFingerprint.of(screen("abcdefabcdefabcd"));
        ScreenFingerprint video2 = ScreenFingerprint.of(screen("abcdefabcdefabcc"));
        ScreenFingerprint other = ScreenFingerprint.of(screen("1234123412341234"));
        assertTrue(video1.matchScore(video2) >= 0);
        assertEquals(-1, video1.matchScore(other));
        assertEquals(-1, ScreenFingerprint.of(screen(null)).matchScore(ScreenFingerprint.of(screen(null))));
    }

    @Test
    void signatureRoundTrips() {
        ScreenFingerprint fp = ScreenFingerprint.of(screen("c5ffd59b90a02ae0", "Network & internet", "Wi-Fi"));
        assertEquals(fp.tokens(), ScreenFingerprint.parseSignature(fp.signature()));
        assertTrue(ScreenFingerprint.parseSignature(null).isEmpty());
    }

    @Test
    void jaccard() {
        assertEquals(0.5, ScreenFingerprint.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")), 1e-9);
        assertEquals(0, ScreenFingerprint.jaccard(Set.of(), Set.of()));
        assertEquals(1.0, ScreenFingerprint.jaccard(Set.copyOf(List.of("x")), Set.of("x")));
    }
}
