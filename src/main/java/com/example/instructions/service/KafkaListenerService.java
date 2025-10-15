package com.example.instructions.service;

import com.example.instructions.config.KafkaConfig;
import com.example.instructions.model.CanonicalTrade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

/**
 * Service for consuming trade instructions from Kafka
 * Processes incoming messages and delegates to TradeService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaListenerService {

    private final TradeService tradeService;

    /**
     * Listen to inbound trade instructions from Kafka topic
     * Includes retry mechanism for failed processing
     */
    @RetryableTopic(
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = KafkaConfig.INBOUND_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void handleTradeInstruction(
            @Payload(required = false) CanonicalTrade canonicalTrade,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        // Handle deserialization errors
        if (canonicalTrade == null) {
            log.error("Received null trade instruction due to deserialization error - Topic: {}, Partition: {}, Offset: {}", 
                     topic, partition, offset);
            acknowledgment.acknowledge(); // Skip this message

            return;
        }

        log.info("Received trade instruction from Kafka - Topic: {}, Partition: {}, Offset: {}, Trade ID: {}",
                topic, partition, offset, canonicalTrade.getTradeId());

        try {
            canonicalTrade.setSource("KAFKA");
            tradeService.processTradeInstruction(canonicalTrade);

            acknowledgment.acknowledge();

            log.info("Successfully processed trade instruction from Kafka - Trade ID: {}",
                    canonicalTrade.getTradeId());

        } catch (Exception e) {
            log.error("Error processing trade instruction from Kafka - Trade ID: {}, Error: {}",
                    canonicalTrade.getTradeId(), e.getMessage(), e);

            // Don't acknowledge - this will trigger retry mechanism

            throw e;
        }
    }

    /**
     * Handle Dead Letter Topic (DLT) for failed messages
     */
    @KafkaListener(topics = KafkaConfig.INBOUND_TOPIC + ".DLT", groupId = "${spring.kafka.consumer.group-id}")
    public void handleDltTradeInstruction(
            @Payload(required = false) CanonicalTrade canonicalTrade,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            Acknowledgment acknowledgment) {

        // Handle deserialization errors in DLT
        if (canonicalTrade == null) {
            log.error("Received null trade instruction in DLT due to deserialization error - Topic: {}, Exception: {}", 
                     topic, exceptionMessage);
            acknowledgment.acknowledge(); // Skip this message

            return;
        }

        log.error("Trade instruction moved to Dead Letter Topic - Topic: {}, Trade ID: {}, Exception: {}",
                topic, canonicalTrade.getTradeId(), exceptionMessage);

        try {
            canonicalTrade.setStatus(CanonicalTrade.TradeStatus.FAILED);
            tradeService.storeTrade(canonicalTrade);

            acknowledgment.acknowledge();

            log.info("Failed trade instruction stored for manual review - Trade ID: {}",
                    canonicalTrade.getTradeId());

        } catch (Exception e) {
            log.error("Error handling DLT trade instruction - Trade ID: {}, Error: {}",
                    canonicalTrade.getTradeId(), e.getMessage(), e);

            // Still acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }
}
