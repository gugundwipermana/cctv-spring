package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orang yang wajahnya sudah didaftarkan (setara tabel `known_faces` di Laravel).
 *
 * PERUBAHAN: embedding sekarang TIDAK disimpan langsung di sini lagi.
 * 1 orang (KnownFace) bisa punya BANYAK foto/embedding (FaceEmbedding),
 * supaya matching lebih akurat untuk variasi sudut/pencahayaan (mis. kondisi
 * CCTV outdoor yang jauh berbeda dari foto selfie waktu registrasi).
 */
@Entity
@Table(name = "known_faces")
@Getter
@Setter
@NoArgsConstructor
public class KnownFace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "knownFace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FaceEmbedding> embeddings = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
