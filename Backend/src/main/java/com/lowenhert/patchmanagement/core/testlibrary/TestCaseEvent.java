package com.lowenhert.patchmanagement.core.testlibrary;

import jakarta.persistence.*;
import lombok.*;

/** One step of a test case: a remote key, a wait, a screenshot or an OCR assertion. */
@Entity
@Table(name = "test_case_events", indexes = @Index(name = "idx_test_case_events_case", columnList = "test_case_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseEvent {

    public enum Action {
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, SELECT, BACK, HOME,
        WAIT, SCREENSHOT, ASSERT_TEXT_VISIBLE, ASSERT_TEXT_NOT_VISIBLE, ASSERT_ANY_TEXT_VISIBLE;

        public boolean isKey() {
            return ordinal() <= HOME.ordinal();
        }

        public boolean needsTarget() {
            return this == ASSERT_TEXT_VISIBLE || this == ASSERT_TEXT_NOT_VISIBLE || this == ASSERT_ANY_TEXT_VISIBLE;
        }
    }

    /** Separator for ASSERT_ANY_TEXT_VISIBLE alternatives in {@link #target}. */
    public static final String ANY_SEPARATOR = " | ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_case_id", nullable = false, foreignKey = @ForeignKey(name = "fk_test_case_events_case"))
    private TestCase testCase;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private Action action;

    @Column(name = "target", length = 400)
    private String target;

    @Column(name = "expected_state", length = 300)
    private String expectedState;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs;
}
