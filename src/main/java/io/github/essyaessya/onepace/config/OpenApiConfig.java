package io.github.essyaessya.onepace.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI onepaceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("One Pace API")
                        .description("문화 뉘앙스 번역 및 회의 요약 기능을 제공하는 API")
                        .version("v0.0.1"));
    }
}
