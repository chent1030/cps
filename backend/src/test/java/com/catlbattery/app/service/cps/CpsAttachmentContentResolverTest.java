package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-N1/P0 修复核心：附件内容解析。
 * 新附件 content 恒 NULL，file_url 是 MinIO 绝对 URL 或历史裸对象键 → 必须按流读取，不再静默跳过。
 */
class CpsAttachmentContentResolverTest {

    private MinioStorageService storageService;
    private CpsAttachmentContentResolver resolver;

    @BeforeEach
    void setUp() {
        storageService = mock(MinioStorageService.class);
        resolver = new CpsAttachmentContentResolver(storageService);
    }

    @Test
    void inlineContentPassthroughWithoutStorage() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/legacy-1.png");
        byte[] content = "legacy-inline".getBytes("UTF-8");
        attachment.setContent(content);
        assertArrayEquals(content, resolver.resolve(attachment));
        verify(storageService, org.mockito.Mockito.never()).readByUrl(anyString());
    }

    @Test
    void nullContentReadsFromMinIOByObjectKey() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/new-attachment.png");
        byte[] bytes = "minio-content".getBytes("UTF-8");
        when(storageService.readByUrl("cps/new-attachment.png")).thenReturn(bytes);
        assertArrayEquals(bytes, resolver.resolve(attachment));
        verify(storageService).readByUrl("cps/new-attachment.png");
    }

    @Test
    void nullContentReadsFromMinIOByAbsoluteUrl() throws Exception {
        // B7 新数据：cps_issue_attachment.file_url 直接存 MinIO 绝对 URL，读取端必须兼容。
        String fileUrl = "http://127.0.0.1:9000/cps-attachments/cps/abs-attachment.png";
        CpsIssueAttachment attachment = attachment(null, fileUrl);
        byte[] bytes = "minio-absolute-url".getBytes("UTF-8");
        when(storageService.readByUrl(fileUrl)).thenReturn(bytes);
        assertArrayEquals(bytes, resolver.resolve(attachment));
        verify(storageService).readByUrl(fileUrl);
    }

    @Test
    void nullContentAndNullObjectKeyYieldsNull() {
        CpsIssueAttachment attachment = attachment(null, null);
        assertNull(resolver.resolve(attachment));
    }

    @Test
    void minioFailureFailsFastInsteadOfSilentSkip() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/missing.png");
        when(storageService.readByUrl("cps/missing.png")).thenThrow(new RuntimeException("minio down"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(attachment));
        assertEquals("Unable to read attachment 42 from MinIO object 'cps/missing.png': minio down",
                exception.getMessage());
    }

    private CpsIssueAttachment attachment(byte[] content, String objectKey) {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(42L);
        attachment.setFileName("photo.png");
        attachment.setFileType("image/png");
        attachment.setContent(content);
        attachment.setFileUrl(objectKey);
        return attachment;
    }
}
