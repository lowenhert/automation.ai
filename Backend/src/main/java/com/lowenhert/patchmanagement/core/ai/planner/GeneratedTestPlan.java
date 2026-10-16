package com.lowenhert.patchmanagement.core.ai.planner;

import java.util.List;

/** Typed form of a validated LLM test plan (see resources/ai/test-plan.schema.json). A plan, never a result. */
public record GeneratedTestPlan(String title, String summary, List<Precondition> preconditions, List<Step> steps,
                                List<Assertion> assertions, FailureHandling failureHandling, List<String> assumptions) {

    public enum Action {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, SELECT, BACK, HOME, WAIT, ASSERT_SCREEN, ASSERT_TEXT_VISIBLE, UNSUPPORTED;

        public boolean isNavigation() {
            return switch (this) {
                case DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, SELECT, BACK, HOME -> true;
                default -> false;
            };
        }
    }

    public enum OnFailure { ABORT, RETRY, SKIP, RECOVER_BACK, RECOVER_HOME }

    public enum AssertionType { SCREEN_IS, TEXT_VISIBLE, TEXT_NOT_VISIBLE }

    public enum Recovery { NONE, BACK, HOME }

    public record Precondition(String description, Long screenId) {
    }

    public record Step(int order, Action action, String requestedAction, Long fromScreenId, Long expectedScreenId,
                       String unknownScreenDescription, String target, String expectedState, int timeoutMs,
                       OnFailure onFailure) {
    }

    public record Assertion(int afterStep, AssertionType type, Long screenId, String text, String description) {
    }

    public record FailureHandling(int maxRetries, Recovery recovery, String notes) {
    }
}
