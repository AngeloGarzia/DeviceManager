package com.devicemanager.service;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageOptimizationServiceTest {

    private static final Logger log = LoggerFactory.getLogger(ImageOptimizationServiceTest.class);

    private final ImageOptimizationService service = new ImageOptimizationService();

    @Test
    void optimize_resizesLargeImageToMaxDimension() throws Exception {
        log.info("Test resize image > {}px", ImageOptimizationService.MAX_DIMENSION);
        MockMultipartFile input = imageFile("large.png", 2400, 1800);

        MultipartFile optimized = service.optimize(input);
        BufferedImage result = ImageIO.read(optimized.getInputStream());

        assertThat(result.getWidth()).isLessThanOrEqualTo(ImageOptimizationService.MAX_DIMENSION);
        assertThat(result.getHeight()).isLessThanOrEqualTo(ImageOptimizationService.MAX_DIMENSION);
        assertThat(result.getWidth()).isEqualTo(900);
        assertThat(result.getHeight()).isEqualTo(675);
        assertThat(optimized.getContentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
        assertThat(optimized.getOriginalFilename()).endsWith(".jpg");
        assertThat(optimized.getSize()).isGreaterThan(0);
    }

    @Test
    void optimize_keepsSmallImageWithinBounds() throws Exception {
        MockMultipartFile input = imageFile("small.jpg", 800, 600);

        MultipartFile optimized = service.optimize(input);
        BufferedImage result = ImageIO.read(optimized.getInputStream());

        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(600);
        assertThat(optimized.getContentType()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
    }

    private MockMultipartFile imageFile(String name, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.ORANGE);
            g.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return new MockMultipartFile("photo", name, "image/png", baos.toByteArray());
    }
}
