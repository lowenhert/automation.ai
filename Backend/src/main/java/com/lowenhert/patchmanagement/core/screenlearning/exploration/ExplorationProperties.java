package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Hard limits for exploration. Requests may ask for less, never more. */
@Getter
@Component
public class ExplorationProperties {

    private final int maxStepsLimit;
    private final int defaultMaxSteps;
    private final int maxDurationLimitSeconds;
    private final int defaultMaxDurationSeconds;
    private final int defaultSettleMs;
    private final int minSettleMs;
    private final int stallLimit;
    private final int maxConsecutiveFailures;
    private final int maxInterpretationsPerSession;
    private final int maxRecoveryBacks;

    public ExplorationProperties(@Value("${app.exploration.max-steps-limit:200}") int maxStepsLimit,
                                 @Value("${app.exploration.default-max-steps:30}") int defaultMaxSteps,
                                 @Value("${app.exploration.max-duration-limit-seconds:1800}") int maxDurationLimitSeconds,
                                 @Value("${app.exploration.default-max-duration-seconds:600}") int defaultMaxDurationSeconds,
                                 @Value("${app.exploration.default-settle-ms:1500}") int defaultSettleMs,
                                 @Value("${app.exploration.min-settle-ms:300}") int minSettleMs,
                                 @Value("${app.exploration.stall-limit:10}") int stallLimit,
                                 @Value("${app.exploration.max-consecutive-failures:3}") int maxConsecutiveFailures,
                                 @Value("${app.exploration.interpretation.max-per-session:20}") int maxInterpretationsPerSession,
                                 @Value("${app.exploration.max-recovery-backs:3}") int maxRecoveryBacks) {
        if (maxStepsLimit < 1 || maxStepsLimit > 1000) throw new IllegalStateException("app.exploration.max-steps-limit must be 1-1000");
        if (maxDurationLimitSeconds < 10 || maxDurationLimitSeconds > 7200) {
            throw new IllegalStateException("app.exploration.max-duration-limit-seconds must be 10-7200");
        }
        this.maxStepsLimit = maxStepsLimit;
        this.defaultMaxSteps = Math.min(defaultMaxSteps, maxStepsLimit);
        this.maxDurationLimitSeconds = maxDurationLimitSeconds;
        this.defaultMaxDurationSeconds = Math.min(defaultMaxDurationSeconds, maxDurationLimitSeconds);
        this.defaultSettleMs = defaultSettleMs;
        this.minSettleMs = Math.max(0, Math.min(minSettleMs, defaultSettleMs));
        this.stallLimit = Math.max(1, stallLimit);
        this.maxConsecutiveFailures = Math.max(1, maxConsecutiveFailures);
        this.maxInterpretationsPerSession = Math.max(0, maxInterpretationsPerSession);
        this.maxRecoveryBacks = Math.max(0, maxRecoveryBacks);
    }
}
