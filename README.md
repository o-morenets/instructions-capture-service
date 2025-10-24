# Instructions Capture Service

A fully reactive Spring Boot microservice built with **Spring WebFlux** that processes trade instructions via file upload and Kafka messaging. The service converts inputs to a canonical format, applies transformations to sensitive fields, and publishes platform-specific JSON to Kafka topics.

## 🚀 Features

- **Fully Reactive Stack**: Built with Spring WebFlux and Project Reactor for end-to-end non-blocking I/O
- **Multi-source Input**: Accept trade instructions via REST API file upload or Kafka messages
- **Format Support**: Process CSV and JSON file formats reactively
- **Data Transformation**: Normalize and mask sensitive data (account numbers, security IDs)
- **In-Memory Storage**: Fast processing with ConcurrentHashMap-based storage
- **Kafka Integration**: Consume from `instructions.inbound` and publish to `instructions.outbound`
- **JWT Security**: JWT Bearer token authentication for REST API endpoints (reactive security)
- **Security**: Input validation, data masking, sanitization, and authentication
- **Performance**: Asynchronous processing with controlled concurrency and backpressure
- **Memory Efficient**: Reactive stream-based processing for large files (10MB+)
- **Monitoring**: Health checks and comprehensive logging
- **Documentation**: OpenAPI/Swagger documentation with JWT support (WebFlux UI)
- **Testing**: Unit tests with WebTestClient for reactive endpoints

## 🛠️ Technology Stack

> **Note**: This project has been fully migrated to a reactive stack using Spring WebFlux. All MVC components, AspectJ dependencies, and Spring Boot Actuator have been removed for a cleaner, fully reactive architecture.

- **Spring Boot 3.5.6** - Application framework
- **Spring WebFlux** - Reactive web framework (fully non-blocking)
- **Project Reactor** - Reactive streams implementation (Mono/Flux)
- **Spring Security (Reactive)** - JWT authentication with ServerHttpSecurity and WebFilter
- **Spring Kafka** - Kafka integration
- **Jackson** - JSON serialization with JavaTimeModule
- **Lombok** - Boilerplate reduction
- **SpringDoc OpenAPI 2.7.0** - API documentation (WebFlux UI)
- **JUnit 5 + Mockito** - Testing framework
- **WebTestClient** - Reactive endpoint testing

### What's NOT Included
- ❌ Spring MVC (replaced with WebFlux)
- ❌ Spring Boot Actuator (removed for demo simplicity)
- ❌ AspectJ (removed - no @Scheduled, @Retriable)
- ❌ Servlet API (fully reactive with Netty)

## 🏗️ Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   File Upload   │    │  Kafka Consumer  │    │  REST API       │
│   (CSV/JSON)    │───▶│  (instructions.  │───▶│  Management     │
└─────────────────┘    │   inbound)       │    └─────────────────┘
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │   Trade Service  │
                       │  (In-Memory      │
                       │   Processing)    │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │  Transformation  │
                       │  & Validation    │
                       └──────────────────┘
                                │
                                ▼
                       ┌──────────────────┐
                       │ Kafka Publisher  │
                       │ (instructions.   │
                       │  outbound)       │
                       └──────────────────┘
```

## 📋 Requirements

- Java 21+
- Maven 3.6+
- Apache Kafka 3.3+
- Spring Boot 3.5.6+
- Spring WebFlux (included)
- Project Reactor Core (included)
- Docker (optional)

## ⚙️ Configuration

The application supports multiple profiles:

### Local Development (`local`)
```yaml
spring.profiles.active: local
kafka.bootstrap-servers: localhost:9092
logging.level.com.example.instructions: DEBUG
```

### Docker (`docker`)
```yaml
spring.profiles.active: docker
kafka.bootstrap-servers: kafka:9092
```

## 🚀 Quick Start

### 1. Start Kafka (Local Development)

**Option 1: Using Docker (Recommended)**
```bash
# Start Kafka in KRaft mode (no Zookeeper needed)
docker compose up kafka -d

# Verify Kafka is running
docker compose logs kafka
```

### 2. Run the Application

```bash
# Clone the repository
git clone <repository-url>
cd instructions-capture-service

# Run with Maven
./mvnw spring-boot:run

# Or run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify the Application

```bash
# Health check
curl http://localhost:8080/api/v1/trades/health

# Swagger UI (WebFlux)
open http://localhost:8080/webjars/swagger-ui/index.html
```

## 📡 API Endpoints

