# Kafka Inbound Topic Message Samples

This file contains sample JSON messages that can be sent directly to the **`instructions.inbound`** Kafka topic.

## How to Send Messages to Kafka Topic

### Option 1: Using Docker Exec (Console Producer)

**⚠️ Important**: The `instructions.inbound` topic is configured as **compacted**, which requires every message to have a key.

```bash
# Single message (key:value format required for compacted topics)
docker exec -i instructions-capture-service-kafka-1 kafka-console-producer \
  --topic instructions.inbound \
  --bootstrap-server localhost:9092 \
  --property "parse.key=true" \
  --property "key.separator=:" << EOF
TRADE-12345-SAMPLE:{"tradeId": "TRADE-12345-SAMPLE", "accountNumber": "987654321", "securityId": "AAPL", "tradeType": "BUY", "amount": 150000.00, "timestamp": "2025-10-15T12:00:00", "platformId": "PLATFORM_A"}
EOF
```

### Option 2: Using Kafka UI (Browser)
1. Open http://localhost:8081
2. Navigate to **Topics** → **instructions.inbound**
3. Click **Produce Message**
4. **Set Key** (e.g., `TRADE-12345-SAMPLE`) - **Required for compacted topics!**
5. **Set Value** with JSON payload (see samples below)

---

## ⚠️ Key Requirement for Compacted Topics

The Kafka topics in this service are configured as **compacted topics**. This means:
- Every message **must have a key**
- Kafka uses the key to keep only the latest message for each unique key
- Messages without keys will be **rejected**

**For Kafka UI**: Set both Key and Value fields  
**For Command Line**: Use `key:value` format as shown above

---

## Sample Messages

**Note**: When using Kafka UI, use the `tradeId` as the **Key** and the JSON as the **Value**.

### 1. Valid Buy Trade
```json
{
  "tradeId": "TRADE-BUY-001",
  "accountNumber": "123456789",
  "securityId": "AAPL", 
  "tradeType": "BUY",
  "amount": 50000.00,
  "timestamp": "2025-10-15T10:30:00",
  "platformId": "NYSE_PLATFORM"
}
```

### 2. Valid Sell Trade
```json
{
  "tradeId": "TRADE-SELL-002", 
  "accountNumber": "987654321",
  "securityId": "GOOGL",
  "tradeType": "SELL", 
  "amount": 75000.50,
  "timestamp": "2025-10-15T11:45:00",
  "platformId": "NASDAQ_PLATFORM"
}
```

### 3. Valid Short Sale
```json
{
  "tradeId": "TRADE-SHORT-003",
  "accountNumber": "456789123", 
  "securityId": "TSLA",
  "tradeType": "SHORT_SELL",
  "amount": 25000.75,
  "timestamp": "2025-10-15T14:20:00", 
  "platformId": "BATS_PLATFORM"
}
```

### 4. Alternative Trade Types
```json
{
  "accountNumber": "111222333",
  "securityId": "MSFT", 
  "tradeType": "PURCHASE",
  "amount": 100000.00,
  "timestamp": "2025-10-15T09:15:00",
  "platformId": "CBOE_PLATFORM"
}
```

### 5. Minimal Valid Message (Auto-generated ID)
```json
{
  "accountNumber": "999888777", 
  "securityId": "NVDA",
  "tradeType": "BUY", 
  "amount": 200000.00,
  "timestamp": "2025-10-15T16:30:00",
  "platformId": "EXCHANGE_X"
}
```

---

## Invalid Message Examples (For Testing Error Handling)

### Invalid Security ID (Too Short)
```json
{
  "accountNumber": "123456789",
  "securityId": "AB", 
  "tradeType": "BUY",
  "amount": 1000.00,
  "timestamp": "2025-10-15T12:00:00", 
  "platformId": "TEST_PLATFORM"
}
```

### Invalid Amount (Negative)
```json
{
  "accountNumber": "123456789",
  "securityId": "TEST123",
  "tradeType": "BUY", 
  "amount": -1000.00,
  "timestamp": "2025-10-15T12:00:00",
  "platformId": "TEST_PLATFORM"
}
```

### Missing Required Fields
```json
{
  "securityId": "TEST123",
  "amount": 1000.00
}
```

---

## Field Descriptions

| Field | Type | Required | Description | Example |
|-------|------|----------|-------------|---------|
| `tradeId` | String | No | Unique identifier (auto-generated if null) | `"TRADE-12345"` |
| `accountNumber` | String | **Yes** | Customer account number | `"123456789"` |
| `securityId` | String | **Yes** | Security identifier (3-12 alphanumeric) | `"AAPL"`, `"ABC123"` |
| `tradeType` | String | **Yes** | Trade type | `"BUY"`, `"SELL"`, `"SHORT"`, `"PURCHASE"`, `"SALE"`, `"SHORT_SELL"` |
| `amount` | Number | **Yes** | Trade amount (positive) | `50000.00` |
| `timestamp` | String | **Yes** | ISO DateTime format | `"2025-10-15T12:00:00"` |
| `platformId` | String | **Yes** | Trading platform identifier | `"NYSE_PLATFORM"` |
| `source` | String | No | Message source (auto-set to "KAFKA") | `"EXTERNAL_SYSTEM"` |
| `status` | String | No | Processing status (auto-set to "RECEIVED") | `"RECEIVED"` |

---

## Expected Processing Flow

1. **Message sent** to `instructions.inbound` topic
2. **KafkaListenerService** consumes message  
3. **Validation** performed by TradeTransformer
4. **Storage** in memory with status updates
5. **Transformation** to PlatformTrade format
6. **Publication** to `instructions.outbound` topic

## Monitoring Results

- **Application logs**: `docker compose logs instructions-service -f`
- **Kafka UI**: Browse `instructions.outbound` topic for processed results
- **API**: `GET http://localhost:8080/api/v1/trades` to see stored trades
