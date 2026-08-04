package com.homeserver.cctv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeserver.cctv.dto.FaceMatch;
import com.homeserver.cctv.entity.FaceEmbedding;
import com.homeserver.cctv.repository.FaceEmbeddingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Setara logika pencocokan cosine similarity yang di Laravel ditulis manual
 * di PHP. Di sini logikanya sama, cuma bahasanya Java.
 *
 * PERUBAHAN: sekarang bandingkan ke SEMUA embedding (bukan cuma 1 per
 * orang), karena 1 KnownFace bisa punya banyak FaceEmbedding (banyak foto).
 * Similarity tertinggi di antara SEMUA foto SEMUA orang yang menentukan
 * hasil match - ini otomatis berarti "similarity tertinggi terhadap salah
 * satu foto orang tersebut", tidak perlu agregasi tambahan per orang.
 */
@Service
public class FaceMatchingService {

    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final ObjectMapper objectMapper;
    private final double matchThreshold;

    public FaceMatchingService(
            FaceEmbeddingRepository faceEmbeddingRepository,
            ObjectMapper objectMapper,
            @Value("${cctv.face-match-threshold}") double matchThreshold
    ) {
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.objectMapper = objectMapper;
        this.matchThreshold = matchThreshold;
    }

    /**
     * Cari embedding (dari foto manapun, orang manapun) dengan cosine
     * similarity tertinggi terhadap embedding yang diberikan. Return kosong
     * (Optional.empty()) kalau similarity tertinggi masih di bawah threshold
     * (dianggap "unknown").
     */
    public Optional<FaceMatch> findBestMatch(List<Double> embedding) {
        List<FaceEmbedding> allEmbeddings = faceEmbeddingRepository.findAllWithKnownFace();

        String bestName = null;
        double bestScore = -1.0;

        for (FaceEmbedding known : allEmbeddings) {
            List<Double> knownEmbedding = parseEmbedding(known.getEmbeddingJson());
            double score = cosineSimilarity(embedding, knownEmbedding);
            if (score > bestScore) {
                bestScore = score;
                bestName = known.getKnownFace().getName();
            }
        }

        if (bestName != null && bestScore >= matchThreshold) {
            return Optional.of(new FaceMatch(bestName, round(bestScore)));
        }
        return Optional.empty();
    }

    public String toEmbeddingJson(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (Exception e) {
            throw new RuntimeException("Gagal serialize embedding ke JSON", e);
        }
    }

    private List<Double> parseEmbedding(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Gagal parse embedding_json dari database", e);
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Dimensi embedding tidak sama: " + a.size() + " vs " + b.size());
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
