package com.company.cps.service;

import com.company.cps.domain.CpsIssueAttachment;
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
 * 新附件 content 恒 NULL（RustFS object_key）→ 必须按流读取，不再静默跳过。
 */
class CpsAttachmentContentResolverTest {

    private RustFsStorageService storageService;
    private CpsAttachmentContentResolver resolver;

    @BeforeEach
    void setUp() {
        storageService = mock(RustFsStorageService.class);
        resolver = new CpsAttachmentContentResolver(storageService);
    }

    @Test
    void inlineContentPassthroughWithoutStorage() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/legacy-1.png");
        byte[] content = "legacy-inline".getBytes("UTF-8");
        attachment.setContent(content);
        assertArrayEquals(content, resolver.resolve(attachment));
        verify(storageService, org.mockito.Mockito.never()).read(anyString());
    }

    @Test
    void nullContentReadsFromRustFsByObjectKey() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/new-attachment.png");
        byte[] bytes = "rustfs-content".getBytes("UTF-8");
        when(storageService.read("cps/new-attachment.png")).thenReturn(bytes);
        assertArrayEquals(bytes, resolver.resolve(attachment));
    }

    @Test
    void nullContentAndNullObjectKeyYieldsNull() {
        CpsIssueAttachment attachment = attachment(null, null);
        assertNull(resolver.resolve(attachment));
    }

    @Test
    void rustFsFailureFailsFastInsteadOfSilentSkip() throws Exception {
        CpsIssueAttachment attachment = attachment(null, "cps/missing.png");
        when(storageService.read("cps/missing.png")).thenThrow(new RuntimeException("rustfs down"));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(attachment));
        assertEquals("Unable to read attachment 42 from RustFS object 'cps/missing.png': rustfs down",
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
