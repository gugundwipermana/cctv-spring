package com.homeserver.cctv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Memetakan folder fisik {storagePath}/recordings ke URL /media/recordings/**,
 * supaya tag <video> di halaman Thymeleaf bisa langsung load file mp4-nya.
 * Ini setara symlink public/storage -> storage/app/public di Laravel.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cctv.storage.path}")
    private String storagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/recordings/**")
                .addResourceLocations("file:" + storagePath + "/recordings/");

        registry.addResourceHandler("/media/unknown_faces/**")
                .addResourceLocations("file:" + storagePath + "/unknown_faces/");

        registry.addResourceHandler("/media/known_faces/**")
                .addResourceLocations("file:" + storagePath + "/known_faces/");
    }
}
