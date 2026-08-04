package com.homeserver.cctv.service;

import com.homeserver.cctv.entity.UnknownFace;
import com.homeserver.cctv.entity.UnknownFaceImage;
import com.homeserver.cctv.repository.UnknownFaceRepository;
import com.homeserver.cctv.repository.RecordingUnknownFaceRepository;
import com.homeserver.cctv.repository.UnknownFaceImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnknownFaceCleanupService {

    private final UnknownFaceRepository unknownFaceRepository;
    private final UnknownFaceImageRepository unknownFaceImageRepository;
    private final RecordingUnknownFaceRepository recordingUnknownFaceRepository;
    private final UnknownFaceService unknownFaceService;

    @Value("${cctv.unknown-face.retention-days:10}")
    private int retentionDays;

    @Value("${cctv.unknown-face.min-detection-count-to-keep:3}")
    private int minDetectionCountToKeep;

    /**
     * Jalan tiap hari jam 03:00. Hapus identitas unknown yang:
     * - sudah tidak terlihat lagi (lastSeenAt) lebih dari retentionDays hari
     * - detectionCount di bawah threshold (artinya cuman numpang lewat sekali/dua kali, 
     *   bukan orang yang sering muncul dan mungkin layak di investigasi/didaftarkan manual)
     * 
     * Hapus juga file gambar fisiknya di storage, bukan cuma row database.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupStaleUnknownFaces() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        List<UnknownFace> staleFaces = unknownFaceRepository
            .findAllByLastSeenAtBeforeAndDetectionCountLessThan(cutoff, minDetectionCountToKeep);

        if (staleFaces.isEmpty()) {
            log.info(">> LOG: Cleanup unknown face: tidak ada data yang perlu di hapus");
            return;
        }

        for (UnknownFace face : staleFaces) {
            unknownFaceService.deleteUnknownFace(face);
        }

        log.info(">> LOG: Cleanup unknown face selesai: {} identitas dihapus", staleFaces.size());
    }

    private boolean deleteFile(String pathStr) {
        try {
            Path path = Path.of(pathStr);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn(">> LOG: Failed delete file {}: {}", pathStr, e.getMessage());
            return false;
        }
    }
}