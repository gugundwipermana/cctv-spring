package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.UnknownFace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface UnknownFaceRepository extends JpaRepository<UnknownFace, Long> {

    List<UnknownFace> findAllByLastSeenAtBeforeAndDetectionCountLessThan(LocalDateTime cutoff, int minDetectionCount);
}
