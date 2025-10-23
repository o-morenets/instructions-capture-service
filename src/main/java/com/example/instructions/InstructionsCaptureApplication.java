package com.example.instructions;

import com.example.instructions.model.PlatformTrade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableKafka
@EnableScheduling
public class InstructionsCaptureApplication {

    public static final String INBOUND_TOPIC = "instructions.inbound";
    public static final String OUTBOUND_TOPIC = "instructions.outbound";

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public static void main(String[] args) {
        SpringApplication.run(InstructionsCaptureApplication.class, args);
    }

    /**
     * Kafka Admin configuration for topic creation
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        return new KafkaAdmin(configs);
    }

    /**
     * Create an inbound topic for receiving trade instructions
     */
    @Bean
    public NewTopic inboundTopic() {
        return TopicBuilder.name(INBOUND_TOPIC)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }

    /**
     * Create an outbound topic for publishing transformed trades
     */
    @Bean
    public NewTopic outboundTopic() {
        return TopicBuilder.name(OUTBOUND_TOPIC)
                .partitions(3)
                .replicas(1)
                .compact()
                .build();
    }

    /**
     * Producer factory configured with custom ObjectMapper for proper Instant serialization
     */
    @Bean
    public ProducerFactory<String, PlatformTrade> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Create JsonSerializer with our custom ObjectMapper that handles Instant properly
        // Pass ObjectMapper in constructor and DON'T use both config properties and setters
        JsonSerializer<PlatformTrade> jsonSerializer = new JsonSerializer<>(objectMapper);

        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), jsonSerializer);
    }

    /**
     * KafkaTemplate configured with custom ObjectMapper
     */
    @Bean
    public KafkaTemplate<String, PlatformTrade> kafkaTemplate(ProducerFactory<String, PlatformTrade> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }

    /**
     * OpenAPI/Swagger configuration for API documentation with JWT security
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(Collections.singletonList(
                        new Server().url("http://localhost:8080").description("Local development server")
                ))
                .info(new Info()
                        .title("Instructions Capture Service API")
                        .version("1.0.0")
                        .description("""
                                Spring Boot microservice for processing trade instructions via file upload and Kafka messaging. \
                                Converts inputs to canonical format, applies transformations, and publishes to accounting platforms. \
                                
                                
                                **Authentication:** All endpoints except health check and Swagger UI require JWT Bearer token authentication.""")
                        .contact(new Contact()
                                .name("Oleksii Morenets")
                                .email("oleksii.morenets@gmail.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                )
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT authentication token")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

}
