package com.lowenhert.patchmanagement.core.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class OllamaPropertiesTest {

    private static OllamaProperties props(String url, String model) {
        return new OllamaProperties(url, model, 5000, 120000, "", 2);
    }

    @Test
    void defaultsAreAccepted() {
        OllamaProperties p = props("http://localhost:11434/", "gemma4:31b-cloud");
        assertEquals("http://localhost:11434", p.getBaseUrl().toString());
        assertEquals("gemma4:31b-cloud", p.getModel());
        assertFalse(p.hasApiKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost:11434", "ftp://host", "not a url", ""})
    void invalidBaseUrlIsRejected(String url) {
        assertThrows(IllegalStateException.class, () -> props(url, "gemma4:31b-cloud"));
    }

    @Test
    void credentialsInBaseUrlAreRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> props("https://user:secret@ollama.com", "gemma4:31b-cloud"));
        assertTrue(ex.getMessage().contains("OLLAMA_API_KEY"));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test
    void blankModelAndBadTimeoutsAreRejected() {
        assertThrows(IllegalStateException.class, () -> props("http://localhost:11434", " "));
        assertThrows(IllegalStateException.class, () -> new OllamaProperties("http://localhost:11434", "m", 0, 1000, "", 2));
        assertThrows(IllegalStateException.class, () -> new OllamaProperties("http://localhost:11434", "m", 1000, 1000, "", 0));
    }
}
