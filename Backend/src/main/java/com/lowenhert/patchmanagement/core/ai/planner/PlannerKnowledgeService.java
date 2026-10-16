package com.lowenhert.patchmanagement.core.ai.planner;

import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbInputValidator;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenFingerprint;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNode;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNodeRepository;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransition;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransitionRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Retrieves known screens and transitions for a device, ranked by relevance to the goal and bounded in size. */
@Service
@RequiredArgsConstructor
public class PlannerKnowledgeService {

    static final int MAX_SCREENS = 60;
    static final int MAX_TEXTS_PER_SCREEN = 25;
    static final int MAX_TEXT_LENGTH = 80;
    static final int MAX_TRANSITIONS = 300;

    private final ScreenNodeRepository nodeRepository;
    private final ScreenTransitionRepository transitionRepository;
    private final CapturedScreenRepository capturedScreenRepository;

    @Transactional(readOnly = true)
    public PlannerKnowledge retrieve(String deviceId, String goal, Long startScreenId) {
        String serial = AdbInputValidator.requireSerial(deviceId);
        List<ScreenNode> nodes = nodeRepository.findByDeviceSerial(serial);
        List<ScreenTransition> edges = transitionRepository.findGraphEdges(serial);

        Set<Long> selected = select(nodes, edges, goal, startScreenId);
        List<PlannerKnowledge.Screen> screens = nodes.stream()
                .filter(n -> selected.contains(n.getId()))
                .sorted(Comparator.comparing(ScreenNode::getId))
                .map(n -> new PlannerKnowledge.Screen(n.getId(), n.getLabel(), n.getScreenType(),
                        n.getRiskLevel().name(), texts(n)))
                .toList();
        List<PlannerKnowledge.Transition> transitions = edges.stream()
                .filter(e -> selected.contains(e.getFromNode().getId()) && selected.contains(e.getToNode().getId()))
                .sorted(Comparator.comparing((ScreenTransition e) -> e.getObservationCount()).reversed())
                .limit(MAX_TRANSITIONS)
                .map(e -> new PlannerKnowledge.Transition(e.getFromNode().getId(), e.getAction(), e.getToNode().getId(),
                        e.getObservationCount()))
                .toList();
        return new PlannerKnowledge(serial, screens, transitions, nodes.size(), edges.size());
    }

    /** All screens if few; otherwise the start screen and its neighbours plus the screens most related to the goal. */
    static Set<Long> select(List<ScreenNode> nodes, List<ScreenTransition> edges, String goal, Long startScreenId) {
        if (nodes.size() <= MAX_SCREENS) return nodes.stream().map(ScreenNode::getId).collect(Collectors.toSet());

        Set<Long> selected = new LinkedHashSet<>();
        if (startScreenId != null) {
            selected.add(startScreenId);
            edges.stream().filter(e -> e.getFromNode().getId().equals(startScreenId))
                    .forEach(e -> selected.add(e.getToNode().getId()));
        }
        Set<String> goalTokens = ScreenFingerprint.tokenize(List.of(goal == null ? "" : goal));
        nodes.stream()
                .sorted(Comparator.comparingDouble((ScreenNode n) -> -relevance(n, goalTokens))
                        .thenComparing(n -> -n.getVisitCount()))
                .map(ScreenNode::getId)
                .forEach(id -> {
                    if (selected.size() < MAX_SCREENS) selected.add(id);
                });
        return selected;
    }

    private static double relevance(ScreenNode node, Set<String> goalTokens) {
        if (goalTokens.isEmpty()) return 0;
        Set<String> nodeTokens = new HashSet<>(ScreenFingerprint.tokenize(List.of(node.getLabel())));
        if (node.getTextSignature() != null) nodeTokens.addAll(Arrays.asList(node.getTextSignature().split(" ")));
        long overlap = goalTokens.stream().filter(nodeTokens::contains).count();
        return (double) overlap / goalTokens.size();
    }

    /** Visible OCR lines of a known screen (empty if the screen or its capture is unknown). */
    @Transactional(readOnly = true)
    public List<String> screenTexts(Long screenId) {
        if (screenId == null) return List.of();
        return nodeRepository.findById(screenId).map(this::texts).orElse(List.of());
    }

    /** Visible OCR lines of the screen's representative capture (status bar excluded). */
    private List<String> texts(ScreenNode node) {
        Optional<CapturedScreen> capture = capturedScreenRepository.findWithTextRegionsByCaptureId(node.getRepresentativeCaptureId());
        if (capture.isEmpty()) return List.of();
        CapturedScreen screen = capture.get();
        int statusBar = (int) Math.round(screen.getHeight() * ScreenFingerprint.STATUS_BAR_FRACTION);
        return screen.getTextRegions().stream()
                .filter(r -> r.getY() >= statusBar)
                .map(ScreenTextRegion::getText)
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .map(t -> t.length() > MAX_TEXT_LENGTH ? t.substring(0, MAX_TEXT_LENGTH) : t)
                .distinct()
                .limit(MAX_TEXTS_PER_SCREEN)
                .toList();
    }
}
