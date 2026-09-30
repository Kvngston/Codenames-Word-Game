package com.tk.wordagents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The React app is served from another origin (Vercel), so the API allows it explicitly. */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "DELETE")
            .allowedHeaders("Authorization", "Content-Type");
    }
}
