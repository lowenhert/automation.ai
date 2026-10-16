package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Comparable identity of a screen: perceptual hash plus normalized OCR tokens. */
public record ScreenFingerprint(String phash, Set<String> tokens) {

    /** Top band ignored for text identity: status bar (clock, battery, notifications) changes constantly. */
    public static final double STATUS_BAR_FRACTION = 0.06;
    static final int MAX_SIGNATURE_CHARS = 4000;

    // Matching thresholds (64-bit perceptual hash; Jaccard similarity of OCR tokens)
    static final int MIN_TOKENS_FOR_TEXT_MATCH = 3;
    static final double TEXT_SAME = 0.6;
    static final double TEXT_SAME_STRONG = 0.85;
    static final int PHASH_SANITY_MAX = 22;
    static final int PHASH_SAME_SPARSE_TEXT = 8;

    public static ScreenFingerprint of(CapturedScreen screen) {
        int statusBarLimit = (int) Math.round(screen.getHeight() * STATUS_BAR_FRACTION);
        Set<String> tokens = tokenize(screen.getTextRegions().stream()
                .filter(r -> r.getY() >= statusBarLimit)
                .map(ScreenTextRegion::getText)
                .toList());
        return new ScreenFingerprint(screen.getPhash(), tokens);
    }

    public static ScreenFingerprint of(ScreenNode node) {
        return new ScreenFingerprint(node.getPhash(), parseSignature(node.getTextSignature()));
    }

    public static Set<String> tokenize(Collection<String> texts) {
        Set<String> tokens = new TreeSet<>();
        for (String text : texts) {
            if (text == null) continue;
            for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                // Skip 1-char noise and pure numbers (clocks, counters, percentages)
                if (raw.length() >= 2 && !raw.chars().allMatch(Character::isDigit)) tokens.add(raw);
            }
        }
        return tokens;
    }

    public String signature() {
        String joined = String.join(" ", tokens);
        return joined.length() <= MAX_SIGNATURE_CHARS ? joined : joined.substring(0, MAX_SIGNATURE_CHARS);
    }

    static Set<String> parseSignature(String signature) {
        if (signature == null || signature.isBlank()) return Set.of();
        return Arrays.stream(signature.split(" ")).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Hamming distance between two 64-bit hex hashes, or -1 when either is missing/invalid. */
    static int phashDistance(String a, String b) {
        if (a == null || b == null || a.length() != 16 || b.length() != 16) return -1;
        try {
            return Long.bitCount(Long.parseUnsignedLong(a, 16) ^ Long.parseUnsignedLong(b, 16));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return (double) common / (a.size() + b.size() - common);
    }

    /**
     * Returns a match score (lower is better) or -1 when the screens are considered different.
     * Text-rich screens are matched mainly on OCR text, so D-pad focus highlights don't split a screen;
     * text-poor screens (video, images) fall back to a strict perceptual-hash match.
     */
    public double matchScore(ScreenFingerprint other) {
        int distance = phashDistance(phash, other.phash);
        boolean textRich = tokens.size() >= MIN_TOKENS_FOR_TEXT_MATCH && other.tokens.size() >= MIN_TOKENS_FOR_TEXT_MATCH;
        if (textRich) {
            double similarity = jaccard(tokens, other.tokens);
            boolean phashPlausible = distance < 0 || distance <= PHASH_SANITY_MAX;
            boolean same = similarity >= TEXT_SAME_STRONG || (similarity >= TEXT_SAME && phashPlausible);
            return same ? (1 - similarity) + Math.max(distance, 0) / 64.0 : -1;
        }
        if (distance >= 0 && distance <= PHASH_SAME_SPARSE_TEXT) {
            // Sparse text must still not contradict
            if (!tokens.isEmpty() && !other.tokens.isEmpty() && jaccard(tokens, other.tokens) == 0) return -1;
            return 1 + distance / 64.0;
        }
        return -1;
    }
}
