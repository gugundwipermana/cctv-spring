package com.homeserver.cctv.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.homeserver.cctv.entity.FaceEmbedding;
import com.homeserver.cctv.entity.KnownFace;
import com.homeserver.cctv.repository.FaceEmbeddingRepository;
import com.homeserver.cctv.repository.KnownFaceRepository;
import com.homeserver.cctv.service.KnownFaceService;

@Controller
@RequestMapping("/known-faces")
public class KnownFaceAdminController {
    
    private final KnownFaceRepository knownFaceRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final KnownFaceService knownFaceService;

    public KnownFaceAdminController(KnownFaceRepository knownFaceRepository, FaceEmbeddingRepository faceEmbeddingRepository, KnownFaceService knownFaceService) {
        this.knownFaceRepository = knownFaceRepository;
        this.faceEmbeddingRepository = faceEmbeddingRepository;
        this.knownFaceService = knownFaceService;
    }

    @GetMapping
    public String list(Model model) {
        return showPage(null, model);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        return showPage(id, model);
    }

    private String showPage(Long selectedId, Model model) {
        model.addAttribute("people", knownFaceRepository.findAllByOrderByNameAsc());
        
        if (selectedId != null) {
            KnownFace selected = knownFaceRepository.findById(selectedId).orElse(null);
            model.addAttribute("selected", selected);
            if (selected != null) {
                List<FaceEmbedding> photos = faceEmbeddingRepository.findByKnownFaceIdOrderByCreatedAtDesc(selectedId);
                model.addAttribute("photos", photos);
            }
        }
        return "known_faces";
    }

    @PostMapping
    public String create(@RequestParam String name,
                        @RequestParam(required = false) MultipartFile[] files) throws IOException {
        
        KnownFace kf = knownFaceService.getOrCreate(name);
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    try {
                        knownFaceService.addPhoto(kf, file.getBytes(), file.getOriginalFilename());
                    } catch (IOException e) {
                        // Handle exception (e.g., log it)
                    }
                }
            }
        }
        return "redirect:/known-faces/" + kf.getId();
    }

    @PostMapping("/{id}/photos")
    public String addPhotos(@PathVariable Long id, @RequestParam MultipartFile[] files) throws IOException {
        KnownFace kf = knownFaceRepository.findById(id).orElseThrow();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    knownFaceService.addPhoto(kf, file.getBytes(), file.getOriginalFilename());
                } catch (IOException e) {
                }
            }
        }
        return "redirect:/known-faces/" + id;
    }
        
    @PostMapping("/{id}/photos/{embeddingId}/delete")
    public String deletePhoto(@PathVariable Long id, @PathVariable Long embeddingId) {
        faceEmbeddingRepository.findById(embeddingId).ifPresent(knownFaceService::deletePhoto);
        return "redirect:/known-faces/" + id;
    }
}
