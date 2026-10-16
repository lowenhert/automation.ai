package com.lowenhert.patchmanagement.core.ai.testplan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Application-level rules for LLM-generated test plans (the JSON schema alone is not trusted). */
public final class TestPlanValidator {

    static final int MAX_TITLE = 200;
    static final int MAX_STEPS = 30;
    static final int MAX_TARGET = 200;
    static final int MAX_EXPECTED = 500;
    private static final Pattern KEY_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");

    private TestPlanValidator() {
    }

    public static List<String> validate(TestPlanDraft plan) {
        List<String> errors = new ArrayList<>();
        requireText(errors, "title", plan.title(), MAX_TITLE);

        List<TestPlanDraft.Step> steps = plan.steps();
        if (steps == null || steps.isEmpty()) {
            errors.add("steps must contain at least one step");
            return errors;
        }
        if (steps.size() > MAX_STEPS) {
            errors.add("steps must contain at most " + MAX_STEPS + " steps");
        }
        for (int i = 0; i < steps.size(); i++) {
            TestPlanDraft.Step step = steps.get(i);
            String prefix = "steps[" + i + "]";
            if (step == null) {
                errors.add(prefix + " must not be null");
                continue;
            }
            if (step.action() == null) errors.add(prefix + ".action is required");
            requireText(errors, prefix + ".target", step.target(), MAX_TARGET);
            requireText(errors, prefix + ".expected", step.expected(), MAX_EXPECTED);
            if (step.action() == TestPlanDraft.Action.PRESS_KEY && step.target() != null
                    && !KEY_NAME.matcher(step.target().strip()).matches()) {
                errors.add(prefix + ".target must be an uppercase key name such as HOME or DPAD_DOWN for PRESS_KEY");
            }
        }
        return errors;
    }

    private static void requireText(List<String> errors, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
        } else if (value.length() > max) {
            errors.add(field + " must be at most " + max + " characters");
        } else if (CONTROL_CHARS.matcher(value).find()) {
            errors.add(field + " contains control characters");
        }
    }
}
