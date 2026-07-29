package com.seoulink.backend.global.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 개발 환경에서 리뷰 업로드 파일을 /uploads/** URL로 제공한다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDirectory = Path.of("uploads").toAbsolutePath().toUri().toString();
        if (!uploadDirectory.endsWith("/")) {
            uploadDirectory += "/";
        }

        // 사용자가 업로드한 파일은 실행 폴더의 uploads에서,
        // 데모 후기 이미지는 classpath의 static/uploads에서 제공한다.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDirectory, "classpath:/static/uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*");
    }
}
