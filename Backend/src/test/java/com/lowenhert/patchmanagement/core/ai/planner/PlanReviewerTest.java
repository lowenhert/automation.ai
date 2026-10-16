package com.lowenhert.patchmanagement.core.ai.planner;

import com.lowenhert.patchmanagement.core.ai.planner.GeneratedTestPlan.*;
import com.lowenhert.patchmanagement.core.ai.planner.PlanReviewer.Evidence;
import com.lowenhert.patchmanagement.core.ai.planner.PlanReviewer.FlagType;
import com.lowenhert.patchmanagement.core.ai.planner.PlanReviewer.Severity;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanReviewerTest {

    static final PlannerKnowledge KNOWLEDGE = new PlannerKnowledge("emulator-5554", List.of(
            new PlannerKnowledge.Screen(1, "Home Launcher", "HOME", "SAFE", List.of("Settings", "Apps", "Movies")),
            new PlannerKnowledge.Screen(2, "Settings", "SETTINGS", "SAFE",
                    List.of("Search Settings", "Network & internet", "Device preferences")),
            new PlannerKnowledge.Screen(3, "Network & internet", "SETTINGS", "SAFE", List.of("Wi-Fi", "Ethernet", "Proxy")),
            new PlannerKnowledge.Screen(4, "Device preferences", "SETTINGS", "DESTRUCTIVE", List.of("Factory reset", "About"))
    ), List.of(
            new PlannerKnowledge.Transition(1, RemoteKey.SELECT, 2, 3),
            new PlannerKnowledge.Transition(2, RemoteKey.SELECT, 3, 2),
            new PlannerKnowledge.Transition(2, RemoteKey.SELECT, 4, 1),
            new PlannerKnowledge.Transition(3, RemoteKey.BACK, 2, 2)
    ), 4, 4);

    static Step step(int order, Action action, Long from, Long to, String target) {
        return new Step(order, action, null, from, to, null, target, "expected state", 5000, OnFailure.ABORT);
    }

    static GeneratedTestPlan plan(List<Step> steps, List<Assertion> assertions) {
        return new GeneratedTestPlan("Open network settings", "summary", List.of(new Precondition("On home screen", 1L)),
                steps, assertions, new FailureHandling(1, Recovery.HOME, ""), List.of());
    }

    static GeneratedTestPlan groundedPlan() {
        return plan(List.of(
                step(1, Action.SELECT, 1L, 2L, "Settings"),
                step(2, Action.SELECT, 2L, 3L, "Network & internet"),
                step(3, Action.ASSERT_TEXT_VISIBLE, 3L, 3L, "Wi-Fi")
        ), List.of(new Assertion(3, AssertionType.SCREEN_IS, 3L, null, "Network screen is shown")));
    }

    @Test
    void groundedPlanHasNoFlagsAndObservedEvidence() {
        PlanReviewer.Review review = PlanReviewer.review(groundedPlan(), KNOWLEDGE);
        assertTrue(review.flags().isEmpty(), review.flags().toString());
        assertEquals(Evidence.OBSERVED, review.stepEvidence().get(1));
        assertEquals(Evidence.OBSERVED, review.stepEvidence().get(2));
        assertEquals(Evidence.NOT_APPLICABLE, review.stepEvidence().get(3));
        assertTrue(PlanReviewer.structuralErrors(groundedPlan(), 20).isEmpty());
    }

    @Test
    void flagsUnknownScreensLocatorsUnsupportedActionsAndUnobservedTransitions() {
        GeneratedTestPlan p = plan(List.of(
                step(1, Action.DPAD_DOWN, 1L, 3L, "Movies"),                 // valid locator, transition never observed
                step(2, Action.SELECT, 3L, 99L, "Bluetooth"),               // unknown screen + locator not on screen 3
                new Step(3, Action.UNSUPPORTED, "Type the Wi-Fi password", 99L, null, "Password dialog",
                        null, "Password entered", 5000, OnFailure.ABORT)
        ), List.of(new Assertion(3, AssertionType.TEXT_VISIBLE, 3L, "Connected", "Shows connected")));

        PlanReviewer.Review review = PlanReviewer.review(p, KNOWLEDGE);
        List<FlagType> types = review.flags().stream().map(PlanReviewer.Flag::type).toList();

        assertTrue(types.contains(FlagType.UNOBSERVED_TRANSITION), types.toString());
        assertEquals(Evidence.NOT_OBSERVED, review.stepEvidence().get(1));
        assertTrue(types.contains(FlagType.UNKNOWN_SCREEN));
        assertEquals(Evidence.UNKNOWN, review.stepEvidence().get(2));
        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.UNKNOWN_LOCATOR && f.message().contains("Bluetooth")));
        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.UNKNOWN_LOCATOR && f.message().contains("Connected")));
        PlanReviewer.Flag unsupported = review.flags().stream().filter(f -> f.type() == FlagType.UNSUPPORTED_ACTION).findFirst().orElseThrow();
        assertEquals(Severity.ERROR, unsupported.severity());
        assertEquals(3, unsupported.stepOrder());
        assertTrue(unsupported.message().contains("Wi-Fi password"));
    }

    @Test
    void flagsSelectOnDestructiveScreenAndSequenceGaps() {
        GeneratedTestPlan p = plan(List.of(
                step(1, Action.SELECT, 1L, 2L, "Settings"),
                step(2, Action.SELECT, 4L, 4L, "Factory reset")             // gap: previous ended on 2
        ), List.of());
        PlanReviewer.Review review = PlanReviewer.review(p, KNOWLEDGE);

        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.DESTRUCTIVE_STEP && f.severity() == Severity.ERROR));
        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.SEQUENCE_GAP && f.stepOrder() == 2));
        assertEquals(1, review.count(Severity.ERROR));
    }

    @Test
    void emptyKnowledgeFlagsEverythingAsUnverified() {
        PlannerKnowledge none = new PlannerKnowledge("192.168.1.20:5555", List.of(), List.of(), 0, 0);
        GeneratedTestPlan p = plan(List.of(new Step(1, Action.SELECT, null, null, null, "Settings screen", "Settings",
                "Settings opens", 5000, OnFailure.ABORT)), List.of());
        PlanReviewer.Review review = PlanReviewer.review(p, none);
        assertEquals(FlagType.NO_SCREEN_KNOWLEDGE, review.flags().get(0).type());
        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.UNKNOWN_SCREEN));
        assertTrue(review.flags().stream().anyMatch(f -> f.type() == FlagType.UNKNOWN_LOCATOR));
        assertEquals(Evidence.UNKNOWN, review.stepEvidence().get(1));
    }

    @Test
    void structuralErrorsAreReportedForRepair() {
        List<Step> steps = new ArrayList<>(List.of(
                step(1, Action.SELECT, 1L, 2L, "Settings"),
                step(3, Action.ASSERT_TEXT_VISIBLE, 2L, 2L, null),
                new Step(3, Action.UNSUPPORTED, " ", null, null, null, null, "x", 5000, OnFailure.ABORT),
                new Step(4, Action.ASSERT_SCREEN, null, 2L, null, null, null, "x", 5000, OnFailure.ABORT)
        ));
        GeneratedTestPlan p = plan(steps, List.of(
                new Assertion(9, AssertionType.TEXT_VISIBLE, 2L, null, "missing text"),
                new Assertion(1, AssertionType.SCREEN_IS, null, null, "no screen")));

        List<String> errors = PlanReviewer.structuralErrors(p, 3);
        String all = String.join("\n", errors);
        assertTrue(all.contains("at most 3"), all);
        assertTrue(all.contains("steps[1].order must be 2"), all);
        assertTrue(all.contains("requestedAction"), all);
        assertTrue(all.contains("ASSERT_TEXT_VISIBLE requires target"), all);
        assertTrue(all.contains("ASSERT_SCREEN requires"), all);
        assertTrue(all.contains("afterStep refers to a step that does not exist"), all);
        assertTrue(all.contains("TEXT_VISIBLE requires text"), all);
        assertTrue(all.contains("SCREEN_IS requires screenId"), all);
    }

    @Test
    void controlCharactersAreStructuralErrors() {
        GeneratedTestPlan p = plan(List.of(step(1, Action.SELECT, 1L, 2L, "Settings\u0000")), List.of());
        assertTrue(PlanReviewer.structuralErrors(p, 20).stream().anyMatch(e -> e.contains("control characters")));
    }

    @Test
    void locatorMatchingIsNormalisedButNotLoose() {
        List<String> known = List.of("Network & internet", "Wi-Fi", "Device preferences");
        assertTrue(PlanReviewer.textKnown(known, "network & internet"));
        assertFalse(PlanReviewer.textKnown(known, "Network and internet"));
        assertFalse(PlanReviewer.textKnown(known, "WiFi"));
        assertTrue(PlanReviewer.textKnown(known, "Wi Fi"));
        assertTrue(PlanReviewer.textKnown(known, "Device preferences menu"));
        assertFalse(PlanReviewer.textKnown(known, "Ne"));
        assertFalse(PlanReviewer.textKnown(known, ""));
    }
}
