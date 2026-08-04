package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Satu baris = status rekaman untuk 1 kamera, 1 tanggal, 1 jam (setara tabel
 * `recordings` di Laravel). Selama jam itu masih berjalan, statusnya
 * RECORDING dan frame terus ditambahkan ke folder. Setelah jam itu berakhir
 * dan scheduler berhasil menggabungkan frame-nya jadi video, status berubah
 * jadi COMPILED dan `videoPath` terisi.
 */
@Entity
@Table(
    name = "recordings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"camera_id", "recording_date", "hour"})
)
@Getter
@Setter
@NoArgsConstructor
public class Recording {

    public enum Status {
        RECORDING,
        COMPILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id", nullable = false, length = 100)
    private String cameraId;

    @Column(name = "recording_date", nullable = false)
    private LocalDate recordingDate;

    /** Jam dalam format 24 jam, 0-23 */
    @Column(nullable = false)
    private Integer hour;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** Jumlah frame yang sudah masuk untuk jam ini, dipakai untuk penamaan sequential frame_NNNNNN.jpg */
    @Column(name = "frame_count", nullable = false)
    private Integer frameCount = 0;

    /** Path relatif video hasil compile, contoh: cam1/2026-07-18/11.mp4. Null selagi masih RECORDING. */
    @Column(name = "video_path", length = 500)
    private String videoPath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = Status.RECORDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
