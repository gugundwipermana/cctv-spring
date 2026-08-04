package com.homeserver.cctv.service;

import com.homeserver.cctv.entity.Recording;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simpan byte gambar (yang sudah dianotasi kotak wajah) ke folder
 * frames/{cameraId}/{tanggal}/{jam}/frame_NNNNNN.jpg - struktur ini yang
 * nanti dibaca RecordingService.compileOne() lewat pattern glob 'frame_*.jpg'.
 */
@Service
public class FrameStorageService {

    private final RecordingService recordingService;

    @Value("${cctv.storage.path}")
    private String storagePath;

    public FrameStorageService(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    /**
     * @return path relatif frame yang baru disimpan, contoh: cam1/2026-07-18/11/frame_000281.jpg
     *         Path ini yang dipakai sebagai "snapshot" di DetectionLog & response /api/upload.
     */
    public String saveFrame(String cameraId, byte[] annotatedImageBytes) throws IOException {
        Recording recording = recordingService.getOrCreateCurrentRecording(cameraId);
        int sequence = recordingService.incrementFrameCount(recording);

        Path dir = Path.of(storagePath, "frames", cameraId,
                recording.getRecordingDate().toString(), String.format("%02d", recording.getHour()));
        Files.createDirectories(dir);

        String filename = String.format("frame_%06d.jpg", sequence);
        Path fullPath = dir.resolve(filename);
        Files.write(fullPath, annotatedImageBytes);

        return Path.of(cameraId, recording.getRecordingDate().toString(),
                String.format("%02d", recording.getHour()), filename).toString();
    }
}
