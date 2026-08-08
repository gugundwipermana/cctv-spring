package com.homeserver.cctv.controller;

import com.homeserver.cctv.entity.KnownFace;
import com.homeserver.cctv.entity.UnknownFace;
import com.homeserver.cctv.repository.KnownFaceRepository;
import com.homeserver.cctv.repository.UnknownFaceImageRepository;
import com.homeserver.cctv.repository.UnknownFaceRepository;
import com.homeserver.cctv.service.KnownFaceService;
import com.homeserver.cctv.service.UnknownFaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class UnknownFaceAdminController {

    private final UnknownFaceRepository unknownFaceRepository;
    private final UnknownFaceImageRepository unknownFaceImageRepository;
    private final UnknownFaceService unknownFaceService;
    private final KnownFaceRepository knownFaceRepository;
    private final KnownFaceService knownFaceService;

    public UnknownFaceAdminController(
            UnknownFaceRepository unknownFaceRepository,
            UnknownFaceImageRepository unknownFaceImageRepository,
            UnknownFaceService unknownFaceService,
            KnownFaceRepository knownFaceRepository,
            KnownFaceService knownFaceService
    ) {
        this.unknownFaceRepository = unknownFaceRepository;
        this.unknownFaceImageRepository = unknownFaceImageRepository;
        this.unknownFaceService = unknownFaceService;
        this.knownFaceRepository = knownFaceRepository;
        this.knownFaceService = knownFaceService;
    }

    @GetMapping("/unknown-faces")
    public String list(Model model) {
        List<UnknownFace> faces = unknownFaceRepository.findAllByOrderByLastSeenAtDesc();

        List<AdminRow> rows = faces.stream()
                .map(f -> new AdminRow(
                        f.getId(),
                        f.getDetectionCount(),
                        f.getFirstSeenAt().toString(),
                        f.getLastSeenAt().toString(),
                        unknownFaceImageRepository.findAllByUnknownFaceIdOrderByCapturedAtAsc(f.getId())
                                .stream().map(img -> img.getImagePath()).collect(Collectors.toList())
                ))
                .collect(Collectors.toList());

        model.addAttribute("rows", rows);
        model.addAttribute("knownFaces", knownFaceRepository.findAllByOrderByNameAsc());
        return "unknown_faces";
    }

    @PostMapping("/unknown-faces/{id}/delete")
    public String delete(@PathVariable Long id) {
        unknownFaceRepository.findById(id).ifPresent(unknownFaceService::deleteUnknownFace);
        return "redirect:/unknown-faces";
    }

    // register as new known face
    @PostMapping("/unknown-faces/{id}/register-new")
    public String registerNew(@PathVariable Long id, @RequestParam String name) {
        unknownFaceRepository.findById(id).ifPresent(uf -> {
            KnownFace kf = knownFaceService.getOrCreate(name);
            unknownFaceService.promoteToKnownFace(uf, kf);
        });
        return "redirect:/unknown-faces";
    }

    // assign to existing known face
    @PostMapping("/unknown-faces/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam Long knownFaceId) {
        UnknownFace uf = unknownFaceRepository.findById(id).orElse(null);
        KnownFace kf = knownFaceRepository.findById(knownFaceId).orElse(null);
        if (uf != null && kf != null) {
            unknownFaceService.promoteToKnownFace(uf, kf);
        }
        return "redirect:/unknown-faces";
    }

    public record AdminRow(Long id, int detectionCount, String firstSeen, String lastSeen, List<String> imagePaths) {}
}