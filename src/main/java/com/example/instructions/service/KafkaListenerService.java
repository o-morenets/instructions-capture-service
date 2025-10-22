package com.example.instructions.service;

import com.example.instructions.model.CanonicalTrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import static com.example.instructions.InstructionsCaptureApplication.INBOUND_TOPIC;

/**
 * Service for consuming trade instructions from Kafka
 * Processes incoming messages and delegates to TradeService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaListenerService {

    private final TradeService tradeService;

    /**
     * Listen to inbound trade instructions from Kafka topic
     * Simple processing - no retries, errors are logged and the message continues
     */
    @KafkaListener(topics = INBOUND_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleTradeInstruction(
            @Payload(required = false) CanonicalTrade canonicalTrade,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // Handle deserialization errors
        if (canonicalTrade == null) {
            log.error("Received null trade instruction due to deserialization error - Topic: {}, Partition: {}, Offset: {}",
                    topic, partition, offset);

            // Message will be auto-committed and skipped
            return;
        }

        log.info("Received trade instruction from Kafka - Topic: {}, Partition: {}, Offset: {}, Trade ID: {}",
                topic, partition, offset, canonicalTrade.getTradeId());

        try {
            canonicalTrade.setSource("KAFKA");
            tradeService.processTradeInstruction(canonicalTrade);

            log.info("Successfully processed trade instruction from Kafka - Trade ID: {}", canonicalTrade.getTradeId());
        } catch (Exception e) {
            log.error("Error processing trade instruction from Kafka - Trade ID: {}, Error: {}",
                    canonicalTrade.getTradeId(), e.getMessage(), e);

            // Store failed to trade for manual review
            try {
                canonicalTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
                tradeService.storeTrade(canonicalTrade);
                log.info("Failed trade stored for manual review - Trade ID: {}", canonicalTrade.getTradeId());
            } catch (Exception storageException) {
                log.error("Failed to store failed trade - Trade ID: {}, Error: {}",
                        canonicalTrade.getTradeId(), storageException.getMessage());
            }

            // Don't throw - let a message be auto-committed and continue
        }
    }
}
