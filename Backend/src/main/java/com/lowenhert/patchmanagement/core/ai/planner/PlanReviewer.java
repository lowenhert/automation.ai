package com.lowenhert.patchmanagement.core.ai.planner;

import com.lowenhert.patchmanagement.core.ai.planner.GeneratedTestPlan.Action;
import com.lowenhert.patchmanagement.core.ai.planner.GeneratedTestPlan.AssertionType;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Checks a validated plan against what is actually known about the device.
 * {@link #structuralErrors} are sent back to the model for repair; {@link #review} findings are kept as flags
 * for the human reviewer. Evidence is computed here from observed transitions, never taken from the model.
 */
public final class PlanReviewer {

    public enum Severity { ERROR, WARNING, INFO }

    public enum FlagType {
        NO_SCREEN_KNOWLEDGE, UNKNOWN_SCREEN, UNKNOWN_LOCATOR, UNSUPPORTED_ACTION, UNOBSERVED_TRANSITION,
        SEQUENCE_GAP, DESTRUCTIVE_STEP
    }

    /** OBSERVED: this exact source → action → destination was recorded during exploration. */
    public enum Evidence { OBSERVED, NOT_OBSERVED, UNKNOWN, NOT_APPLICABLE }

    public record Flag(FlagType type, Severity severity, Integer stepOrder, String message) {
    }

    public record Review(List<Flag> flags, Map<Integer, Evidence> stepEvidence) {
        public long count(Severity severity) {
            return flags.stream().filter(f -> f.severity() == severity).count();
        }
    }

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private PlanReviewer() {
    }

    // ── Structural rules (sent back to the model) ───────────────────────────

    public static List<String> structuralErrors(GeneratedTestPlan plan, int maxSteps) {
        List<String> errors = new ArrayList<>();
        List<GeneratedTestPlan.Step> steps = plan.steps();
        if (steps.size() > maxSteps) errors.add("plan has " + steps.size() + " steps; at most " + maxSteps + " allowed");

        for (int i = 0; i < steps.size(); i++) {
            GeneratedTestPlan.Step s = steps.get(i);
            String at = "steps[" + i + "]";
            if (s.order() != i + 1) errors.add(at + ".order must be " + (i + 1) + " (orders must be 1..n in sequence)");
            if (s.action() == Action.UNSUPPORTED && isBlank(s.requestedAction())) {
                errors.add(at + ": UNSUPPORTED steps must describe the needed capability in requestedAction");
            }
            if (s.action() == Action.ASSERT_TEXT_VISIBLE && isBlank(s.target())) {
                errors.add(at + ": ASSERT_TEXT_VISIBLE requires target text");
            }
            if (s.action() == Action.ASSERT_SCREEN && s.expectedScreenId() == null && isBlank(s.unknownScreenDescription())) {
                errors.add(at + ": ASSERT_SCREEN requires expectedScreenId or unknownScreenDescription");
            }
        }
        for (int i = 0; i < plan.assertions().size(); i++) {
            GeneratedTestPlan.Assertion a = plan.assertions().get(i);
            String at = "assertions[" + i + "]";
            if (a.afterStep() > steps.size()) errors.add(at + ".afterStep refers to a step that does not exist");
            if (a.type() != AssertionType.SCREEN_IS && isBlank(a.text())) errors.add(at + ": " + a.type() + " requires text");
            if (a.type() == AssertionType.SCREEN_IS && a.screenId() == null) {
                errors.add(at + ": SCREEN_IS requires screenId; use TEXT_VISIBLE for screens that are not known");
            }
        }
        if (containsControlCharacters(plan)) errors.add("text fields must not contain control characters");
        return errors;
    }

    // ── Grounding review (kept as flags) ────────────────────────────────────

    public static Review review(GeneratedTestPlan plan, PlannerKnowledge knowledge) {
        List<Flag> flags = new ArrayList<>();
        Map<Integer, Evidence> evidence = new LinkedHashMap<>();
        Map<Long, PlannerKnowledge.Screen> screens = knowledge.screensById();
        Set<String> observed = knowledge.transitionKeys();

        if (knowledge.isEmpty()) {
            flags.add(new Flag(FlagType.NO_SCREEN_KNOWLEDGE, Severity.WARNING, null,
                    "No explored screens exist for " + knowledge.deviceSerial() + "; every screen and locator is unverified"));
        }

        for (GeneratedTestPlan.Precondition p : plan.preconditions()) {
            if (p.screenId() != null && !screens.containsKey(p.screenId())) {
                flags.add(new Flag(FlagType.UNKNOWN_SCREEN, Severity.WARNING, null,
                        "Precondition refers to screen #" + p.screenId() + ", which has not been explored"));
            }
        }

        GeneratedTestPlan.Step previous = null;
        for (GeneratedTestPlan.Step s : plan.steps()) {
            int order = s.order();
            checkScreenRef(flags, screens, s.fromScreenId(), order, "source");
            checkScreenRef(flags, screens, s.expectedScreenId(), order, "expected");

            if (s.action() == Action.UNSUPPORTED) {
                flags.add(new Flag(FlagType.UNSUPPORTED_ACTION, Severity.ERROR, order,
                        "Needs an unsupported action: " + s.requestedAction()));
            }
            if (s.action().isNavigation() && s.expectedScreenId() == null) {
                flags.add(new Flag(FlagType.UNKNOWN_SCREEN, Severity.WARNING, order, "Destination screen is not known"
                        + (isBlank(s.unknownScreenDescription()) ? "" : ": " + s.unknownScreenDescription())));
            }
            if (s.action() == Action.SELECT && s.fromScreenId() != null && screens.containsKey(s.fromScreenId())
                    && "DESTRUCTIVE".equals(screens.get(s.fromScreenId()).riskLevel())) {
                flags.add(new Flag(FlagType.DESTRUCTIVE_STEP, Severity.ERROR, order,
                        "SELECT on screen '" + screens.get(s.fromScreenId()).label() + "', which is marked DESTRUCTIVE"));
            }
            if (previous != null && previous.expectedScreenId() != null && s.fromScreenId() != null
                    && !previous.expectedScreenId().equals(s.fromScreenId())) {
                flags.add(new Flag(FlagType.SEQUENCE_GAP, Severity.WARNING, order, "Starts on screen #" + s.fromScreenId()
                        + " but the previous step expects screen #" + previous.expectedScreenId()));
            }

            Long locatorScreen = switch (s.action()) {
                case ASSERT_TEXT_VISIBLE -> s.expectedScreenId() != null ? s.expectedScreenId() : s.fromScreenId();
                case SELECT, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT -> s.fromScreenId();
                default -> null;
            };
            boolean needsLocator = s.action() == Action.ASSERT_TEXT_VISIBLE || (s.action().isNavigation() && !isBlank(s.target()));
            if (needsLocator && !isBlank(s.target())) {
                checkLocator(flags, screens, locatorScreen, s.target(), order);
            }

            evidence.put(order, evidence(s, screens, observed));
            if (evidence.get(order) == Evidence.NOT_OBSERVED) {
                flags.add(new Flag(FlagType.UNOBSERVED_TRANSITION, Severity.WARNING, order,
                        screens.get(s.fromScreenId()).label() + " → " + s.action() + " → "
                                + screens.get(s.expectedScreenId()).label() + " has not been observed"));
            }
            previous = s;
        }

        for (GeneratedTestPlan.Assertion a : plan.assertions()) {
            checkScreenRef(flags, screens, a.screenId(), a.afterStep(), "asserted");
            if (a.type() == AssertionType.TEXT_VISIBLE && !isBlank(a.text())) {
                checkLocator(flags, screens, a.screenId(), a.text(), a.afterStep());
            }
        }
        return new Review(List.copyOf(flags), evidence);
    }

    private static Evidence evidence(GeneratedTestPlan.Step s, Map<Long, PlannerKnowledge.Screen> screens, Set<String> observed) {
        if (!s.action().isNavigation()) return Evidence.NOT_APPLICABLE;
        if (s.fromScreenId() == null || s.expectedScreenId() == null
                || !screens.containsKey(s.fromScreenId()) || !screens.containsKey(s.expectedScreenId())) {
            return Evidence.UNKNOWN;
        }
        return observed.contains(PlannerKnowledge.key(s.fromScreenId(), s.action().name(), s.expectedScreenId()))
                ? Evidence.OBSERVED : Evidence.NOT_OBSERVED;
    }

    private static void checkScreenRef(List<Flag> flags, Map<Long, PlannerKnowledge.Screen> screens, Long id, Integer order,
                                       String role) {
        if (id != null && !screens.containsKey(id)) {
            flags.add(new Flag(FlagType.UNKNOWN_SCREEN, Severity.WARNING, order,
                    "Refers to " + role + " screen #" + id + ", which is not among the explored screens"));
        }
    }

    private static void checkLocator(List<Flag> flags, Map<Long, PlannerKnowledge.Screen> screens, Long screenId,
                                     String text, Integer order) {
        PlannerKnowledge.Screen screen = screenId == null ? null : screens.get(screenId);
        if (screen == null) {
            flags.add(new Flag(FlagType.UNKNOWN_LOCATOR, Severity.WARNING, order,
                    "Cannot verify text '" + text + "': its screen is not known"));
        } else if (!textKnown(screen.texts(), text)) {
            flags.add(new Flag(FlagType.UNKNOWN_LOCATOR, Severity.WARNING, order,
                    "Text '" + text + "' was not seen by OCR on screen '" + screen.label() + "'"));
        }
    }

    /** Normalised match against OCR lines: equal, or one contains the other (ignoring punctuation and case). */
    static boolean textKnown(List<String> knownTexts, String target) {
        String t = normalize(target);
        if (t.isEmpty()) return false;
        for (String known : knownTexts) {
            String k = normalize(known);
            if (k.isEmpty()) continue;
            if (k.equals(t) || (t.length() >= 3 && k.contains(t)) || (k.length() >= 4 && t.contains(k))) return true;
        }
        return false;
    }

    static String normalize(String s) {
        return s == null ? "" : NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    private static boolean containsControlCharacters(GeneratedTestPlan plan) {
        List<String> texts = new ArrayList<>(List.of(nz(plan.title()), nz(plan.summary()), nz(plan.failureHandling().notes())));
        plan.assumptions().forEach(a -> texts.add(nz(a)));
        plan.preconditions().forEach(p -> texts.add(nz(p.description())));
        plan.steps().forEach(s -> texts.addAll(List.of(nz(s.target()), nz(s.expectedState()), nz(s.requestedAction()),
                nz(s.unknownScreenDescription()))));
        plan.assertions().forEach(a -> texts.addAll(List.of(nz(a.text()), nz(a.description()))));
        return texts.stream().anyMatch(t -> CONTROL.matcher(t).find());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
