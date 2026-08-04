package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Satu baris = satu kali proses upload dari kamera yang sudah diproses face
 * detection (setara tabel `detection_logs` di Laravel).
 *
 * `matchesJson` menyimpan hasil pencocokan dalam bentuk JSON text, contoh:
 * [{"name":"Gugun","confidence":0.62}] - persis seperti field "matches" di
 * response /api/upload pada versi Laravel. Kalau tidak ada wajah cocok,
 * isinya array kosong "[]".
 */
@Entity
@Table(name = "detection_logs")
@Getter
@Setter
@NoArgsConstructor
public class DetectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id", nullable = false, length = 100)
    private String cameraId;

    /** Path relatif snapshot di dalam folder frames, contoh: cam1/2026-07-18/11/frame_000281.jpg */
    @Column(name = "snapshot_path", nullable = false, length = 500)
    private String snapshotPath;

    @Column(name = "faces_detected", nullable = false)
    private Integer facesDetected;

    @Column(name = "matches_json", nullable = false, columnDefinition = "TEXT")
    private String matchesJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
