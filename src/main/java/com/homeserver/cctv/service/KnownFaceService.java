package com.homeserver.cctv.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.homeserver.cctv.dto.FaceDetectionResponse;
import com.homeserver.cctv.entity.FaceEmbedding;
import com.homeserver.cctv.entity.KnownFace;
import com.homeserver.cctv.repository.FaceEmbeddingRepository;
import com.homeserver.cctv.repository.KnownFaceRepository;

@Service
public class KnownFaceService {
    
    private static final Logger log = LoggerFactory.getLogger(KnownFaceService.class);
    
    private final KnownFaceRepository knownFaceRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FaceServiceClient faceServiceClient;
    private final FaceMatchingService faceMatchingService;

    @Value("${cctv.storage.path}")
    private String storageBasePath;

    public KnownFaceService(KnownFaceRepository knownFaceRepository, FaceEmbeddingRepository faceEmbeddingRepository, FaceServiceClient faceServiceClient, FaceMatchingService faceMatchingService) {
        this.knownFaceRepository = knownFaceRepository;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.faceServiceClient = faceServiceClient;
        this.faceMatchingService = faceMatchingService;
    }

    public KnownFace getOrCreate(String name) {
        return knownFaceRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    KnownFace kf = new KnownFace();
                    kf.setName(name);
                    return knownFaceRepository.save(kf);
                });
    }

    /**
     * Deteksi wajah di poto, simpan file + embedding untuk knownFace ini.
     * Melempat IOException kalau tidak ada wajah / lebih dari 1 wajah - 
     * panggil (controller) yang menentukan bagaimana menampilkan error ini. 
     */
    public FaceEmbedding addPhoto(KnownFace knownFace, byte[] imageBytes, String originalFilename) throws IOException {
        FaceDetectionResponse detection = faceServiceClient.detectFaces(imageBytes, originalFilename);
        if (detection == null || detection.faces() == null || detection.faces().isEmpty()) {
            throw new IOException("No face detected in the image.");
        }
        if (detection.faces().size() > 1) {
            throw new IOException("Multiple faces detected in the image.");
        }

        List<Double> embedding = detection.faces().get(0).embedding();

        Path dir = Path.of(storageBasePath, "known_faces", String.valueOf(knownFace.getId()));
        Files.createDirectories(dir);
        String fileName = System.currentTimeMillis() + "_" + sanitize(originalFilename);
        Path filePath = dir.resolve(fileName);
        Files.write(filePath, imageBytes);

        String relativePath = Path.of(String.valueOf(knownFace.getId()), fileName).toString();

        FaceEmbedding faceEmbedding = new FaceEmbedding();
        faceEmbedding.setKnownFace(knownFace);
        faceEmbedding.setEmbeddingJson(faceMatchingService.toEmbeddingJson(embedding));
        faceEmbedding.setImagePath(relativePath);
        return faceEmbeddingRepository.save(faceEmbedding);
    }

    public void deletePhoto(FaceEmbedding embedding) {
        if (embedding.getImagePath() != null) {
            try {
                Path path = Path.of(storageBasePath, "known_faces", embedding.getImagePath());
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.error("Failed to delete photo known face id={}: {}" + embedding.getId(), e.getMessage());
            }
        }
        faceEmbeddingRepository.delete(embedding);
    }

    private String sanitize(String filename) {
        return filename == null? "photo.jpg" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
