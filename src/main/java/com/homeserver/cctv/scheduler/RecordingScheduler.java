package com.homeserver.cctv.scheduler;

import com.homeserver.cctv.service.RecordingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Setara routes/console.php di Laravel:
 *
 *   Schedule::command('recordings:compile')->everyFiveMinutes()->withoutOverlapping();
 *   Schedule::command('recordings:cleanup --days=7')->dailyAt('03:00')->withoutOverlapping();
 *
 * Bedanya: di Laravel ini butuh container terpisah ("scheduler") yang
 * menjalankan `php artisan schedule:work` terus-menerus. Di Spring Boot,
 * @Scheduled cukup jalan di proses aplikasi yang sama - tidak perlu
 * container tambahan. "withoutOverlapping" di Spring diganti dengan
 * memastikan method-nya tidak dipanggil bertumpuk (di sini otomatis aman
 * karena Spring default single-threaded scheduler, cron berikutnya baru
 * jalan setelah eksekusi sebelumnya selesai).
 */
@Component
public class RecordingScheduler {

    private final RecordingService recordingService;

    @Value("${cctv.retention-days}")
    private int retentionDays;

    public RecordingScheduler(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    // Tiap 5 menit, cek jam-jam yang sudah berakhir dan compile ke mp4
    @Scheduled(cron = "0 */5 * * * *")
    public void compileRecordings() {
        recordingService.compilePendingRecordings(false);
    }

    // Tiap hari jam 03:00 pagi, hapus rekaman + log lebih lama dari retentionDays
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldRecordings() {
        recordingService.cleanupOlderThan(retentionDays);
    }
}
