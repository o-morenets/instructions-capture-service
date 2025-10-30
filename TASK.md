# 🧩 Coding Challenge: Instructions/Trades Capture Service

## Objective

Design and implement a Spring Boot microservice that:

1. Accepts trade instructions either via file upload or Kafka message
2. Converts the input into a canonical format
3. Applies transformation logic to sensitive fields
4. Converts the canonical format into an accounting platform-specific JSON
5. Publishes the final JSON to a Kafka topic
6. Uses in-memory storage for intermediate processing
7. Ensures security and performance best practices

---

## ⚙️ Functional Requirements

### 1. Input Sources

- **File Upload Endpoint**: Accept `.csv` or `.json` files via REST API
- **Kafka Listener**: Consume messages from topic `instructions.inbound`

### 2. Canonical Transformation

Normalize fields:

- `account_number`: Mask all but last 4 digits
- `security_id`: Convert to uppercase and validate format
- `trade_type`: Normalize to standard codes (e.g., "Buy" → "B")

### 3. Platform-Specific JSON Output

Transform canonical data into the following JSON structure:

```json
{
  "platform_id": "ACCT123",
  "trade": {
    "account": "*****1234",
    "security": "ABC123",
    "type": "B",
    "amount": 100000,
    "timestamp": "2025-08-04T21:15:33Z"
  }
}
```

### 4. Kafka Publishing

Publish the transformed JSON to topic `instructions.outbound`

### 5. In-Memory Storage

Use in-memory data structures (e.g., `ConcurrentHashMap`) to temporarily store canonical records for auditing or retry logic

---

## 🔒 Security Requirements

- Validate and sanitize all input
- Mask sensitive fields (e.g., account numbers)
- Avoid logging sensitive data
- Use secure deserialization and input parsing

---

## 🚀 Performance Requirements

- Efficient parsing and transformation (stream-based processing preferred)
- Asynchronous Kafka publishing
- Graceful handling of large files and high-throughput Kafka streams

---

## 🧠 Bonus Points

- Unit and integration tests using JUnit and Mockito
- Use of Spring Profiles for environment-specific configs
- Swagger/OpenAPI documentation
- Dockerfile for containerization

---

## 📦 Deliverables

- Source code (preferably in a GitHub repo)
- README with setup instructions
- Sample input files and expected output
- Postman collection or curl commands for testing

---

## 🗂️ Project Structure

```
instructions-capture-service/
├── src/
│   └── main/
│       ├── java/com/example/instructions/
│       │   ├── InstructionsCaptureApplication.java
│       │   ├── controller/
│       │   │   └── TradeController.java
│       │   ├── service/
│       │   │   ├── TradeService.java
│       │   │   ├── KafkaPublisher.java
│       │   │   └── KafkaListenerService.java
│       │   ├── model/
│       │   │   ├── CanonicalTrade.java
│       │   │   └── PlatformTrade.java
│       │   └── util/
│       │       └── TradeTransformer.java
│       └── resources/
│           └── application.yml
└── pom.xml
```
