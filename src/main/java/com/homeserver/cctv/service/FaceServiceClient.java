package com.homeserver.cctv.service;

import com.homeserver.cctv.dto.FaceDetectionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pemanggil REST ke Python face-service (stateless, cuma deteksi + embedding,
 * tidak menyentuh database - persis seperti dijelaskan di README Laravel).
 *
 * Endpoint & bentuk response sudah dikonfirmasi dari main.py:
 * POST /detect (multipart, field "file") -> { "faces_detected": N, "faces": [...] }
 *
 * CATATAN: body multipart dibangun manual di sini (bukan pakai
 * LinkedMultiValueMap/MultipartBodyBuilder bawaan Spring), supaya kontrol
 * persis byte yang dikirim. Root cause masalah sebelumnya ternyata BUKAN di
 * sini, tapi di RestTemplate yang default pakai JDK HttpClient modern -
 * lihat RestTemplateConfig.java yang sekarang paksa SimpleClientHttpRequestFactory.
 */
@Component
public class FaceServiceClient {

    private final RestTemplate restTemplate;
    private final String faceServiceUrl;

    public FaceServiceClient(
            RestTemplate restTemplate,
            @Value("${cctv.face-service.url}") String faceServiceUrl
    ) {
        this.restTemplate = restTemplate;
        this.faceServiceUrl = faceServiceUrl;
    }

    public FaceDetectionResponse detectFaces(byte[] imageBytes, String originalFilename) {
        String boundary = "----CctvBoundary" + UUID.randomUUID().toString().replace("-", "");

        byte[] body;
        try {
            body = buildMultipartBody(boundary, imageBytes, originalFilename);
        } catch (IOException e) {
            throw new RuntimeException("Gagal membangun multipart body", e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("multipart/form-data; boundary=" + boundary));

        HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(
                faceServiceUrl + "/detect",
                request,
                FaceDetectionResponse.class
        );
    }

    private byte[] buildMultipartBody(String boundary, byte[] imageBytes, String filename) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String lineEnd = "\r\n";

        out.write(("--" + boundary + lineEnd).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + lineEnd)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/octet-stream" + lineEnd).getBytes(StandardCharsets.UTF_8));
        out.write(lineEnd.getBytes(StandardCharsets.UTF_8));
        out.write(imageBytes);
        out.write(lineEnd.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + lineEnd).getBytes(StandardCharsets.UTF_8));

        return out.toByteArray();
    }
}