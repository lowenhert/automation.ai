package com.lowenhert.patchmanagement.core.testlibrary;

import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end on a real device: events → generated script → validate → approve → execute (real adb, OCR, pytest and
 * Robot Framework). Opt-in: EXECUTION_LIVE_TEST=true, device from EXECUTION_DEVICE_ID (default emulator-5554).
 */
@EnabledIfEnvironmentVariable(named = "EXECUTION_LIVE_TEST", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testlib-live;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.test-engine.executions-dir=target/live-executions"
})
@ActiveProfiles("h2")
class ScriptExecutionLiveTest {

    @Autowired private TestCaseService cases;
    @Autowired private TestScriptService scripts;
    @Autowired private ScriptExecutionService executions;

    private final String device = System.getenv().getOrDefault("EXECUTION_DEVICE_ID", "emulator-5554");

    @Test
    void generatedScriptsRunOnDevice() throws Exception {
        TestCaseDetail testCase = cases.create(new TestCaseRequest("Live home screen", "Live execution check", device), "live");
        cases.replaceEvents(testCase.id(), List.of(
                new EventRequest(TestCaseEvent.Action.HOME, null, "Launcher", null),
                new EventRequest(TestCaseEvent.Action.WAIT, null, null, 2000),
                new EventRequest(TestCaseEvent.Action.SCREENSHOT, "home", null, null),
                new EventRequest(TestCaseEvent.Action.ASSERT_TEXT_NOT_VISIBLE, "zzqx-not-on-screen", null, 3000)));

        for (TestScript.Language language : TestScript.Language.values()) {
            ScriptDetail generated = cases.generateScript(testCase.id(), new GenerateRequest(language, null), "live");
            ScriptDetail validated = scripts.validate(generated.id());
            assertThat(validated.validation().errors()).isEmpty();
            ScriptDetail approved = scripts.approve(generated.id(), new ApproveRequest(generated.contentHash()), "live");

            ExecutionDto started = executions.start(new ExecuteRequest(approved.id(), device, approved.contentHash(), 300, true), "live");
            ExecutionDto done = started;
            long end = System.currentTimeMillis() + 240_000;
            while (done.status() == ScriptExecution.Status.RUNNING && System.currentTimeMillis() < end) {
                Thread.sleep(1000);
                done = executions.get(started.id());
            }
            String log = executions.log(started.id(), 0).text();
            System.out.println("=== " + language + " → " + done.status() + " " + done.errorMessage() + "\n" + log);
            assertThat(done.status()).as(log).isEqualTo(ScriptExecution.Status.PASSED);
            assertThat(done.testsPassed()).isEqualTo(1);
            assertThat(done.screenshots()).anyMatch(s -> s.endsWith("_home.png"));
            byte[] png = executions.artifact(started.id(), done.screenshots().get(0));
            assertThat(new String(png, 1, 3, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PNG");
        }
        cases.delete(testCase.id(), true);
    }
}
