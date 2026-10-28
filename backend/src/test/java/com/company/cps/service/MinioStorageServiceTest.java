package com.company.cps.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** MinIO file_url 新契约：绝对 URL 与历史裸对象键的判定、归一与读取。 */
class MinioStorageServiceTest {

    private static final String PUBLIC_URL = "http://127.0.0.1:9000";
    private static final String BUCKET = "cps-attachments";
    private static final String ABSOLUTE_URL = PUBLIC_URL + "/" + BUCKET + "/cps/photo-1.png";

    private MinioStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new MinioStorageService(PUBLIC_URL, "admin", "secret", BUCKET, PUBLIC_URL);
    }

    @Test
    void publicObjectUrlPointsAtMinioBucket() {
        assertEquals(ABSOLUTE_URL, storage.publicObjectUrl("cps/photo-1.png"));
    }

    @Test
    void absoluteUrlDetection() {
        assertTrue(MinioStorageService.isAbsoluteUrl("http://127.0.0.1:9000/cps-attachments/cps/a.png"));
        assertTrue(MinioStorageService.isAbsoluteUrl("https://minio.example.com/cps-attachments/cps/a.png"));
        assertFalse(MinioStorageService.isAbsoluteUrl("cps/a.png"));
        assertFalse(MinioStorageService.isAbsoluteUrl("/api/cps/attachments/1/content"));
        assertFalse(MinioStorageService.isAbsoluteUrl(null));
    }

    @Test
    void objectKeyOfAcceptsAbsoluteUrlAndBareKey() {
        assertEquals("cps/photo-1.png", storage.objectKeyOf(ABSOLUTE_URL));
        assertEquals("cps/photo-1.png", storage.objectKeyOf("cps/photo-1.png"));
        assertTrue(storage.isPublicObjectUrl(ABSOLUTE_URL));
    }

    @Test
    void objectKeyOfRejectsForeignUrlAndEmptyValue() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.objectKeyOf("http://other-host/cps-attachments/cps/photo-1.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.objectKeyOf("  "));
    }

    @Test
    void readFileUrlDelegatesToObjectKeyRead() throws Exception {
        byte[] bytes = "image-bytes".getBytes("UTF-8");
        MinioStorageService spied = spy(storage);
        doReturn(bytes).when(spied).read("cps/photo-1.png");
        assertArrayEquals(bytes, spied.readFileUrl(ABSOLUTE_URL));
        verify(spied).read("cps/photo-1.png");
    }
}
