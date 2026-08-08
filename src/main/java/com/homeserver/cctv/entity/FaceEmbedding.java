package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Satu embedding wajah (hasil dari 1 foto registrasi). Beberapa
 * FaceEmbedding bisa menunjuk ke KnownFace yang sama, supaya 1 orang
 * bisa punya beberapa foto/variasi sudut untuk matching yang lebih akurat.
 */
@Entity
@Table(name = "face_embeddings")
@Getter
@Setter
@NoArgsConstructor
public class FaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "known_face_id", nullable = false)
    private KnownFace knownFace;

    @Column(name = "embedding_json", nullable = false, columnDefinition = "TEXT")
    private String embeddingJson;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