### File Upload
```bash
# Upload CSV file
curl -X POST "http://localhost:8080/api/v1/trades/upload" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: multipart/form-data" \
     -F "file=@sample-trades.csv"

# Upload JSON file
curl -X POST "http://localhost:8080/api/v1/trades/upload" \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: multipart/form-data" \
     -F "file=@sample-trade.json"
```

### Trade Management (Reactive Endpoints)
```bash
# Stream all trades (NDJSON - auto-closes connection)
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/trades

# Get trade by ID
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/trades/TRADE-123

# Stream trades by status (NDJSON)
curl -H "Authorization: Bearer $TOKEN" \
     "http://localhost:8080/api/v1/trades?status=RECEIVED"

# Get statistics
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/trades/statistics

# Clear all trades (testing)
curl -X DELETE \
     -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/trades/clear
```

## 📄 Data Formats

### CSV Format
```csv
account_number,security_id,trade_type,amount,timestamp,platform_id
123456789,ABC123,BUY,100000,2025-08-04 21:15:33,ACCT123
987654321,XYZ789,SELL,50000,2025-08-04 21:16:33,ACCT456
```

### JSON Format
```json
{
  "accountNumber": "123456789",
  "securityId": "ABC123",
  "tradeType": "BUY",
  "amount": 100000,
  "timestamp": "2025-08-04T21:15:33",
  "platformId": "ACCT123"
}
```

### Output Format (Platform-Specific)
```json
{
  "platform_id": "ACCT123",
  "trade": {
    "account": "*****1234",
    "security": "ABC123",
    "type": "B",
    "amount": 100000,
    "timestamp": "2025-08-04T21:15:33"
  }
}
```

## 🔒 Security Features

### Data Masking
- **Account Numbers**: Show only last 4 digits (`123456789` → `*****6789`)
- **Logging**: Sensitive data is never logged in plain text

### Input Validation
- File size limits (10MB max)
- File format validation (CSV/JSON only)
- Data type validation and sanitization
- Security ID format validation (3-12 alphanumeric characters)

### Data Normalization
- **Security IDs**: Converted to uppercase
- **Trade Types**: Normalized to standard codes:
  - `BUY`, `PURCHASE` → `B`
  - `SELL`, `SALE` → `S`
  - `SHORT`, `SHORT_SELL` → `SS`

## 🔐 Security - JWT Authentication

The service uses JWT Bearer token authentication for all REST API endpoints (except health check and Swagger UI).

### Quick Start

1. **Create `.env` file** (recommended):
   ```bash
   # Generate a strong secret
   openssl rand -base64 64
   
   # Create .env file in project root
   cat > .env << 'EOF'
   JWT_SECRET=your-generated-secret-here
   EOF
   ```
   
   The application automatically loads variables from `.env` using `spring-dotenv`.

2. **Alternative: Export environment variables**:
   ```bash
   export JWT_SECRET="your-super-secret-key-here"
   ```

3. **Generate a Test Token**:
   ```bash
   mvn test -Dtest="JwtTokenGeneratorTest#generateTestToken"
   ```

4. **Use the Token in Requests**:
   ```bash
   TOKEN="your-jwt-token-here"
   
   curl -H "Authorization: Bearer $TOKEN" \
        -F "file=@sample-data/sample-trades.csv" \
        http://localhost:8080/api/v1/trades/upload
   ```

For detailed environment setup, see [ENV_SETUP.md](ENV_SETUP.md).

### Protected Endpoints

All endpoints require JWT authentication except:
- `GET /api/v1/trades/health` - Health check
- `GET /swagger-ui/**` - Swagger UI
- `GET /swagger-ui.html` - Swagger UI redirect
- `GET /webjars/**` - Static resources for Swagger UI
- `GET /v3/api-docs/**` - OpenAPI documentation

### Integration with Other Services

## 🧪 Testing

### Run All Tests
```bash
./mvnw test
```

### Run Specific Test Categories
```bash
# Unit tests only
./mvnw test -Dtest="*Test"

# Controller tests only
./mvnw test -Dtest="*ControllerTest"
```

## 🐳 Docker Support

### Build Image
```bash
docker build -t instructions-capture-service .
```

### Run with Docker Compose
```bash
docker compose up -d
```

### Environment Variables
```bash
# Required
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Optional
SPRING_PROFILES_ACTIVE=docker
LOG_LEVEL=INFO
KAFKA_CONSUMER_GROUP=capture-service  # Default: 'capture-service' (local), 'capture-service-docker' (docker)
```

## 📊 Monitoring

### Health Checks
- **Application**: `/api/v1/trades/health`

