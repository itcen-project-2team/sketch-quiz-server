package itcen.whiteboardserver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "DRAWCEN API 명세서",
                description = "DRAWCEN 프로젝트 API 명세서",
                version = "v1"
        )
//        security = {@SecurityRequirement(name = "Bearer Authentication")}
)
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI openAPI() {
            return new OpenAPI();
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("멤버 관련 API")
                .pathsToMatch("/api/member/**") // 특정 경로만 포함
                .build();
    }

}
