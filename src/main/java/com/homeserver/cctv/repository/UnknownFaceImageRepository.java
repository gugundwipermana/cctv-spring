package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.UnknownFaceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnknownFaceImageRepository extends JpaRepository<UnknownFaceImage, Long> {

    List<UnknownFaceImage> findAllByUnknownFaceIdOrderByCapturedAtAsc(Long unknownFaceId);
    
    Long countByUnknownFaceId(Long unknownFaceId);
}