### Logging
- **Structured Logging**: JSON format in production
- **Log Levels**: Configurable per environment
- **No Sensitive Data**: Account numbers and other PII are masked
- **Reactive Logging**: Non-blocking log operations

## 🔄 Kafka Integration

### Modern KRaft Mode (No Zookeeper!)
This project uses **Kafka in KRaft mode**, eliminating the need for Zookeeper:
- **Simpler Architecture**: Fewer moving parts, easier to manage
- **Better Performance**: Reduced latency and improved throughput
- **Enhanced Security**: Built-in security features and easier configuration
- **Faster Startup**: No dependency on Zookeeper cluster initialization
- **Production Ready**: Stable since Kafka 3.3+

### Topics
- **Inbound**: `instructions.inbound` (consume trade instructions)
- **Outbound**: `instructions.outbound` (publish transformed trades)

### Consumer Configuration
- **Group ID**: `capture-service`
- **Auto Commit**: Disabled (manual acknowledgment)
- **Retry Logic**: 3 attempts with exponential backoff
- **Dead Letter Topic**: Automatic for failed messages

### Producer Configuration
- **Serialization**: JSON format
- **Key Strategy**: Platform ID for partitioning
- **Retry Logic**: 3-5 attempts depending on environment
- **Acknowledgment**: All replicas (`acks=all`)

## 🚨 Error Handling

### File Processing Errors
- Invalid file formats are rejected with clear error messages
- Malformed data rows are skipped with warnings
- Processing continues for valid data

### Kafka Errors
- Failed messages are retried with exponential backoff
- Persistent failures are sent to Dead Letter Topic
- Circuit breaker prevents cascade failures

### Validation Errors
- Input validation errors return HTTP 400 with details
- Business rule violations are logged and reported

## 🔧 Development

### Prerequisites
```bash
# Install Java 21
sdk install java 21.0.1-open

# Install Maven
sdk install maven 3.9.5

# Start local Kafka (KRaft mode - no Zookeeper!)
docker compose up kafka -d
```

### IDE Setup
- **IntelliJ IDEA**: Import Maven project, enable annotation processing
- **VS Code**: Install Java Extension Pack and Spring Boot Extension

### Code Quality
```bash
# Run checkstyle
./mvnw checkstyle:check

# Run SpotBugs
./mvnw spotbugs:check

# Run all quality checks
./mvnw verify
```

## 📈 Performance

### Throughput
- **File Processing**: 1000+ trades/second with reactive streaming
- **Kafka Processing**: 5000+ messages/second
- **Memory Usage**: ~80MB for 176K trades (10MB file)

### Optimization Features
- **Fully Reactive**: Spring WebFlux with Project Reactor for end-to-end non-blocking I/O
- **Reactive File Upload**: FilePart with DataBufferUtils for efficient streaming
- **Backpressure**: Automatic flow control prevents memory overflow
- **Controlled Concurrency**: Process up to 8 trades in parallel with flatMap
- **Stream Processing**: Memory-efficient file parsing (handles 10MB+ files)
- **Reactive Security**: Non-blocking JWT authentication with WebFilter
- **Connection Pooling**: Optimized Kafka connections
- **Caching**: In-memory trade storage with ConcurrentHashMap

### Large File Support
- **10MB CSV**: ~3.5 seconds, 176,000 trades
- **Memory Peak**: 80MB (vs 120MB with traditional streaming)
- **No Timeouts**: Handles large files without client disconnection
- **Response Size**: Optimized (returns trade count only)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Standards
- Follow Google Java Style Guide
- Maintain 90%+ test coverage
- Add documentation for public APIs
- Use meaningful commit messages

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Troubleshooting

### Common Issues

**Kafka Connection Failed**
```bash
# Check Kafka is running in KRaft mode
docker ps | grep kafka
docker compose logs kafka

# Verify topic exists
docker exec -it instructions-capture-service-kafka-1 kafka-topics --list --bootstrap-server localhost:9092

# Check cluster info
docker exec -it instructions-capture-service-kafka-1 kafka-cluster --bootstrap-server localhost:9092 cluster-id
```

**File Upload Errors**
- Check file size (max 10MB)
- Verify file format (CSV/JSON only)
- Ensure proper CSV headers

**Memory Issues**
- Monitor with `/api/v1/trades/statistics`
- Consider clearing old trades: `DELETE /api/v1/trades/clear`
- Adjust JVM heap size: `-Xmx2g`
- Check logs for backpressure signals

### Support
- 📧 Email: oleksii.morenets@gmail.com
- 🐛 Issues: GitHub Issues
- 💬 Discussions: GitHub Discussions
