package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.FaceEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FaceEmbeddingRepository extends JpaRepository<FaceEmbedding, Long> {

    // JOIN FETCH supaya saat looping semua embedding untuk matching
    // (lihat FaceMatchingService), nama KnownFace-nya ikut ke-load sekaligus
    // - tidak query berulang ke DB satu-satu per embedding (N+1 problem).
    @Query("SELECT fe FROM FaceEmbedding fe JOIN FETCH fe.knownFace")
    List<FaceEmbedding> findAllWithKnownFace();

    List<FaceEmbedding> findByKnownFaceId(Long knownFaceId);
}
