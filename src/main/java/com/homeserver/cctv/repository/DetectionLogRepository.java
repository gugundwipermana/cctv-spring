package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.DetectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface DetectionLogRepository extends JpaRepository<DetectionLog, Long> {

    // Setara `?limit=20` di GET /api/logs, diurutkan dari yang terbaru
    List<DetectionLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Dipakai scheduler cleanup harian (hapus log lebih lama dari N hari)
    List<DetectionLog> findAllByCreatedAtBefore(Instant cutoff);

    // Dipakai halaman web show(date) -> daftar nama yang terdeteksi di jam tertentu
    List<DetectionLog> findAllByCameraIdAndCreatedAtBetween(String cameraId, Instant start, Instant end);
}
