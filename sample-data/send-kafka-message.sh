#!/bin/bash

# Send Test Message to Kafka Inbound Topic
# Usage: ./send-kafka-message.sh

echo "🚀 Sending sample trade message to Kafka inbound topic..."

# Send a sample message to the instructions.inbound topic (with key for compacted topic)
docker exec -i instructions-capture-service-kafka-1 kafka-console-producer \
  --topic instructions.inbound \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:" << 'EOF'
KAFKA-TEST-001:{"tradeId": "KAFKA-TEST-001", "accountNumber": "123456789", "securityId": "AAPL", "tradeType": "BUY", "amount": 50000.00, "timestamp": "2025-10-15T12:00:00", "platformId": "TEST_PLATFORM"}
EOF

echo "✅ Message sent successfully!"
echo ""
echo "📋 To verify processing:"
echo "  1. Check application logs: docker compose logs instructions-service -f"
echo "  2. Check API endpoint: curl http://localhost:8080/api/v1/trades"
echo "  3. Check Kafka UI: http://localhost:8081 → instructions.outbound topic"
echo ""
