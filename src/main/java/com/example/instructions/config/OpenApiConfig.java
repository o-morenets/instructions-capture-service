package com.example.instructions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * OpenAPI/Swagger configuration for API documentation
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080").description("Local development server"),
                        new Server().url("https://api.example.com").description("Production server")
                ))
                .info(new Info()
                        .title("Instructions Capture Service API")
                        .version("1.0.0")
                        .description("Spring Boot microservice for processing trade instructions via file upload and Kafka messaging. " +
                                "Converts inputs to canonical format, applies transformations, and publishes to accounting platforms.")
                        .contact(new Contact()
                                .name("Development Team")
                                .email("dev@example.com")
                                .url("https://github.com/example/instructions-capture-service"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                );
    }
}
