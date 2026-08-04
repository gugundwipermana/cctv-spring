package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.KnownFace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnownFaceRepository extends JpaRepository<KnownFace, Long> {

    // Dipakai saat register-face: kalau nama sudah pernah didaftarkan,
    // foto baru ditambahkan ke orang yang sama, bukan bikin entry duplikat.
    Optional<KnownFace> findByNameIgnoreCase(String name);
}
