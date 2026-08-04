package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "unknown_faces")
@Getter
@Setter
public class UnknownFace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "embedding_json", columnDefinition = "TEXT", nullable = false)
    private String embeddingJson;

    @Column(name = "representative_image_path")
    private String representativeImagePath;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "detection_count", nullable = false)
    private int detectionCount = 1;

    @OneToMany(mappedBy = "unknownFace", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UnknownFaceImage> images = new ArrayList<>();
}