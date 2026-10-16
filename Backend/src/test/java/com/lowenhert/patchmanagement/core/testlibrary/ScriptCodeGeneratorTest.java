package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScriptCodeGeneratorTest {

    private static TestCaseEvent ev(TestCaseEvent.Action action, String target, int timeoutMs) {
        return TestCaseEvent.builder().action(action).target(target).timeoutMs(timeoutMs).build();
    }

    private static final String HOSTILE = "x\"); __import__('os').system('calc') #\\ ${EVIL} @{L} &{D} %{ENV} = \n next";

    private static List<TestCaseEvent> events() {
        return List.of(
                ev(TestCaseEvent.Action.HOME, null, 10_000),
                ev(TestCaseEvent.Action.DPAD_DOWN, null, 10_000),
                ev(TestCaseEvent.Action.WAIT, null, 2_000),
                ev(TestCaseEvent.Action.ASSERT_TEXT_VISIBLE, HOSTILE, 15_000),
                ev(TestCaseEvent.Action.ASSERT_TEXT_NOT_VISIBLE, "Error", 5_000),
                ev(TestCaseEvent.Action.ASSERT_ANY_TEXT_VISIBLE, "Settings | Network & internet", 10_000),
                ev(TestCaseEvent.Action.SCREENSHOT, "final", 10_000));
    }

    @Test
    void pythonStringsAreEscaped() {
        assertThat(ScriptCodeGenerator.py("a\"b\\c\nd\u0001")).isEqualTo("\"a\\\"b\\\\c\\nd\\x01\"");
        assertThat(ScriptCodeGenerator.identifier("Open Wi-Fi settings!")).isEqualTo("open_wi_fi_settings");
        assertThat(ScriptCodeGenerator.identifier("???")).isEqualTo("case");
    }

    @Test
    void robotArgumentsAreEscaped() {
        assertThat(ScriptCodeGenerator.rb("${x} @{y} &{z} %{E} # a=b \\")).isEqualTo("\\${x} \\@{y} \\&{z} \\%{E} \\# a\\=b \\\\");
        assertThat(ScriptCodeGenerator.rb("two  spaces\tand\ttabs")).isEqualTo("two spaces and tabs");
        assertThat(ScriptCodeGenerator.rb("  ")).isEqualTo("${EMPTY}");
        assertThat(ScriptCodeGenerator.robotName("Test\n*** Settings ***")).isEqualTo("Test Settings");
    }

    @Test
    void generatesPytestFromEvents() {
        String code = ScriptCodeGenerator.generate(TestScript.Language.PYTEST, "Open settings", "Goal \"\"\" x", events());
        assertThat(code).contains("def test_open_settings(device: Device):")
                .contains("device.press(\"HOME\")")
                .contains("device.sleep(2.0)")
                .contains("device.assert_text_not_visible(\"Error\", timeout=5.0)")
                .contains("device.assert_any_text_visible([\"Settings\", \"Network & internet\"], timeout=10.0)")
                .contains("device.screenshot(\"final\")")
                .doesNotContain("\"\"\" x");
        // The hostile text stays inside one string literal on one line
        assertThat(code.lines().filter(l -> l.contains("__import__")).toList())
                .allMatch(l -> l.startsWith("    device.assert_text_visible(\"x\\\")") || l.startsWith("    #"));
    }

    @Test
    void generatesRobotFromEvents() {
        String code = ScriptCodeGenerator.generate(TestScript.Language.ROBOT, "Open settings", null, events());
        assertThat(code).contains("Library    AutomationAiLibrary")
                .contains("    Press Key    HOME")
                .contains("    Any Text Should Be Visible    Settings    Network \\& internet    timeout=10.0")
                .contains("\\${EVIL}").doesNotContain(" ${EVIL}");
    }

    /** Generated code must pass the real static validator (skipped if Python or the test engine is missing). */
    @Test
    void generatedCodePassesValidator() {
        TestEngineSettings settings = new TestEngineSettings("python", "../test-engine", "../ai-engine/capture/capture.py",
                "target/test-executions", "adb", "", 30_000, 600, 1800, 5_242_880);
        assumeTrue(Files.isRegularFile(settings.getValidatorScript()), "test-engine not found");
        ScriptValidator validator = new ScriptValidator(new ProcessRunner(), settings, new ObjectMapper());
        TestLibraryDtos.Validation py;
        try {
            py = validator.validate(TestScript.Language.PYTEST,
                    ScriptCodeGenerator.generate(TestScript.Language.PYTEST, "Open settings", "d", events()));
        } catch (RuntimeException e) {
            assumeTrue(false, "Python not available: " + e.getMessage());
            return;
        }
        assertThat(py.errors()).isEmpty();
        assertThat(py.valid()).isTrue();
        assertThat(py.tests()).containsExactly("test_open_settings");
        assertThat(py.actions()).hasSizeGreaterThanOrEqualTo(7);

        TestLibraryDtos.Validation robot = validator.validate(TestScript.Language.ROBOT,
                ScriptCodeGenerator.generate(TestScript.Language.ROBOT, "Open settings", "d", events()));
        assertThat(robot.errors()).isEmpty();
        assertThat(robot.valid()).isTrue();
        assertThat(robot.tests()).containsExactly("Open settings");
    }
}
