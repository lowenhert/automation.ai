package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DestructiveActionGuardTest {

    private final DestructiveActionGuard guard = new DestructiveActionGuard(DestructiveActionGuard.DEFAULT_KEYWORDS);

    private static CapturedScreen withText(String text) {
        return CapturedScreen.builder().extractedText(text).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Factory reset", "Erase all data (factory reset)", "Uninstall", "Buy for $3.99",
            "Rent HD", "Sign out", "Clear data", "Delete account", "RESTART NOW", "Confirm"})
    void blocksSelectOnDestructiveText(String text) {
        assertTrue(guard.selectBlockReason(null, withText("Device preferences\n" + text)).isPresent(), text);
        assertEquals(ScreenNode.RiskLevel.DESTRUCTIVE, guard.assess(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Settings\nNetwork & internet\nDisplay", "Movies\nTV shows\nApps", "Updates available",
            "Presets", "Formatting tips", "Restoration hardware", "Buyer guide", "Paypal"})
    void allowsOrdinaryScreensAndAvoidsSubstringFalsePositives(String text) {
        assertTrue(guard.selectBlockReason(null, withText(text)).isEmpty(), text);
    }

    @Test
    void nodeMarkedDestructiveBlocksEvenWithoutKeywords() {
        ScreenNode node = ScreenNode.builder().label("Danger zone").riskLevel(ScreenNode.RiskLevel.DESTRUCTIVE).build();
        assertTrue(guard.selectBlockReason(node, withText("Continue")).orElseThrow().contains("DESTRUCTIVE"));
    }

    @Test
    void customKeywordList() {
        DestructiveActionGuard custom = new DestructiveActionGuard(" Format , Wipe ");
        assertTrue(custom.findKeyword("Format USB drive").isPresent());
        assertTrue(custom.findKeyword("Factory reset").isEmpty());
    }
}
