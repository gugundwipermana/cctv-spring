package com.homeserver.cctv.service;

import com.homeserver.cctv.entity.Recording;
import com.homeserver.cctv.repository.DetectionLogRepository;
import com.homeserver.cctv.repository.RecordingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Setara `RecordingsCompileCommand` + `RecordingsCleanupCommand` (artisan
 * command) di Laravel, plus bagian "get or create recording row" yang di
 * Laravel biasanya ada di CctvController::upload().
 */
@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final RecordingRepository recordingRepository;
    private final DetectionLogRepository detectionLogRepository;

    @Value("${cctv.storage.path}")
    private String storagePath;

    @Value("${cctv.frame-output-fps}")
    private int frameOutputFps;

    @Value("${cctv.keep-frames-after-compile}")
    private boolean keepFramesAfterCompile;

    public RecordingService(RecordingRepository recordingRepository, DetectionLogRepository detectionLogRepository) {
        this.recordingRepository = recordingRepository;
        this.detectionLogRepository = detectionLogRepository;
    }

    /** Ambil row Recording untuk jam & tanggal sekarang, bikin baru kalau belum ada. */
    public synchronized Recording getOrCreateCurrentRecording(String cameraId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDate date = now.toLocalDate();
        int hour = now.getHour();

        return recordingRepository.findByCameraIdAndRecordingDateAndHour(cameraId, date, hour)
                .orElseGet(() -> {
                    Recording r = new Recording();
                    r.setCameraId(cameraId);
                    r.setRecordingDate(date);
                    r.setHour(hour);
                    r.setStatus(Recording.Status.RECORDING);
                    r.setFrameCount(0);
                    return recordingRepository.save(r);
                });
    }

    public synchronized int incrementFrameCount(Recording recording) {
        recording.setFrameCount(recording.getFrameCount() + 1);
        recordingRepository.save(recording);
        return recording.getFrameCount();
    }

    /**
     * Dipanggil scheduler tiap 5 menit (setara Schedule::command('recordings:compile')
     * ->everyFiveMinutes()). Cari semua Recording yang masih RECORDING tapi
     * jamnya sudah lewat, lalu compile jadi mp4 pakai ffmpeg.
     */
    public void compilePendingRecordings(boolean force) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Recording> pending = recordingRepository.findAllByStatus(Recording.Status.RECORDING);

        for (Recording recording : pending) {
            boolean hourHasEnded = recording.getRecordingDate().isBefore(now.toLocalDate())
                    || (recording.getRecordingDate().isEqual(now.toLocalDate()) && recording.getHour() < now.getHour());

            if (hourHasEnded || force) {
                try {
                    compileOne(recording);
                } catch (Exception e) {
                    log.error("Gagal compile recording id={} camera={} tanggal={} jam={}: {}",
                            recording.getId(), recording.getCameraId(), recording.getRecordingDate(),
                            recording.getHour(), e.getMessage(), e);
                }
            }
        }
    }

    private void compileOne(Recording recording) throws IOException, InterruptedException {
        Path framesDir = Path.of(storagePath, "frames", recording.getCameraId(),
                recording.getRecordingDate().toString(), String.format("%02d", recording.getHour()));

        if (!Files.exists(framesDir) || isEmptyDir(framesDir)) {
            log.warn("Tidak ada frame untuk recording id={}, skip compile", recording.getId());
            recording.setStatus(Recording.Status.COMPILED);
            recordingRepository.save(recording);
            return;
        }

        Path recordingsDir = Path.of(storagePath, "recordings", recording.getCameraId(),
                recording.getRecordingDate().toString());
        Files.createDirectories(recordingsDir);
        Path outputFile = recordingsDir.resolve(recording.getHour() + ".mp4");

        // Setara: ffmpeg -framerate 10 -pattern_type glob -i 'frame_*.jpg' -c:v libx264 -pix_fmt yuv420p output.mp4
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y",
                "-framerate", String.valueOf(frameOutputFps),
                "-pattern_type", "glob",
                "-i", "frame_*.jpg",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                outputFile.toAbsolutePath().toString()
        );
        pb.directory(framesDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // Buang output ffmpeg (kalau mau debug, bisa di-log di sini)
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("ffmpeg keluar dengan exit code " + exitCode);
        }

        String relativeVideoPath = Path.of(recording.getCameraId(), recording.getRecordingDate().toString(),
                recording.getHour() + ".mp4").toString();

        recording.setStatus(Recording.Status.COMPILED);
        recording.setVideoPath(relativeVideoPath);
        recordingRepository.save(recording);

        if (!keepFramesAfterCompile) {
            deleteDirectoryRecursively(framesDir);
        }

        log.info("Recording id={} berhasil di-compile -> {}", recording.getId(), relativeVideoPath);
    }

    /**
     * Dipanggil scheduler harian jam 03:00 (setara Schedule::command('recordings:cleanup --days=7')
     * ->dailyAt('03:00')). Hapus row + file fisik yang lebih lama dari N hari.
     */
    public void cleanupOlderThan(int days) {
        LocalDate cutoffDate = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        Instant cutoffInstant = Instant.now().minusSeconds((long) days * 24 * 3600);

        List<Recording> old = recordingRepository.findAllByRecordingDateBefore(cutoffDate);
        for (Recording recording : old) {
            try {
                if (recording.getVideoPath() != null) {
                    Files.deleteIfExists(Path.of(storagePath, "recordings", recording.getVideoPath()));
                }
                Path framesDir = Path.of(storagePath, "frames", recording.getCameraId(),
                        recording.getRecordingDate().toString(), String.format("%02d", recording.getHour()));
                deleteDirectoryRecursively(framesDir);
            } catch (IOException e) {
                log.warn("Gagal hapus file untuk recording id={}: {}", recording.getId(), e.getMessage());
            }
        }
        recordingRepository.deleteAll(old);

        detectionLogRepository.deleteAll(detectionLogRepository.findAllByCreatedAtBefore(cutoffInstant));

        log.info("Cleanup selesai: {} recording lama & log terkait dihapus (lebih lama dari {} hari)",
                old.size(), days);
    }

    private boolean isEmptyDir(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Gagal hapus {}: {}", p, e.getMessage());
                }
            });
        }
    }
}
