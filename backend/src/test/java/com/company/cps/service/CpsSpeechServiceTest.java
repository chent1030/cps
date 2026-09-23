package com.company.cps.service;

import com.company.cps.config.CpsSpeechProperties;
import com.company.cps.dto.CpsSpeechTranscriptionResponse;
import com.company.cps.service.CpsSpeechTranscribeClient.Transcription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F3 语音上传链路：白名单校验 / RustFS 落桶 / C-06 透传 / 降级文案。 */
class CpsSpeechServiceTest {

    private CpsSpeechProperties properties;
    private RustFsStorageService storage;
    private CpsSpeechTranscribeClient transcribeClient;
    private CpsSpeechService service;

    @BeforeEach
    void setUp() {
        properties = new CpsSpeechProperties();
        storage = mock(RustFsStorageService.class);
        transcribeClient = mock(CpsSpeechTranscribeClient.class);
        service = new CpsSpeechService(properties, storage, transcribeClient);
    }

    private MockMultipartFile audio(String name) {
        return new MockMultipartFile("file", name, "audio/webm", new byte[]{1, 2, 3, 4});
    }

    @Test
    void happyPathStoresAudioThenTranscribes() throws Exception {
        when(transcribeClient.transcribe(eq("issue-9"), eq("reason"), eq(1), anyString(), eq("webm")))
                .thenReturn(Transcription.transcribed("泵房地面有积水", 6.5, "qwen3-asr-flash"));
        CpsSpeechTranscriptionResponse response =
                service.transcribe(audio("voice.webm"), "E001", "reason", "issue-9", 1);

        assertEquals("TRANSCRIBED", response.getStatus());
        assertEquals("泵房地面有积水", response.getText());
        assertEquals("reason", response.getField());
        assertEquals(Integer.valueOf(1), response.getAttempt());
        assertEquals("qwen3-asr-flash", response.getModel());
        assertTrue(response.getAudioObjectKey().startsWith("cps/speech/"));
        assertTrue(response.getAudioObjectKey().endsWith("voice.webm"));
        verify(storage).put(anyString(), any(byte[].class), anyString());
    }

    @Test
    void objectKeyCarriesDatePartition() {
        when(transcribeClient.transcribe(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(Transcription.unavailable());
        service.transcribe(audio("v.mp3"), "E001", "short_term", null, null);
        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(transcribeClient).transcribe(anyString(), eq("short_term"), eq(1),
                keyCaptor.capture(), eq("mp3"));
        assertTrue(keyCaptor.getValue().matches("cps/speech/\\d{8}/[\\w-]+-v\\.mp3"),
                "unexpected object key: " + keyCaptor.getValue());
    }

    @Test
    void fieldWhitelistEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(audio("v.webm"), "E001", "description", "s", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(audio("v.webm"), "E001", null, "s", 1));
    }

    @Test
    void emptyFileRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(new MockMultipartFile("file", "v.webm", "audio/webm", new byte[0]),
                        "E001", "reason", "s", 1));
    }

    @Test
    void oversizedAudioRejected() {
        properties.setMaxAudioBytes(3);
        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(audio("v.webm"), "E001", "reason", "s", 1));
    }

    @Test
    void invalidAttemptRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.transcribe(audio("v.webm"), "E001", "reason", "s", 0));
    }

    @Test
    void degradedClientYieldsFallbackMessageNotError() {
        when(transcribeClient.transcribe(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(Transcription.unavailable());
        CpsSpeechTranscriptionResponse response =
                service.transcribe(audio("v.webm"), "E001", "long_term", "issue-9", 2);

        assertEquals("UNAVAILABLE", response.getStatus());
        assertNull(response.getText());
        assertEquals(CpsSpeechService.FALLBACK_UNAVAILABLE, response.getFallbackMessage());
        assertEquals(Integer.valueOf(2), response.getAttempt());
    }

    @Test
    void skippedResultYieldsEmptyTextAndReason() {
        when(transcribeClient.transcribe(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(Transcription.skipped("asr provider disabled", null, null));
        CpsSpeechTranscriptionResponse response =
                service.transcribe(audio("v.webm"), "E001", "reason", null, null);

        assertEquals("SKIPPED", response.getStatus());
        assertEquals("", response.getText());
        assertEquals(CpsSpeechService.FALLBACK_SKIPPED + "：asr provider disabled",
                response.getFallbackMessage());
    }

    @Test
    void storageFailureSurfacesAsIllegalState() {
        // put 为 void 方法且声明受检异常：用 doThrow 桩运行时异常
        try {
            doThrow(new RuntimeException("rustfs down"))
                    .when(storage)
                    .put(anyString(), any(byte[].class), anyString());
        } catch (Exception ignored) {
            // stub 声明受检异常路径
        }
        assertThrows(IllegalStateException.class,
                () -> service.transcribe(audio("v.webm"), "E001", "reason", "s", 1));
    }

    @Test
    void submissionIdDefaultsToDerivedValue() {
        when(transcribeClient.transcribe(anyString(), anyString(), anyInt(), anyString(), isNull()))
                .thenReturn(Transcription.unavailable());
        CpsSpeechTranscriptionResponse response =
                service.transcribe(audio("v"), "E001", "reason", "  ", 1);
        assertTrue(response.getSubmissionId().startsWith("speech-"));
        verify(transcribeClient).transcribe(anyString(), eq("reason"), eq(1), anyString(), isNull());
    }
}
