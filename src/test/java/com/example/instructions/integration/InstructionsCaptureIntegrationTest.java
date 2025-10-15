package com.example.instructions.integration;

import com.example.instructions.model.CanonicalTrade;
import com.example.instructions.model.PlatformTrade;
import com.example.instructions.service.TradeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(partitions = 1, 
               brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" },
               topics = { "instructions.inbound", "instructions.outbound" })
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
class InstructionsCaptureIntegrationTest {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, PlatformTrade> outboundConsumer;
    private Producer<String, CanonicalTrade> inboundProducer;

    @BeforeEach
    void setUp() {
        tradeService.clearAllTrades();
        setupKafkaTestClients();
    }

    private void setupKafkaTestClients() {
        // Setup consumer for outbound topic
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PlatformTrade.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        outboundConsumer = new DefaultKafkaConsumerFactory<String, PlatformTrade>(consumerProps).createConsumer();
        outboundConsumer.subscribe(Collections.singletonList("instructions.outbound"));

        // Setup producer for inbound topic
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", JsonSerializer.class);

        inboundProducer = new DefaultKafkaProducerFactory<String, CanonicalTrade>(producerProps).createProducer();
    }

    @Test
    void shouldProcessCsvFileUploadEndToEnd() throws Exception {
        String csvContent = "account_number,security_id,trade_type,amount,timestamp,platform_id\n" +
                          "123456789,ABC123,BUY,100000,2025-08-04 21:15:33,ACCT123";
        MockMultipartFile file = new MockMultipartFile("file", "trades.csv", "text/csv", csvContent.getBytes());

        List<String> tradeIds = tradeService.processFileUploadReactive(file).block();

        assertThat(tradeIds).hasSize(1);
        
        // Verify trade is stored
        String tradeId = tradeIds.get(0);
        var storedTrade = tradeService.getTradeById(tradeId);
        assertThat(storedTrade).isPresent();
        assertThat(storedTrade.get().getAccountNumber()).isEqualTo("123456789");
        assertThat(storedTrade.get().getSource()).isEqualTo("FILE_UPLOAD");

        // Wait and verify Kafka message
        ConsumerRecord<String, PlatformTrade> record = KafkaTestUtils.getSingleRecord(outboundConsumer, "instructions.outbound", Duration.ofSeconds(10));
        
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo("ACCT123");
        
        PlatformTrade platformTrade = record.value();
        assertThat(platformTrade.platformId()).isEqualTo("ACCT123");
        assertThat(platformTrade.trade().account()).isEqualTo("*****6789");
        assertThat(platformTrade.trade().security()).isEqualTo("ABC123");
        assertThat(platformTrade.trade().type()).isEqualTo("B");
    }

    @Test
    void shouldProcessJsonFileUploadEndToEnd() throws Exception {
        CanonicalTrade trade = CanonicalTrade.builder()
            .accountNumber("987654321")
            .securityId("XYZ789")
            .tradeType("SELL")
            .amount(new BigDecimal("50000"))
            .timestamp(LocalDateTime.of(2025, 8, 4, 22, 30, 45))
            .platformId("ACCT456")
            .build();

        String jsonContent = objectMapper.writeValueAsString(trade);
        MockMultipartFile file = new MockMultipartFile("file", "trade.json", "application/json", jsonContent.getBytes());

        List<String> tradeIds = tradeService.processFileUploadReactive(file).block();

        assertThat(tradeIds).hasSize(1);
        
        // Verify trade is stored
        String tradeId = tradeIds.get(0);
        var storedTrade = tradeService.getTradeById(tradeId);
        assertThat(storedTrade).isPresent();
        assertThat(storedTrade.get().getAccountNumber()).isEqualTo("987654321");

        // Wait and verify Kafka message
        ConsumerRecord<String, PlatformTrade> record = KafkaTestUtils.getSingleRecord(outboundConsumer, "instructions.outbound", Duration.ofSeconds(10));
        
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo("ACCT456");
        
        PlatformTrade platformTrade = record.value();
        assertThat(platformTrade.trade().account()).isEqualTo("*****4321");
        assertThat(platformTrade.trade().security()).isEqualTo("XYZ789");
        assertThat(platformTrade.trade().type()).isEqualTo("S");
    }

    @Test
    void shouldProcessKafkaInboundMessage() throws JsonProcessingException, InterruptedException {
        CanonicalTrade canonicalTrade = CanonicalTrade.builder()
            .tradeId("KAFKA-TRADE-001")
            .accountNumber("555666777")
            .securityId("DEF456")
            .tradeType("BUY")
            .amount(new BigDecimal("75000"))
            .timestamp(LocalDateTime.now())
            .platformId("ACCT789")
            .status(CanonicalTrade.TradeStatus.RECEIVED)
            .build();

        // Send message to inbound topic
        ProducerRecord<String, CanonicalTrade> producerRecord = 
            new ProducerRecord<>("instructions.inbound", canonicalTrade.getPlatformId(), canonicalTrade);
        inboundProducer.send(producerRecord);
        inboundProducer.flush();

        Thread.sleep(5000);

        // Verify trade was processed and published to outbound
        ConsumerRecord<String, PlatformTrade> outboundRecord = 
            KafkaTestUtils.getSingleRecord(outboundConsumer, "instructions.outbound", Duration.ofSeconds(10));
        
        assertThat(outboundRecord).isNotNull();
        assertThat(outboundRecord.key()).isEqualTo("ACCT789");
        
        PlatformTrade platformTrade = outboundRecord.value();
        assertThat(platformTrade.platformId()).isEqualTo("ACCT789");
        assertThat(platformTrade.trade().account()).isEqualTo("***66777");
        assertThat(platformTrade.trade().security()).isEqualTo("DEF456");
        assertThat(platformTrade.trade().type()).isEqualTo("B");

        // Verify trade is stored in memory
        var storedTrade = tradeService.getTradeById("KAFKA-TRADE-001");
        assertThat(storedTrade).isPresent();
        assertThat(storedTrade.get().getSource()).isEqualTo("KAFKA");
    }

    @Test
    void shouldHandleInvalidDataGracefully() throws Exception {
        // CSV with invalid data
        String invalidCsvContent = "account_number,security_id,trade_type,amount,timestamp,platform_id\n" +
                                 "123,INVALID-SECURITY-ID-TOO-LONG,BUY,invalid_amount,2025-08-04 21:15:33,ACCT123";
        MockMultipartFile file = new MockMultipartFile("file", "invalid.csv", "text/csv", invalidCsvContent.getBytes());

        // Should handle gracefully without crashing
        assertThatCode(() -> tradeService.processFileUploadReactive(file).block())
            .doesNotThrowAnyException();
        
        // Should still process valid rows and skip invalid ones
        List<String> tradeIds = tradeService.processFileUploadReactive(file).block();
        assertThat(tradeIds).isEmpty(); // No valid trades in this case
    }
}
