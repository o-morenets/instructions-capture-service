# JWT Security Configuration

This service uses JWT (JSON Web Token) for authentication. All API endpoints except health check and Swagger UI require a valid JWT token.

## Overview

- **Algorithm**: HS256 (HMAC with SHA-256)
- **Token Expiration**: 1 hour (configurable)
- **Token Location**: Authorization header with Bearer scheme

## Configuration

### Using .env File (Recommended)

The easiest way to configure JWT is using a `.env` file in the project root:

```bash
# Generate a strong secret
openssl rand -base64 64

# Create .env file
cat > .env << 'EOF'
JWT_SECRET=your-generated-secret-here
JWT_EXPIRATION=3600000
EOF
```

The application automatically loads these variables using `spring-dotenv`. See [ENV_SETUP.md](ENV_SETUP.md) for details.

### Using Environment Variables

Alternatively, export environment variables:

```bash
# JWT Secret (minimum 256 bits / 32 bytes)
# Generate using: openssl rand -base64 64
export JWT_SECRET="your-super-secret-key-here"

# Token expiration in milliseconds (default: 1 hour)
export JWT_EXPIRATION=3600000
```

### application.yml

The configuration reads from environment variables:

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}
```

## Generating JWT Tokens

### For Testing

Run the test class to generate tokens:

```bash
mvn test -Dtest=JwtTokenGeneratorTest#generateTestToken
```

### Using Java Code

```java
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class TokenGenerator {
    public static String generateToken(String secret, String serviceName, long expirationMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        
        return Jwts.builder()
                .subject(serviceName)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }
}
```

### Using Python

```python
import jwt
import datetime

def generate_token(secret, service_name, expiration_hours=1):
    payload = {
        'sub': service_name,
        'iat': datetime.datetime.utcnow(),
        'exp': datetime.datetime.utcnow() + datetime.timedelta(hours=expiration_hours)
    }
    return jwt.encode(payload, secret, algorithm='HS256')

# Usage
token = generate_token('your-secret-key', 'external-service')
print(f"Bearer {token}")
```

### Using Node.js

```javascript
const jwt = require('jsonwebtoken');

function generateToken(secret, serviceName, expirationHours = 1) {
    const payload = {
        sub: serviceName,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + (expirationHours * 3600)
    };
    return jwt.sign(payload, secret, { algorithm: 'HS256' });
}

// Usage
const token = generateToken('your-secret-key', 'external-service');
console.log(`Bearer ${token}`);
```

## Making Authenticated Requests

### Using curl

```bash
TOKEN="your-jwt-token-here"

curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/trades
```

### Using Postman

1. Open Postman
2. Go to Authorization tab
3. Select Type: **Bearer Token**
4. Paste your JWT token in the Token field
5. Send the request

### Using Java RestTemplate

```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class ApiClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String jwtToken;

    public String callApi(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class)
                          .getBody();
    }
}
```

### Using Python requests

```python
import requests

def call_api(url, token):
    headers = {
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    }
    response = requests.get(url, headers=headers)
    return response.json()

# Usage
result = call_api('http://localhost:8080/api/v1/trades', your_token)
```

## API Endpoints

### Public Endpoints (No Authentication Required)

- `GET /actuator/**` - Actuator endpoints
- `GET /api/v1/trades/health` - Health check
- `GET /v3/api-docs/**` - OpenAPI documentation
- `GET /swagger-ui/**` - Swagger UI

### Protected Endpoints (JWT Required)

- `POST /api/v1/trades/upload` - Upload trade files
- `GET /api/v1/trades` - Get all trades
- `GET /api/v1/trades/{id}` - Get trade by ID
- `GET /api/v1/trades/statistics` - Get trade statistics
- `DELETE /api/v1/trades` - Clear all trades

## Error Responses

### 401 Unauthorized

When token is missing or invalid:

```json
{
  "success": false,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "errorCode": "UNAUTHORIZED",
  "status": 401,
  "timestamp": "2025-10-16T14:30:00Z",
  "path": "/api/v1/trades"
}
```

### Token Validation Errors

- **Missing Token**: No Authorization header provided
- **Malformed Token**: Invalid JWT format
- **Expired Token**: Token has passed its expiration time
- **Invalid Signature**: Token signature doesn't match the secret

## Security Best Practices

### For Production

1. **Use Strong Secrets**
   ```bash
   # Generate a strong secret
   openssl rand -base64 64
   ```

2. **Use Environment Variables**
   - Never hardcode secrets in application.yml
   - Use environment variables or secret management systems

3. **Rotate Secrets Regularly**
   - Change JWT secrets periodically
   - Implement a secret rotation strategy

4. **Use HTTPS**
   - Always use HTTPS in production
   - JWT tokens in plain HTTP can be intercepted

5. **Set Appropriate Expiration**
   - Short-lived tokens are more secure
   - Consider using refresh tokens for long-lived sessions

6. **Monitor Token Usage**
   - Log authentication attempts
   - Monitor for suspicious activity

### Token Storage (Client Side)

- **Never** store tokens in localStorage (vulnerable to XSS)
- **Prefer** httpOnly cookies or secure in-memory storage
- **Always** validate tokens server-side

## Testing JWT Security

### Unit Tests

The security components include tests in `JwtUtilTest` and `JwtAuthenticationFilterTest`.

### Integration Tests

Test the complete authentication flow:

```java
@Test
void testAuthenticatedEndpoint() {
    String token = generateToken("test-service");
    
    mockMvc.perform(get("/api/v1/trades")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
}

@Test
void testUnauthorizedAccess() {
    mockMvc.perform(get("/api/v1/trades"))
            .andExpect(status().isUnauthorized());
}
```

## Troubleshooting

### Token Validation Fails

1. Check the secret matches between token generation and service
2. Verify the token hasn't expired
3. Ensure the token is properly formatted in the header
4. Check logs for detailed error messages

### Common Issues

- **"Invalid JWT signature"**: Secret mismatch
- **"JWT token has expired"**: Token expiration exceeded
- **"Malformed JWT"**: Invalid token format
- **"Failed to extract username"**: Token payload is invalid

## Support

For issues or questions:
- Check the logs: `logs/instructions-capture-service.log`
- Review Swagger UI: `http://localhost:8080/swagger-ui.html`
- Contact: dev@example.com

