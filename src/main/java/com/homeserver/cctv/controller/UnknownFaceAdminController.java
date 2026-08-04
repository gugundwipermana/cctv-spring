package com.homeserver.cctv.controller;

import com.homeserver.cctv.entity.UnknownFace;
import com.homeserver.cctv.repository.UnknownFaceImageRepository;
import com.homeserver.cctv.repository.UnknownFaceRepository;
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

    public UnknownFaceAdminController(
            UnknownFaceRepository unknownFaceRepository,
            UnknownFaceImageRepository unknownFaceImageRepository,
            UnknownFaceService unknownFaceService
    ) {
        this.unknownFaceRepository = unknownFaceRepository;
        this.unknownFaceImageRepository = unknownFaceImageRepository;
        this.unknownFaceService = unknownFaceService;
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
        return "unknown_faces";
    }

    @PostMapping("/unknown-faces/{id}/delete")
    public String delete(@PathVariable Long id) {
        unknownFaceRepository.findById(id).ifPresent(unknownFaceService::deleteUnknownFace);
        return "redirect:/unknown-faces";
    }

    public record AdminRow(Long id, int detectionCount, String firstSeen, String lastSeen, List<String> imagePaths) {}
}