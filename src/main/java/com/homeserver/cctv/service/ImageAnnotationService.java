package com.homeserver.cctv.service;

import com.homeserver.cctv.dto.FaceDetectionResponse;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Setara fungsi gambar kotak + label di Laravel yang pakai library GD.
 * Di Java, cara paling standar adalah lewat Java2D (Graphics2D) yang sudah
 * built-in di JDK, tidak perlu dependency tambahan.
 *
 * Warna: hijau = wajah dikenali, merah = unknown - sama seperti disebutkan
 * di README Laravel.
 */
@Service
public class ImageAnnotationService {

    private static final Color GREEN = new Color(0, 200, 0);
    private static final Color RED = new Color(220, 0, 0);

    /**
     * @param imageBytes gambar asli dari kamera
     * @param faces      hasil deteksi dari face-service
     * @param nameForFace fungsi yang mengembalikan nama match untuk index wajah ke-i
     *                    (atau Optional.empty() kalau unknown) - dioper dari CctvController
     *                    supaya service ini tidak perlu tahu soal FaceMatchingService.
     */
    public byte[] drawBoundingBoxes(
            byte[] imageBytes,
            List<FaceDetectionResponse.DetectedFace> faces,
            java.util.function.IntFunction<Optional<String>> nameForFace
    ) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(3f));
        g.setFont(new Font("SansSerif", Font.BOLD, 18));

        for (int i = 0; i < faces.size(); i++) {
            FaceDetectionResponse.DetectedFace face = faces.get(i);
            List<Double> bbox = face.bbox(); // [x1, y1, x2, y2]
            int x1 = bbox.get(0).intValue();
            int y1 = bbox.get(1).intValue();
            int x2 = bbox.get(2).intValue();
            int y2 = bbox.get(3).intValue();

            Optional<String> name = nameForFace.apply(i);
            Color color = name.isPresent() ? GREEN : RED;
            String label = name.orElse("unknown");

            g.setColor(color);
            g.drawRect(x1, y1, x2 - x1, y2 - y1);

            // Background kecil di belakang teks label biar kebaca jelas
            int textWidth = g.getFontMetrics().stringWidth(label) + 8;
            g.fillRect(x1, Math.max(0, y1 - 22), textWidth, 22);
            g.setColor(Color.WHITE);
            g.drawString(label, x1 + 4, Math.max(14, y1 - 6));
        }

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    public byte[] cropFace(
        byte[] originalImageBytes, 
        List<Double> bbox
    ) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalImageBytes));

        int x1 = bbox.get(0).intValue();
        int y1 = bbox.get(1).intValue();
        int x2 = bbox.get(2).intValue();
        int y2 = bbox.get(3).intValue();
        
        // clamp supaya tidak keluar batas gambar
        x1 = Math.max(0, x1);
        y1 = Math.max(0, y1);
        x2 = Math.min(original.getWidth(), x2);
        y2 = Math.min(original.getHeight(), y2);

        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) {
            throw new IOException(">> LOG: Bbox tidak valid untuk crop: " + bbox);
        }

        BufferedImage cropped = original.getSubimage(x1, y1, width, height);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(cropped, "jpg", baos);
        return baos.toByteArray();
    }
}
