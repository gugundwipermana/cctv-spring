package com.homeserver.cctv.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homeserver.cctv.dto.FaceDetectionResponse;
import com.homeserver.cctv.dto.FaceMatch;
import com.homeserver.cctv.entity.DetectionLog;
import com.homeserver.cctv.entity.KnownFace;
import com.homeserver.cctv.entity.Recording;
import com.homeserver.cctv.repository.DetectionLogRepository;
import com.homeserver.cctv.repository.KnownFaceRepository;
import com.homeserver.cctv.service.FaceMatchingService;
import com.homeserver.cctv.service.FaceServiceClient;
import com.homeserver.cctv.service.FrameStorageService;
import com.homeserver.cctv.service.ImageAnnotationService;
import com.homeserver.cctv.service.RecordingService;
import com.homeserver.cctv.service.UnknownFaceService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import com.homeserver.cctv.entity.FaceEmbedding;
import com.homeserver.cctv.repository.FaceEmbeddingRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setara app/Http/Controllers/CctvController.php di Laravel.
 * Route-nya persis mengikuti routes/api.php yang kamu kasih:
 *   POST   /api/upload
 *   POST   /api/register-face
 *   GET    /api/faces
 *   DELETE /api/faces/{id}
 *   GET    /api/logs
 *   GET    /api/snapshot/{filename}
 */
@RestController
@RequestMapping("/api")
public class CctvController {

    private static final Logger log = LoggerFactory.getLogger(CctvController.class);
    private final FaceServiceClient faceServiceClient;
    private final FaceMatchingService faceMatchingService;
    private final ImageAnnotationService imageAnnotationService;
    private final FrameStorageService frameStorageService;
    private final RecordingService recordingService;
    private final UnknownFaceService unknownFaceService;
    private final KnownFaceRepository knownFaceRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final DetectionLogRepository detectionLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${cctv.storage.path}")
    private String storagePath;

