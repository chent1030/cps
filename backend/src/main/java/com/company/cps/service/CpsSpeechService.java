package com.company.cps.service;

import com.company.cps.config.CpsSpeechProperties;
import com.company.cps.dto.CpsSpeechTranscriptionResponse;
import com.company.cps.service.CpsSpeechTranscribeClient.Transcription;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * F3 语音上传链路：multipart 音频 → RustFS object_key → 同步调 C-06 speech-to-text → 转写文本。
 *
 * 约定：
 * - field 白名单 = D-03 三字段 reason / short_term / long_term（转写仅回填表单，不作点检证据）。
 * - 原始音频无论转写成败均先落 RustFS（cps/speech/{日期}/{uuid}-{文件名}），保留周期待定（Python 侧只读不删）。
 * - C-06 未配置(enabled=false)/连接失败/超时/非 2xx ⇒ 降级返回 UNAVAILABLE + 降级文案，不抛异常；
 *   SKIPPED（如 ASR 配置缺失）返回空文本 + skip_reason 文案。移动端允许重试（attempt 递增）或手工输入。
 */
@Service
public class CpsSpeechService {

    /** D-03 语音覆盖三字段白名单。 */
    static final Set<String> ALLOWED_FIELDS = Set.of("reason", "short_term", "long_term");

    static final String FALLBACK_UNAVAILABLE = "语音转写暂不可用，可点击重试或手动输入";
    static final String FALLBACK_SKIPPED = "语音转写已跳过";

    private final CpsSpeechProperties properties;
    private final RustFsStorageService storage;
    private final CpsSpeechTranscribeClient client;

    public CpsSpeechService(CpsSpeechProperties properties, RustFsStorageService storage,
                            CpsSpeechTranscribeClient client) {
        this.properties = properties;
        this.storage = storage;
        this.client = client;
    }

    public CpsSpeechTranscriptionResponse transcribe(MultipartFile file, String empNo, String field,
                                                     String submissionId, Integer attempt) {
        if (field == null || !ALLOWED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("field must be one of reason/short_term/long_term");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("audio file is required");
        }
        if (file.getSize() > properties.getMaxAudioBytes()) {
            throw new IllegalArgumentException("audio file exceeds size limit: " + properties.getMaxAudioBytes());
        }
        String resolvedSubmissionId = (submissionId == null || submissionId.isBlank())
                ? "speech-" + UUID.randomUUID().toString().substring(0, 8)
                : submissionId.trim();
        int resolvedAttempt = attempt == null ? 1 : attempt;
        if (resolvedAttempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }

        // 1) 原始音频先落 RustFS（同桶同鉴权，Python 内网按 object_key 取流）
        String originalName = firstNonBlank(file.getOriginalFilename(), "voice.webm");
        String audioFormat = audioFormatOf(originalName);
        String safeName = originalName.replace('\\', '_').replace('/', '_');
        String objectKey = "cps/speech/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "/" + UUID.randomUUID() + "-" + safeName;
        try {
            storage.put(objectKey, file.getBytes(),
                    firstNonBlank(file.getContentType(), "application/octet-stream"));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to store speech audio in RustFS", error);
        }

        // 2) 同步调 C-06（超时可配，默认 240s 量级）；失败降级不抛异常
        Transcription transcription = client.transcribe(
                resolvedSubmissionId, field, resolvedAttempt, objectKey, audioFormat);

        // 3) 组装响应：TRANSCRIBED 回填文本；SKIPPED 空文本+原因；UNAVAILABLE 降级文案
        CpsSpeechTranscriptionResponse response = new CpsSpeechTranscriptionResponse();
        response.setField(field);
        response.setAttempt(resolvedAttempt);
        response.setSubmissionId(resolvedSubmissionId);
        response.setAudioObjectKey(objectKey);
        if (transcription.degraded) {
            response.setStatus("UNAVAILABLE");
            response.setText(null);
            response.setFallbackMessage(FALLBACK_UNAVAILABLE);
        } else if ("SKIPPED".equals(transcription.status)) {
            response.setStatus("SKIPPED");
            response.setText("");
            response.setFallbackMessage(transcription.skipReason == null
                    ? FALLBACK_SKIPPED : FALLBACK_SKIPPED + "：" + transcription.skipReason);
            response.setDurationSeconds(transcription.durationSeconds);
            response.setModel(transcription.model);
        } else {
            response.setStatus("TRANSCRIBED");
            response.setText(transcription.text);
            response.setDurationSeconds(transcription.durationSeconds);
            response.setModel(transcription.model);
        }
        return response;
    }

    /** audio_format 从文件名后缀推断（C-06 契约：缺省后缀推断，再缺省 wav——wav 由 Python 侧兜底）。 */
    static String audioFormatOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
