package com.devicemanager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Les fichiers /uploads/** sont servis par {@link com.devicemanager.controller.UploadController}
 * (disque + MySQL), pas par un ResourceHandler Spring (fragile sur Render).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}
