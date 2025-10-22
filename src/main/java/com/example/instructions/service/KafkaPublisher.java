package com.example.instructions.service;

import com.example.instructions.model.PlatformTrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static com.example.instructions.InstructionsCaptureApplication.OUTBOUND_TOPIC;

/**
 * Service for publishing transformed trades to Kafka
 * Handles asynchronous publishing with retry logic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaPublisher {

    private final KafkaTemplate<String, PlatformTrade> kafkaTemplate;

    /**
     * Publish platform trade to outbound Kafka topic
     * Uses asynchronous publishing with a retry mechanism
     */
    @Retryable(
            retryFor = {Exception.class},
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public CompletableFuture<SendResult<String, PlatformTrade>> publishTrade(PlatformTrade platformTrade) {
        String key = platformTrade.platformId();

        log.info("Publishing trade to Kafka topic: {} with key: {}", OUTBOUND_TOPIC, key);

        try {
            CompletableFuture<SendResult<String, PlatformTrade>> future =
                    kafkaTemplate.send(OUTBOUND_TOPIC, key, platformTrade);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish trade with key: {} to topic: {}. Error: {}",
                            key, OUTBOUND_TOPIC, ex.getMessage());
                } else {
                    log.info("Successfully published trade with key: {} to topic: {} at offset: {}",
                            key, OUTBOUND_TOPIC, result.getRecordMetadata().offset());
                }
            });

            return future;
        } catch (Exception e) {
            log.error("Error publishing trade with key: {} to topic: {}. Error: {}",
                    key, OUTBOUND_TOPIC, e.getMessage());
            throw e;
        }
    }
}
