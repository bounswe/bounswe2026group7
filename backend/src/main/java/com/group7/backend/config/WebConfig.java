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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded photos at /api/uploads/photos/{filename}
        // Cache for 1 hour (photos rarely change, reduces repeat requests).
        // Profile photos are intentionally public; chat attachments are not —
        // they are served through AttachmentDownloadController, which enforces
        // authentication and conversation-participant ACL.
        registry.addResourceHandler("/api/uploads/photos/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic());
    }
}
