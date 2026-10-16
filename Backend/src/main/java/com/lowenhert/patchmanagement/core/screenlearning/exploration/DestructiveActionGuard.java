package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Blocks SELECT on screens that look destructive (reset, delete, purchase, ...).
 * Deliberately conservative: a keyword anywhere on the screen blocks SELECT for that screen.
 */
@Slf4j
@Component
public class DestructiveActionGuard {

    static final String DEFAULT_KEYWORDS = "factory reset,reset,erase,wipe,delete,remove,uninstall,format,clear data,"
            + "clear storage,clear cache,purchase,buy,rent,pay,subscribe,sign out,log out,logout,deactivate,"
            + "disable,restore,reboot,restart,power off,shut down,shutdown,install,update now,confirm";

    @Getter
    private final List<String> keywords;
    private final List<Pattern> patterns;

    public DestructiveActionGuard(@Value("${app.exploration.blocked-keywords:" + DEFAULT_KEYWORDS + "}") String keywords) {
        this.keywords = Arrays.stream(keywords.split(","))
                .map(k -> k.strip().toLowerCase(Locale.ROOT))
                .filter(k -> !k.isEmpty())
                .toList();
        this.patterns = this.keywords.stream()
                .map(k -> Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}])"))
                .toList();
        log.info("Exploration SELECT guard keywords: {}", this.keywords);
    }

    /** Assessment used when a node is first created (from OCR only). */
    public ScreenNode.RiskLevel assess(String screenText) {
        return findKeyword(screenText).isPresent() ? ScreenNode.RiskLevel.DESTRUCTIVE : ScreenNode.RiskLevel.SAFE;
    }

    /** Reason SELECT must not be sent on this screen, or empty if allowed. */
    public Optional<String> selectBlockReason(ScreenNode node, CapturedScreen currentScreen) {
        if (node != null && node.getRiskLevel() == ScreenNode.RiskLevel.DESTRUCTIVE) {
            return Optional.of("Screen '" + node.getLabel() + "' is marked DESTRUCTIVE");
        }
        String text = currentScreen != null ? currentScreen.getExtractedText() : null;
        return findKeyword(text).map(k -> "Screen contains potentially destructive text '" + k + "'");
    }

    Optional<String> findKeyword(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matcher(lower).find()) return Optional.of(keywords.get(i));
        }
        return Optional.empty();
    }
}
