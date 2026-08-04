package com.devicemanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Web MVC minimale.
 * <p>
 * Les fichiers {@code /uploads/**} sont servis par {@link com.devicemanager.controller.UploadController}
 * (disque local + repli MySQL), et non via un {@code ResourceHandler} Spring, pour rester fiable
 * sur Render où le disque est éphémère.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
