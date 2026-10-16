package com.lowenhert.patchmanagement.core.ai.testplan;

import java.util.List;

/** An LLM-proposed UI test plan. Drafts are suggestions for review; they are never executed automatically. */
public record TestPlanDraft(String title, List<Step> steps) {

    public enum Action { TAP, INPUT_TEXT, PRESS_KEY, WAIT, ASSERT_TEXT_VISIBLE }

    public record Step(Action action, String target, String expected) {
    }
}
