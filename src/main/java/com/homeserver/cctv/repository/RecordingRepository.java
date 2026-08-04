package com.homeserver.cctv.repository;

import com.homeserver.cctv.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    Optional<Recording> findByCameraIdAndRecordingDateAndHour(String cameraId, LocalDate date, Integer hour);

    // Dipakai scheduler compile: cari semua yang masih RECORDING (dicek satu-satu
    // apakah jamnya sudah lewat di service layer, karena perbandingan tanggal+jam
    // lebih jelas ditulis di Java daripada di JPQL)
    List<Recording> findAllByStatus(Recording.Status status);

    // Dipakai halaman web index() -> daftar tanggal yang punya rekaman
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT r.recordingDate FROM Recording r ORDER BY r.recordingDate DESC"
    )
    List<LocalDate> findDistinctRecordingDates();

    // Dipakai halaman web show(date) -> daftar jam untuk 1 tanggal
    List<Recording> findAllByRecordingDateOrderByHourAsc(LocalDate date);

    // Dipakai scheduler cleanup harian
    List<Recording> findAllByRecordingDateBefore(LocalDate cutoff);
}
