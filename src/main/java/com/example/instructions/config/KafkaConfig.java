package com.example.instructions.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

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
}
