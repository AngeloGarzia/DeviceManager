package com.devicemanager.service;

import com.devicemanager.security.DeepFileContentValidator;
import com.devicemanager.security.FileMagicBytesValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

/**
 * Optimisation des photos de pièces détachées avant stockage.
 * <p>
 * Normalise en JPEG {@link #TARGET_WIDTH}×{@link #TARGET_HEIGHT} (ratio 4:3) :
 * recadrage centré si besoin, puis mise à l'échelle fixe.
 */
@Service
@Slf4j
public class ImageOptimizationService {

    /** Largeur fixe des photos stockées (ratio 4:3). */
    public static final int TARGET_WIDTH = 800;
    /** Hauteur fixe des photos stockées (ratio 4:3). */
    public static final int TARGET_HEIGHT = 600;
    private static final float JPEG_QUALITY = 0.82f;

    /**
     * Recadre / redimensionne et compresse une image en JPEG 800×600 (4:3).
     *
     * @param file image source
     * @return fichier multipart en mémoire (JPEG)
     * @throws org.springframework.web.server.ResponseStatusException {@code 400} si image absente ou illisible
     */
    public MultipartFile optimize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La photo est obligatoire");
        }
        try {
            byte[] rawBytes = file.getBytes();
            FileMagicBytesValidator.validateImageMagicBytes(rawBytes);
            String declared = file.getContentType();
            DeepFileContentValidator.validateImage(rawBytes,
                    declared != null && declared.toLowerCase(Locale.ROOT).startsWith("image/") ? declared : null);
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo illisible ou format non pris en charge");
            }
            BufferedImage normalized = normalizeToFixedFourByThree(source);
            byte[] jpegBytes = encodeJpeg(normalized);

            String originalName = file.getOriginalFilename();
            String filename = toJpegFilename(originalName);

            log.info(
                    "Image optimisée: {}x{} → {}x{} ({} → {} octets)",
                    source.getWidth(),
                    source.getHeight(),
                    normalized.getWidth(),
                    normalized.getHeight(),
                    file.getSize(),
                    jpegBytes.length
            );

            String partName = file.getName();
            return new OptimizedMultipartFile(
                    partName.isBlank() ? "photo" : partName,
                    filename,
                    MediaType.IMAGE_JPEG_VALUE,
                    jpegBytes
            );
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de préparer la photo. Réessayez avec une autre image.", ex);
        }
    }

    /**
     * Recadre au centre en 4:3 puis met à l'échelle exacte {@value #TARGET_WIDTH}×{@value #TARGET_HEIGHT}.
     */
    BufferedImage normalizeToFixedFourByThree(BufferedImage source) {
        BufferedImage rgb = toRgb(source);
        BufferedImage cropped = centerCropToAspect(rgb, TARGET_WIDTH, TARGET_HEIGHT);
        if (cropped.getWidth() == TARGET_WIDTH && cropped.getHeight() == TARGET_HEIGHT) {
            return cropped;
        }
        BufferedImage target = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(cropped, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private BufferedImage centerCropToAspect(BufferedImage source, int aspectW, int aspectH) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 1 || height < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo illisible ou format non pris en charge");
        }
        double targetAspect = (double) aspectW / aspectH;
        double sourceAspect = (double) width / height;
        int cropW = width;
        int cropH = height;
        int x = 0;
        int y = 0;
        if (sourceAspect > targetAspect) {
            cropW = Math.max(1, (int) Math.round(height * targetAspect));
            x = Math.max(0, (width - cropW) / 2);
        } else if (sourceAspect < targetAspect) {
            cropH = Math.max(1, (int) Math.round(width / targetAspect));
            y = Math.max(0, (height - cropH) / 2);
        }
        cropW = Math.min(cropW, width - x);
        cropH = Math.min(cropH, height - y);
        return source.getSubimage(x, y, cropW, cropH);
    }

    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("Aucun encodeur JPEG disponible");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private String toJpegFilename(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "photo.jpg";
        }
        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String sanitized = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            sanitized = "photo";
        }
        return sanitized + ".jpg";
    }

    /**
     * MultipartFile en mémoire après optimisation.
     */
    static final class OptimizedMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        OptimizedMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = Arrays.copyOf(content, content.length);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return Arrays.copyOf(content, content.length);
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
