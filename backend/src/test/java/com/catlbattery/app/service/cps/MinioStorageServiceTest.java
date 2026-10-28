package com.catlbattery.app.service.cps;

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

/** MinIO 契约：业务表只存可公开访问的绝对 URL，读取按 URL 反查对象键。 */
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
    void recognizesOwnPublicUrlOnly() {
        assertTrue(storage.isPublicObjectUrl(ABSOLUTE_URL));
        assertFalse(storage.isPublicObjectUrl("http://other-host/cps-attachments/cps/photo-1.png"));
        assertFalse(storage.isPublicObjectUrl("cps/photo-1.png"));
        assertFalse(storage.isPublicObjectUrl(null));
    }

    @Test
    void readByUrlRejectsNonStorageValue() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.readByUrl("http://other-host/cps-attachments/cps/photo-1.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.readByUrl("cps/photo-1.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.readByUrl(null));
    }

    @Test
    void readByUrlStripsPrefixAndReadsObject() throws Exception {
        byte[] bytes = "image-bytes".getBytes("UTF-8");
        MinioStorageService spied = spy(storage);
        doReturn(bytes).when(spied).read("cps/photo-1.png");
        assertArrayEquals(bytes, spied.readByUrl(ABSOLUTE_URL));
        verify(spied).read("cps/photo-1.png");
    }
}
