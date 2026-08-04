package com.homeserver.cctv.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Bentuk response dari Python face-service (POST /detect), dikonfirmasi
 * langsung dari main.py:
 *
 * {
 *   "faces_detected": 2,
 *   "faces": [
 *     { "bbox": [x1, y1, x2, y2], "embedding": [...512 float...], "det_score": 0.93 }
 *   ]
 * }
 *
 * "faces_detected" di level atas tidak kita mapping ke sini karena kita
 * cukup pakai faces.size() di Java - lebih aman daripada percaya 2 sumber
 * angka yang seharusnya selalu sama.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FaceDetectionResponse(List<DetectedFace> faces) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetectedFace(
            List<Double> bbox,               // [x1, y1, x2, y2] dalam pixel (int di Python, otomatis kebaca sebagai Double di Java)
            List<Double> embedding,          // embedding 512-dim, SUDAH dinormalisasi (unit length) oleh InsightFace
            @JsonProperty("det_score") Double detScore  // confidence deteksi wajah (bukan confidence matching nama)
    ) {}
}

