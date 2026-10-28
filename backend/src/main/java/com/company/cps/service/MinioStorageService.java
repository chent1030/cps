package com.company.cps.service;

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
 * <p>存储约定：新数据直接把可公开访问的绝对 URL 写入业务表（{@link #publicObjectUrl(String)}，
 * 形如 http://127.0.0.1:9000/cps-attachments/cps/xxx.jpg）；历史数据里是裸对象键（cps/xxx.jpg）。
 * 读取侧统一走 {@link #readFileUrl(String)} 兼容两种形态。
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

    /** http(s) 绝对地址判定（file_url 新旧两种形态的分流依据）。 */
    public static boolean isAbsoluteUrl(String value) {
        return value != null
                && (value.startsWith("http://") || value.startsWith("https://"));
    }

    /**
     * file_url → 对象键：绝对 URL 去掉存储前缀，裸对象键（历史数据）原样返回。
     */
    public String objectKeyOf(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("empty CPS storage reference");
        }
        String value = fileUrl.trim();
        if (!isAbsoluteUrl(value)) {
            return value;
        }
        if (!isPublicObjectUrl(value)) {
            throw new IllegalArgumentException("not a CPS storage object URL: " + value);
        }
        return value.substring((publicUrl + "/" + bucket + "/").length());
    }

    /**
     * 按 file_url 读取对象内容，同时兼容绝对 URL（新数据）与裸对象键（历史数据）。
     */
    public byte[] readFileUrl(String fileUrl) throws Exception {
        return read(objectKeyOf(fileUrl));
    }

    public byte[] readPublicObjectUrl(String value) throws Exception {
        if (!isPublicObjectUrl(value)) {
            throw new IllegalArgumentException("not a CPS storage object URL");
        }
        String objectKey = value.substring((publicUrl + "/" + bucket + "/").length());
        return read(objectKey);
    }
}
