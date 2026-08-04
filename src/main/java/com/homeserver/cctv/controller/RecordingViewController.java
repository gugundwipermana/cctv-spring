package com.homeserver.cctv.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeserver.cctv.entity.DetectionLog;
import com.homeserver.cctv.entity.Recording;
import com.homeserver.cctv.entity.RecordingUnknownFace;
import com.homeserver.cctv.entity.UnknownFace;
import com.homeserver.cctv.entity.UnknownFaceImage;
import com.homeserver.cctv.repository.DetectionLogRepository;
import com.homeserver.cctv.repository.RecordingRepository;
import com.homeserver.cctv.repository.RecordingUnknownFaceRepository;
import com.homeserver.cctv.repository.UnknownFaceImageRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Setara routes/web.php:
 *   Route::get('/', [RecordingViewController::class, 'index']);
 *   Route::get('/recordings/{date}', [RecordingViewController::class, 'show'])->where('date', '\d{4}-\d{2}-\d{2}');
 */
@Controller
public class RecordingViewController {

    private static final ZoneId LOCAL_ZONE_ID = ZoneId.of("Asia/Jakarta"); // Ganti sesuai zona waktu lokal kamu

    private final RecordingRepository recordingRepository;
    private final DetectionLogRepository detectionLogRepository;
    private final RecordingUnknownFaceRepository recordingUnknownFaceRepository;
    private final UnknownFaceImageRepository unknownFaceImageRepository;
    private final ObjectMapper objectMapper;

    public RecordingViewController(
            RecordingRepository recordingRepository,
            DetectionLogRepository detectionLogRepository,
            RecordingUnknownFaceRepository recordingUnknownFaceRepository,
            UnknownFaceImageRepository unknownFaceImageRepository,
            ObjectMapper objectMapper
    ) {
        this.recordingRepository = recordingRepository;
        this.detectionLogRepository = detectionLogRepository;
        this.recordingUnknownFaceRepository = recordingUnknownFaceRepository;
        this.unknownFaceImageRepository = unknownFaceImageRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- GET / : daftar tanggal yang punya rekaman ----------
    @GetMapping("/")
    public String index(Model model) {
        List<LocalDate> dates = recordingRepository.findDistinctRecordingDates();
        model.addAttribute("dates", dates);
        return "index";
    }

    // ---------- GET /recordings/{date} : daftar 24 jam untuk tanggal itu ----------
    @GetMapping("/recordings/{date}")
    public String show(@PathVariable("date") String dateStr, Model model) {
        LocalDate date = LocalDate.parse(dateStr);
        List<Recording> recordings = recordingRepository.findAllByRecordingDateOrderByHourAsc(date);

        List<RecordingRow> rows = recordings.stream()
                .map(r -> {

                    // convert hour UTC ke local time (misal WIB) untuk ditampilkan di UI
                    LocalDateTime utcDateTime = r.getRecordingDate().atTime(r.getHour(), 0); 
                    LocalDateTime localDateTime = utcDateTime.atZone(ZoneOffset.UTC)
                        .withZoneSameInstant(LOCAL_ZONE_ID)
                        .toLocalDateTime();
                    
                    return new RecordingRow(
                        r.getCameraId(),
                        localDateTime.getHour(),
                        r.getStatus().name(),
                        r.getVideoPath(),
                        namesDetectedInHour(r),
                        unknownFacesForRecording(r)
                    );
                })
                .collect(Collectors.toList());

        model.addAttribute("date", dateStr);
        model.addAttribute("rows", rows);
        return "show";
    }

    /** Ambil daftar nama unik yang terdeteksi selama jam tersebut, dari detection_logs. */
    private Set<String> namesDetectedInHour(Recording recording) {
        var startOfHour = recording.getRecordingDate().atTime(recording.getHour(), 0).toInstant(ZoneOffset.UTC);
        var endOfHour = startOfHour.plusSeconds(3600);

        List<DetectionLog> logs = detectionLogRepository.findAllByCameraIdAndCreatedAtBetween(
                recording.getCameraId(), startOfHour, endOfHour);

        Set<String> names = new LinkedHashSet<>();
        for (DetectionLog log : logs) {
            try {
                JsonNode matches = objectMapper.readTree(log.getMatchesJson());
                for (JsonNode match : matches) {
                    names.add(match.get("name").asText());
                }
            } catch (Exception ignored) {
                // matches_json tidak valid, skip
            }
        }
        return names;
    }

    public record UnknownFaceSummary(
        Long id,
        int detectionCount,
        String firstSeen,
        String lastSeen,
        List<String> imagePaths
    ) {}

    /**
     * Ambil semua inknown face yang muncul dalam recording (jam) ini
     * beserta galery fotonya
     * 
     * @param recording
     * @return
     */
    private List<UnknownFaceSummary> unknownFacesForRecording(Recording recording) {
        List<RecordingUnknownFace> links = recordingUnknownFaceRepository.findAllByRecordingId(recording.getId());

        return links.stream()
            .map(link -> {
                UnknownFace face = link.getUnknownFace();
                List<String> imagePaths = unknownFaceImageRepository
                        .findAllByUnknownFaceIdOrderByCapturedAtAsc(face.getId())
                        .stream()
                        .map(UnknownFaceImage::getImagePath)
                        .collect(Collectors.toList());
                
                return new UnknownFaceSummary(
                    face.getId(),
                    face.getDetectionCount(),
                    link.getFirstSeenAt().toLocalTime().withNano(0).toString(),
                    link.getLastSeenAt().toLocalTime().withNano(0).toString(),
                    imagePaths
                );
            })
            .collect(Collectors.toList());
    }

    /** Baris data untuk template Thymeleaf (1 baris = 1 jam rekaman) */
    public record RecordingRow(
        String cameraId, 
        int hour,
        String status, 
        String videoPath, 
        Set<String> namesDetected,
        List<UnknownFaceSummary> unknownFaces
    ) {
        public int knownFaceCount() {
            return namesDetected.size();
        }

        public int unknownFaceCount() {
            return unknownFaces.size();
        }
    }
}
