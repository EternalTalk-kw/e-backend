package com.example.eternaltalk.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.time.Duration;

@Component
public class S3Uploader {
    private final S3Client s3;
    private final S3Presigner presigner;

    @Value("${S3_BUCKET}")
    private String bucket;

    public S3Uploader(S3Client s3, S3Presigner presigner) {
        this.s3 = s3; this.presigner = presigner;
    }

    public String uploadBytes(String key, byte[] data, String contentType){
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket).key(key)
                .contentType(contentType).build();
        s3.putObject(put, RequestBody.fromBytes(data));
        return presign(key);
    }

    private String presign(String key){
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket).key(key).build();
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(12))
                .getObjectRequest(get).build();
        URL url = presigner.presignGetObject(req).url();
        return url.toString();
    }
}
