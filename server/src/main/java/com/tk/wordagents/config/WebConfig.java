package com.tk.wordagents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** The React app is served from another origin (Vercel), so the API allows it explicitly. */
@Configuration
class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;
    private final RateLimitInterceptor rateLimits;

    WebConfig(AppProperties properties, RateLimitInterceptor rateLimits) {
        this.properties = properties;
        this.rateLimits = rateLimits;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimits).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("Authorization", "Content-Type");
    }
}
