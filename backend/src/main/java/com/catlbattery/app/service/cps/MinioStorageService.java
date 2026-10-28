package com.catlbattery.app.service.cps;

import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

/**
 * MinIO 对象存储（默认 endpoint http://127.0.0.1:9000，bucket cps-attachments）。
 *
 * <p>存储约定：字节落桶，业务表里存的**只有一种形态**——可公开访问的绝对 URL
 * （{@link #publicObjectUrl(String)}，形如 http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg）。
 * 读取侧统一走 {@link #readByUrl(String)}，不再区分「裸对象键」形态。
 */
@Service
public class MinioStorageService {
    private final MinioClient client;
    private final String bucket;
    private final String publicUrl;

    public MinioStorageService(
            @Value("${cps.storage.endpoint}") String endpoint,
            @Value("${cps.storage.access-key}") String accessKey,
            @Value("${cps.storage.secret-key}") String secretKey,
            @Value("${cps.storage.bucket}") String bucket,
            @Value("${cps.storage.public-url}") String publicUrl) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @PostConstruct
    public void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public void put(String objectKey, byte[] content, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                .stream(new ByteArrayInputStream(content), content.length, -1)
                .contentType(contentType).build());
    }

    public InputStream get(String objectKey) throws Exception {
        return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    public byte[] read(String objectKey) throws Exception {
        InputStream input = get(objectKey);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) output.write(buffer, 0, length);
        input.close();
        return output.toByteArray();
    }

    /**
     * 供浏览器预览和向量服务读取的对象访问地址。对象桶需要配置为只读公开访问。
     */
    public String publicObjectUrl(String objectKey) {
        return publicUrl + "/" + bucket + "/" + objectKey;
    }

    /** 是否为本存储的公开对象 URL（形如 {public-url}/{bucket}/{objectKey}）。 */
    public boolean isPublicObjectUrl(String value) {
        return value != null && value.startsWith(publicUrl + "/" + bucket + "/");
    }

    /** 按公开 URL 读对象内容；不是本存储的地址直接报错（调用方据此判断是否在 MinIO）。 */
    public byte[] readByUrl(String fileUrl) throws Exception {
        if (!isPublicObjectUrl(fileUrl)) {
            throw new IllegalArgumentException("not a CPS storage object URL: " + fileUrl);
        }
        return read(fileUrl.trim().substring((publicUrl + "/" + bucket + "/").length()));
    }
}
