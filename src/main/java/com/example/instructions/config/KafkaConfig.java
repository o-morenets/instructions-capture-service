package com.example.instructions.config;

import com.example.instructions.model.PlatformTrade;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig {

    public static final String INBOUND_TOPIC = "instructions.inbound";
    public static final String OUTBOUND_TOPIC = "instructions.outbound";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

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
     * Create inbound topic for receiving trade instructions
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
     * Create outbound topic for publishing transformed trades
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
     * Producer factory configured with custom ObjectMapper for proper LocalDateTime serialization
     */
    @Bean
    public ProducerFactory<String, PlatformTrade> producerFactory(ObjectMapper objectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Create JsonSerializer with our custom ObjectMapper
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
}
