package com.lowenhert.patchmanagement.core.ai.planner;

import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What is actually known about a device: observed screens (with their OCR text) and observed transitions.
 * The planner prompt and the plan review both use this same snapshot.
 */
public record PlannerKnowledge(String deviceSerial, List<Screen> screens, List<Transition> transitions, int totalScreens,
                               int totalTransitions) {

    public record Screen(long id, String label, String screenType, String riskLevel, List<String> texts) {
    }

    public record Transition(long from, RemoteKey action, long to, int count) {
    }

    public Map<Long, Screen> screensById() {
        return screens.stream().collect(java.util.stream.Collectors.toMap(Screen::id, s -> s));
    }

    public Set<String> transitionKeys() {
        return transitions.stream().map(t -> key(t.from(), t.action().name(), t.to()))
                .collect(java.util.stream.Collectors.toSet());
    }

    public static String key(long from, String action, long to) {
        return from + "|" + action + "|" + to;
    }

    public boolean isEmpty() {
        return screens.isEmpty();
    }
}
