package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.RecordingUnknownFace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecordingUnknownFaceRepository extends JpaRepository<RecordingUnknownFace, Long> {

    Optional<RecordingUnknownFace> findByRecordingIdAndUnknownFaceId(Long recordingId, Long unknownFaceId);

    List<RecordingUnknownFace> findAllByRecordingId(Long recordingId);

    // for Cleanup
    void deleteAllByUnknownFaceId(Long unknownFaceId);
}