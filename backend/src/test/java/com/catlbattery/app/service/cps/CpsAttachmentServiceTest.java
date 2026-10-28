package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsIssueAttachment;
import com.catlbattery.domain.entity.cps.dto.CpsAttachmentUploadResponse;
import com.catlbattery.infra.mapper.cps.CpsIssueAttachmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B7：上传直接把 MinIO 绝对 URL 写进 file_url 并原样返回，content 读取兼容两种 file_url 形态。 */
class CpsAttachmentServiceTest {

    private static final String ABSOLUTE_URL =
            "http://127.0.0.1:9000/cps-attachments/cps/11111111-2222-3333-4444-555555555555-photo.png";

    private CpsIssueAttachmentMapper attachmentMapper;
    private MinioStorageService storage;
    private CpsAttachmentService service;

    @BeforeEach
    void setUp() {
        attachmentMapper = mock(CpsIssueAttachmentMapper.class);
        storage = mock(MinioStorageService.class);
        service = new CpsAttachmentService(attachmentMapper, storage);
    }

    @Test
    void uploadStoresAbsoluteUrlInFileUrlAndReturnsSameUrl() throws Exception {
        byte[] bytes = "upload-bytes".getBytes("UTF-8");
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", bytes);
        when(storage.publicObjectUrl(anyString())).thenReturn(ABSOLUTE_URL);

        CpsAttachmentUploadResponse response = service.upload(file, "E001");

        assertEquals(ABSOLUTE_URL, response.getUrl());
        assertEquals("photo.png", response.getName());
        assertFalse(response.getUrl().startsWith("/api/cps/attachments/"));

        ArgumentCaptor<CpsIssueAttachment> captor = ArgumentCaptor.forClass(CpsIssueAttachment.class);
        verify(attachmentMapper).insert(captor.capture());
        assertEquals(ABSOLUTE_URL, captor.getValue().getFileUrl());
        verify(storage).put(startsWith("cps/"), eq(bytes), eq("image/png"));
    }

    @Test
    void contentReadsAbsoluteUrlFileUrlThroughStorage() throws Exception {
        CpsIssueAttachment attachment = new CpsIssueAttachment();
        attachment.setId(9L);
        attachment.setFileUrl(ABSOLUTE_URL);
        attachment.setFileType("image/png");
        when(attachmentMapper.findById(9L)).thenReturn(Optional.of(attachment));
        byte[] bytes = "minio-bytes".getBytes("UTF-8");
        when(storage.readByUrl(ABSOLUTE_URL)).thenReturn(bytes);

        ResponseEntity<byte[]> response = service.content(9L);

        assertEquals(200, response.getStatusCodeValue());
        assertArrayEquals(bytes, response.getBody());
        verify(storage).readByUrl(ABSOLUTE_URL);
        verify(storage, org.mockito.Mockito.never()).read(any());
    }
}
