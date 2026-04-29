package com.group7.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:/app/uploads/photos}")
    private String uploadDir;

    @Value("${app.upload.attachments-dir:/app/uploads/attachments}")
    private String attachmentsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded photos at /api/uploads/photos/{filename}
        // Cache for 1 hour (photos rarely change, reduces repeat requests)
        registry.addResourceHandler("/api/uploads/photos/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());

        // Serve chat message attachments at /api/uploads/attachments/{filename}
        // Same caching policy as photos — attachments are immutable once stored
        // (UUID-based filenames; no overwrite path).
        registry.addResourceHandler("/api/uploads/attachments/**")
                .addResourceLocations("file:" + attachmentsDir + "/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
    }
}
