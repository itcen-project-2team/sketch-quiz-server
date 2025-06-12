package com.itcen.whiteboardserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
//                .allowedOrigins("http"+allowedOrigins.split(","), "https"+allowedOrigins)
                .allowedOrigins("http"+allowedOrigins, "https"+allowedOrigins)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }


    /**
     * SecurityFilterChain에서 사용할 CorsConfigurationSource 빈을 직접 정의
     * Spring Security 6.2 이상에서는 이 빈을 Security DSL에 연결해 주어야 한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 Origin을 쉼표(,) 기준으로 분리해서 등록
//        for (String origin : allowedOrigins.split(",")) {
//            config.addAllowedOrigin(origin.trim());
//        }
        config.addAllowedOrigin("http"+allowedOrigins);
        config.addAllowedOrigin("https"+allowedOrigins);

        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 허용할 헤더
        config.setAllowedHeaders(List.of("*"));
        // 자격 증명(Cookie/Authorization 헤더 등) 허용
        config.setAllowCredentials(true);

        // 모든 경로에 대해 위 설정을 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}