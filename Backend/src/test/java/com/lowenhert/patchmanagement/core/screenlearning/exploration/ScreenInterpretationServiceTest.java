package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.AiProviderException;
import com.lowenhert.patchmanagement.core.ai.ModelCapabilities;
import com.lowenhert.patchmanagement.core.ai.OllamaProperties;
import com.lowenhert.patchmanagement.core.ai.StructuredChatService;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScreenInterpretationServiceTest {

    @TempDir
    Path dir;

    private StructuredChatService chat;
    private ModelCapabilities capabilities;
    private ScreenInterpretationService service;
    private Path image;
    private CapturedScreen screen;

    @BeforeEach
    void setUp() throws Exception {
        chat = mock(StructuredChatService.class);
        capabilities = mock(ModelCapabilities.class);
        service = new ScreenInterpretationService(chat, capabilities,
                new OllamaProperties("http://localhost:11434", "gemma4:31b-cloud", 1000, 1000, "", 1), new ObjectMapper(), true);
        image = dir.resolve("shot.png");
        ImageIO.write(new BufferedImage(1080, 2340, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
        screen = ScreenFingerprintTest.screen("c5ffd59b90a02ae0", "Settings", "Network & internet", "Connected devices", "Apps");
    }

    private static ScreenInterpretationService.Interpretation answer(List<String> visible) {
        return new ScreenInterpretationService.Interpretation("Settings", "SETTINGS", "Main settings menu", "SAFE", visible);
    }

    @Test
    void verifiedImageInputWhenModelReadsTextFromTheImage() {
        when(capabilities.supportsVision()).thenReturn(true);
        when(chat.chatJson(anyString(), anyString(), anyList(), any(), eq(ScreenInterpretationService.Interpretation.class), any()))
                .thenReturn(answer(List.of("Network & internet", "Connected devices", "Apps")));

        ScreenInterpretationService.Result result = service.interpret(screen, image);

        assertEquals(ScreenNode.InterpretationInput.IMAGE, result.input());
        assertEquals("gemma4:31b-cloud", result.model());
        verify(chat).chatJson(contains("screenshot image"), contains("Network & internet"),
                argThat(images -> images.size() == 1 && Base64.getDecoder().decode(images.get(0)).length > 0),
                any(), any(), any());
    }

    @Test
    void imageSentButUnrelatedAnswerIsMarkedUnverified() {
        when(capabilities.supportsVision()).thenReturn(true);
        when(chat.chatJson(anyString(), anyString(), anyList(), any(), any(), any()))
                .thenReturn(answer(List.of("Welcome", "Continue")));
        assertEquals(ScreenNode.InterpretationInput.IMAGE_UNVERIFIED, service.interpret(screen, image).input());
    }

    @Test
    void noVisionCapabilityUsesOcrTextOnlyAndSaysSo() {
        when(capabilities.supportsVision()).thenReturn(false);
        when(chat.chatJson(anyString(), anyString(), any(), any(), any())).thenReturn(answer(List.of()));

        ScreenInterpretationService.Result result = service.interpret(screen, image);

        assertEquals(ScreenNode.InterpretationInput.TEXT, result.input());
        verify(chat).chatJson(contains("NOT given an image"), anyString(), any(), any(), any());
        verify(chat, never()).chatJson(anyString(), anyString(), anyList(), any(), any(), any());
    }

    @Test
    void failedImageRequestFallsBackToText() {
        when(capabilities.supportsVision()).thenReturn(true);
        when(chat.chatJson(anyString(), anyString(), anyList(), any(), any(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Reason.PROVIDER_ERROR, "Ollama returned HTTP 400: image input not supported"));
        when(chat.chatJson(anyString(), anyString(), any(), any(), any())).thenReturn(answer(List.of()));

        assertEquals(ScreenNode.InterpretationInput.TEXT, service.interpret(screen, image).input());
    }

    @Test
    void ollamaDownReturnsFailureWithoutThrowing() {
        when(capabilities.supportsVision()).thenReturn(true);
        when(chat.chatJson(anyString(), anyString(), anyList(), any(), any(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Reason.CONNECTION_FAILED, "Cannot connect"));

        ScreenInterpretationService.Result result = service.interpret(screen, image);

        assertNull(result.interpretation());
        assertEquals(ScreenNode.InterpretationInput.NONE, result.input());
        assertTrue(result.error().contains("Cannot connect"));
    }

    @Test
    void verificationNeedsEnoughOcrText() {
        CapturedScreen blank = ScreenFingerprintTest.screen("c5ffd59b90a02ae0");
        assertFalse(ScreenInterpretationService.imageVerified(List.of("anything", "at all"), blank));
        assertFalse(ScreenInterpretationService.imageVerified(List.of("Apps"), screen), "one token is not enough evidence");
        assertTrue(ScreenInterpretationService.imageVerified(List.of("Network & Internet"), screen));
    }

    @Test
    void validatorRejectsUnexpectedValues() {
        assertTrue(ScreenInterpretationService.validate(answer(List.of())).isEmpty());
        assertFalse(ScreenInterpretationService.validate(new ScreenInterpretationService.Interpretation(
                "", "SETTINGS", "", "SAFE", List.of())).isEmpty());
        assertFalse(ScreenInterpretationService.validate(new ScreenInterpretationService.Interpretation(
                "x", "WEBPAGE", "", "SAFE", List.of())).isEmpty());
        assertFalse(ScreenInterpretationService.validate(new ScreenInterpretationService.Interpretation(
                "x", "MENU", "", "HARMLESS", List.of())).isEmpty());
    }

    @Test
    void imageIsDownscaledForTheModel() throws Exception {
        byte[] jpeg = Base64.getDecoder().decode(ScreenInterpretationService.encodeForModel(image));
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(jpeg));
        assertEquals(ScreenInterpretationService.MAX_IMAGE_SIDE, Math.max(decoded.getWidth(), decoded.getHeight()));
    }

    @Test
    void ocrContextIsMarkedAsData() {
        String context = ScreenInterpretationService.describe(screen);
        assertTrue(context.contains("<<<") && context.contains(">>>"));
        assertTrue(context.contains("\"Network & internet\" at (100,"));
    }
}
