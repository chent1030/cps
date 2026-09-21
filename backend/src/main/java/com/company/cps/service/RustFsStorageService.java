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

@Service
public class RustFsStorageService {
    private final MinioClient client;
    private final String bucket;
    private final String publicUrl;

    public RustFsStorageService(
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

    public boolean isPublicObjectUrl(String value) {
        return value != null && value.startsWith(publicUrl + "/" + bucket + "/");
    }

    public byte[] readPublicObjectUrl(String value) throws Exception {
        if (!isPublicObjectUrl(value)) {
            throw new IllegalArgumentException("not a CPS storage object URL");
        }
        String objectKey = value.substring((publicUrl + "/" + bucket + "/").length());
        return read(objectKey);
    }
}