    public CctvController(
            FaceServiceClient faceServiceClient,
            FaceMatchingService faceMatchingService,
            ImageAnnotationService imageAnnotationService,
            FrameStorageService frameStorageService,
            RecordingService recordingService,
            UnknownFaceService unknownFaceService,
            KnownFaceRepository knownFaceRepository,
            FaceEmbeddingRepository faceEmbeddingRepository,
            DetectionLogRepository detectionLogRepository,
            ObjectMapper objectMapper
    ) {
        this.faceServiceClient = faceServiceClient;
        this.faceMatchingService = faceMatchingService;
        this.imageAnnotationService = imageAnnotationService;
        this.frameStorageService = frameStorageService;
        this.recordingService = recordingService;
        this.unknownFaceService = unknownFaceService;
        this.knownFaceRepository = knownFaceRepository;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.detectionLogRepository = detectionLogRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- POST /api/upload (dipanggil ESP32-CAM) ----------
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
            @RequestParam("camera_id") String cameraId,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            byte[] imageBytes = file.getBytes();

            FaceDetectionResponse detection = faceServiceClient.detectFaces(imageBytes, file.getOriginalFilename());
            List<FaceDetectionResponse.DetectedFace> faces =
                    detection != null && detection.faces() != null ? detection.faces() : List.of();

            // Cocokkan tiap wajah yang terdeteksi terhadap known_faces
            List<Optional<FaceMatch>> matchResults = faces.stream()
                    .map(f -> faceMatchingService.findBestMatch(f.embedding()))
                    .collect(Collectors.toList());

            // ===== UNKNOWN FACE: proses wajah yang belum terdaftar =====
            LocalDateTime detectedAt = LocalDateTime.now(ZoneOffset.UTC);
            boolean hasUnknown = matchResults.stream().anyMatch(Optional::isEmpty);
            Recording currentRecording = hasUnknown
                ? recordingService.getOrCreateCurrentRecording(cameraId)
                : null;
            
            for (int i = 0; i < faces.size(); i++) {
                if (matchResults.get(i).isEmpty()) {
                    try {
                        byte[] croppedFace = imageAnnotationService.cropFace(imageBytes, faces.get(i).bbox());
                        unknownFaceService.handleUnknownFace(faces.get(i).embedding(), croppedFace, detectedAt, currentRecording);
                    } catch (IOException e) {
                        log.warn(">> LOG: Gagal crop/simpan unknown face untuk camera={}: {}", cameraId, e.getMessage());
                    }
                }
            }
            // ===== END UNKNOWN FACE =====

            byte[] annotated = faces.isEmpty()
                    ? imageBytes
                    : imageAnnotationService.drawBoundingBoxes(
                        imageBytes, faces, i -> matchResults.get(i).map(FaceMatch::name));

            String snapshotPath = frameStorageService.saveFrame(cameraId, annotated);

            List<FaceMatch> matches = matchResults.stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            DetectionLog logEntry = new DetectionLog();
            logEntry.setCameraId(cameraId);
            logEntry.setSnapshotPath(snapshotPath);
            logEntry.setFacesDetected(faces.size());
            logEntry.setMatchesJson(objectMapper.writeValueAsString(matches));
            detectionLogRepository.save(logEntry);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("faces_detected", faces.size());
            response.put("matches", matches);
            response.put("snapshot", snapshotPath);

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Gagal proses upload: " + e.getMessage()));
        }
    }

    // ---------- POST /api/register-face ----------
    // PERUBAHAN: sekarang terima BANYAK file sekaligus (field "files", bukan
    // "file"). Kalau nama sudah pernah didaftarkan, foto baru ditambahkan ke
    // orang yang sama (bukan bikin entry duplikat).
    //
    // Contoh pemanggilan via curl:
    //   curl -F "name=Gugun" -F "files=@foto1.jpg" -F "files=@foto2.jpg" -F "files=@foto3.jpg" \
    //        http://localhost:8080/api/register-face
    @PostMapping(value = "/register-face", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<?> registerFace(
            @RequestParam("name") String name,
            @RequestParam("files") MultipartFile[] files
    ) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Minimal 1 foto wajib diupload"));
        }

        KnownFace knownFace = knownFaceRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    KnownFace kf = new KnownFace();
                    kf.setName(name);
                    return knownFaceRepository.save(kf);
                });

        List<Map<String, Object>> details = new ArrayList<>();
        int successCount = 0;

        for (MultipartFile file : files) {
            try {
                byte[] imageBytes = file.getBytes();
                FaceDetectionResponse detection = faceServiceClient.detectFaces(imageBytes, file.getOriginalFilename());

                if (detection == null || detection.faces() == null || detection.faces().isEmpty()) {
                    details.add(Map.of("file", file.getOriginalFilename(), "status", "gagal", "error", "Tidak ada wajah terdeteksi"));
                    continue;
                }
                if (detection.faces().size() > 1) {
                    details.add(Map.of("file", file.getOriginalFilename(), "status", "gagal", "error", "Lebih dari 1 wajah terdeteksi di foto ini"));
                    continue;
                }

                List<Double> embedding = detection.faces().get(0).embedding();
                log.info(">> LOG: DEBUG embedding size={}, isi={}", embedding.size(), embedding);

                FaceEmbedding faceEmbedding = new FaceEmbedding();
                faceEmbedding.setKnownFace(knownFace);
                faceEmbedding.setEmbeddingJson(faceMatchingService.toEmbeddingJson(embedding));
                faceEmbeddingRepository.save(faceEmbedding);

                details.add(Map.of("file", file.getOriginalFilename(), "status", "sukses"));
                successCount++;
            } catch (IOException e) {
                details.add(Map.of("file", file.getOriginalFilename(), "status", "gagal", "error", e.getMessage()));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", knownFace.getId());
        response.put("name", knownFace.getName());
        response.put("photos_added", successCount);
        response.put("photos_failed", files.length - successCount);
        response.put("details", details);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ---------- GET /api/faces ----------
    // PERUBAHAN: tambah "photo_count" supaya kelihatan berapa foto per orang
    @GetMapping("/faces")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFaces() {
        return knownFaceRepository.findAll().stream()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("name", f.getName());
                    m.put("photo_count", f.getEmbeddings().size());
                    m.put("created_at", f.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ---------- DELETE /api/faces/{id} ----------
    @DeleteMapping("/faces/{id}")
    public ResponseEntity<?> deleteFace(@PathVariable Long id) {
        if (!knownFaceRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        knownFaceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- GET /api/logs?limit=20 ----------
    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(defaultValue = "20") int limit) {
        return detectionLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toLogResponse)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toLogResponse(DetectionLog log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("camera_id", log.getCameraId());
        m.put("snapshot", log.getSnapshotPath());
        m.put("faces_detected", log.getFacesDetected());
        try {
            m.put("matches", objectMapper.readTree(log.getMatchesJson()));
        } catch (IOException e) {
            m.put("matches", List.of());
        }
        m.put("created_at", log.getCreatedAt());
        return m;
    }

    // ---------- GET /api/snapshot/{filename} ----------
    // Dipetakan sebagai wildcard "**" karena path frame kita hierarkis
    // (cameraId/tanggal/jam/frame_NNNNNN.jpg), bukan flat filename.
    @GetMapping("/snapshot/**")
    public ResponseEntity<Resource> snapshot(HttpServletRequest request) throws IOException {
        String fullPath = request.getRequestURI();
        String marker = "/api/snapshot/";
        String relativePath = UriUtils.decode(fullPath.substring(fullPath.indexOf(marker) + marker.length()), StandardCharsets.UTF_8);

        Path filePath = Path.of(storagePath, "frames", relativePath).normalize();
        Path framesRoot = Path.of(storagePath, "frames").normalize();

        // Cegah path traversal (../../dst) - file yang diminta wajib tetap di dalam folder frames
        if (!filePath.startsWith(framesRoot) || !Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] bytes = Files.readAllBytes(filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(new ByteArrayResource(bytes));
    }
}
