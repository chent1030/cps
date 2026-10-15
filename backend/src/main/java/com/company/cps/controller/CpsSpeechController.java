package com.company.cps.controller;

import com.company.cps.dto.CpsSpeechTranscriptionResponse;
import com.company.cps.service.CpsSpeechService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * F3/F4 语音上传与转写（mobile → Java → RustFS → Python C-06 speech-to-text，同步）。
 *
 * multipart：file=音频，field=reason|short_term|long_term（D-03 三字段），
 * submissionId（缺省服务端派生）、attempt（同字段重录递增，缺省 1）。
 * 转写文本仅回填移动端表单，不作点检/整改证据（PRD §20.2）。
 */
@RestController
@RequestMapping("/api/cps/speech")
public class CpsSpeechController {

    private final CpsSpeechService speechService;

    public CpsSpeechController(CpsSpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping("/transcriptions")
    public CpsSpeechTranscriptionResponse transcribe(
            @RequestParam("file") MultipartFile file,
            @RequestParam("field") String field,
            @RequestParam(value = "empNo", required = false) String empNo,
            @RequestParam(value = "submissionId", required = false) String submissionId,
            @RequestParam(value = "attempt", required = false) Integer attempt) {
        return speechService.transcribe(file, resolveCurrentEmpNo(empNo), field, submissionId, attempt);
    }

    private String resolveCurrentEmpNo(String empNo) {
        if (empNo != null && !empNo.trim().isEmpty()) {
            return empNo.trim();
        }
        return "DEV_EMP";
    }
}
