package com.homeserver.cctv.service;

import com.homeserver.cctv.entity.Recording;
import com.homeserver.cctv.entity.RecordingUnknownFace;
import com.homeserver.cctv.entity.UnknownFace;
import com.homeserver.cctv.entity.UnknownFaceImage;
import com.homeserver.cctv.repository.RecordingUnknownFaceRepository;
import com.homeserver.cctv.repository.UnknownFaceImageRepository;
import com.homeserver.cctv.repository.UnknownFaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class UnknownFaceService {

    private final UnknownFaceRepository unknownFaceRepository;
    private final UnknownFaceImageRepository unknownFaceImageRepository;
    private final RecordingUnknownFaceRepository recordingUnknownFaceRepository;
    private final ObjectMapper objectMapper;

    @Value("${cctv.unknown-face.similarity-threshold:0.4}")
    private double similarityThreshold;

    @Value("${cctv.unknown-face.max-images-per-face:5}")
    private int maxImagesPerFace;

    @Value("${cctv.storage.path:/data/storage}")
    private String storageBasePath;

    public UnknownFaceService(
        UnknownFaceRepository unknownFaceRepository,
        UnknownFaceImageRepository unknownFaceImageRepository,
        RecordingUnknownFaceRepository recordingUnknownFaceRepository,
        ObjectMapper objectMapper
    ) {
        this.unknownFaceRepository = unknownFaceRepository;
        this.unknownFaceImageRepository = unknownFaceImageRepository;
        this.recordingUnknownFaceRepository = recordingUnknownFaceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Dipanggil dari FaceDetectionService/ImageAnnotationService setiap kali
     * FastApi mengembalikan wajah dengan recognize=false, tapi tetap
     * menyertakan embedding vector.
     * 
     * Scope GLOBAL: pencarian match dilakukan lintas semua kamera
     */
    public UnknownFace handleUnknownFace(List<Double> embedding, byte[] croppedImageBytes, LocalDateTime detectedAt, Recording currentRecording) {

        UnknownFace match = findBestMatch(embedding);

        UnknownFace unknownFace;
        if (match != null) {
            match.setLastSeenAt(detectedAt);
            match.setDetectionCount(match.getDetectionCount() + 1);
            unknownFace = unknownFaceRepository.save(match);
            log.debug(">> LOG: Unknown face matched existing id={} (detectionCount={})", unknownFace.getId(), unknownFace.getDetectionCount());
        } else {
            unknownFace = new UnknownFace();
            unknownFace.setEmbeddingJson(toJson(embedding));
            unknownFace.setFirstSeenAt(detectedAt);
            unknownFace.setLastSeenAt(detectedAt);
            unknownFace.setDetectionCount(1);
            unknownFace = unknownFaceRepository.save(unknownFace);
            log.info(">> LOG: New unknown face identity created id={}", unknownFace.getId());
        }

        saveImageIfUnderLimit(unknownFace, croppedImageBytes, detectedAt);
        linkToRecording(currentRecording, unknownFace, detectedAt);

        return unknownFace;
    }

    /**
     * Cari identitas unknown yang paling mirip berdasarkan cosine similarity.
     * Scope global -> ambil semua row. untuk skala rumahan (puluhan - ratusan unknown identity) ini masih murah; 
     * kalau nanti membesar pertimbangkan pgvector + index approximate nearest neighbor.
     */
    private UnknownFace findBestMatch(List<Double> embedding) {
        List<UnknownFace> candidates = unknownFaceRepository.findAll();

        UnknownFace best = null;
        double bestSimilarity = -1;

        for (UnknownFace candidate : candidates) {
            if (candidate.getEmbeddingJson() == null) continue;
            List<Double> candidateEmbedding = fromJson(candidate.getEmbeddingJson());
            double similarity = cosineSimilarity(embedding, candidateEmbedding);
            if(similarity >= similarityThreshold && similarity > bestSimilarity) {
                best = candidate;
                bestSimilarity = similarity;
            }
        }

        return best;
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return -1;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return -1;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String toJson(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException(">> LOG: Failed serialize embedding unknown face", e);
        }
    }

    private List<Double> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            throw new RuntimeException(">> LOG: Failed parse embedding unknown face", e);
        }
    }

    private void saveImageIfUnderLimit(UnknownFace unknownFace, byte[] imageByte, LocalDateTime capturedAt) {
        long currentCount = unknownFaceImageRepository.countByUnknownFaceId(unknownFace.getId());
        if(currentCount >= maxImagesPerFace) {
            return; // max 5 images, skip
        }

        try {
            Path dir = Paths.get(storageBasePath, "unknown_faces", String.valueOf(unknownFace.getId()));
            Files.createDirectories(dir);

            String fileName = capturedAt.toString().replace(":", "-") + ".jpg";
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, imageByte);

            // Simpan path RELATIF (terhadap storageBasePath/unknown_faces/), bukan albolute
            // Supaya konsiten dengan Recording.videoPath dan bisa langsung di pakai di /mendia/...
            String relativePath = Path.of(String.valueOf(unknownFace.getId()), fileName).toString();

            UnknownFaceImage image = new UnknownFaceImage();
            image.setUnknownFace(unknownFace);
            image.setImagePath(relativePath);
            image.setCapturedAt(capturedAt);
            unknownFaceImageRepository.save(image);

            // set first image as revresentative thumbnail id not exist yet
            if (unknownFace.getRepresentativeImagePath() == null) {
                unknownFace.setRepresentativeImagePath(filePath.toString());
                unknownFaceRepository.save(unknownFace);
            }
        } catch (IOException e) {
            log.error(">> LOG: Failed save image unknown face id={}: {}", unknownFace.getId(), e.getMessage(), e);
        }
    }

    /**
     * Catat rentang waktu unknown face ini muncul dalam recording (jam) tertentu.
     * kalau sudah ada link untuk recording+face ini, update lastSeenAt saja.
     */
    private void linkToRecording(Recording recording, UnknownFace unknownFace, LocalDateTime detectedAt) {
        if (recording == null) return;

        RecordingUnknownFace link = recordingUnknownFaceRepository
            .findByRecordingIdAndUnknownFaceId(recording.getId(), unknownFace.getId())
            .orElse(null);

        if (link == null) {
            link = new RecordingUnknownFace();
            link.setRecording(recording);
            link.setUnknownFace(unknownFace);
            link.setFirstSeenAt(detectedAt);
            link.setLastSeenAt(detectedAt);
        } else {
            link.setLastSeenAt(detectedAt);
        }

        recordingUnknownFaceRepository.save(link);
    }
}