package com.homeserver.cctv.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "unknown_face_images")
@Getter
@Setter
public class UnknownFaceImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unknown_face_id", nullable = false)
    private UnknownFace unknownFace;

    @Column(name = "image_path", nullable = false)
    private String imagePath;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;
}