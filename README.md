# Zoom Integration

A production-ready Spring Boot application integrating with Zoom API, built following Fellow engineering standards.

## Features

- **Server-to-Server OAuth Authentication** - Automatic token management with thread-safe caching
- **PII Redaction** - Secure logging with automatic redaction of sensitive information
- **Production Monitoring** - Health checks, metrics, and build ID tracking
- **API Documentation** - Interactive Swagger UI for API exploration
- **12-Factor Compliant** - Environment-based configuration, stateless design, dev/prod parity
- **Resilient API Calls** - Exponential backoff retry logic for transient failures

## Tech Stack

- Java 21
- Spring Boot 3.5.6
- PostgreSQL 15
- OkHttp 4.12.0
- OpenAPI 3 (Swagger)
- Spring Boot Actuator
- Docker Compose

## Architecture

Built following Fellow engineering principles:

### Security-First
- No hardcoded secrets (environment variables only)
- PII redaction in all logs
- Token masking in API responses
- Secure password handling
- OWASP-compliant practices

### Production Mindset
- Thread-safe token caching with AtomicReference
- Graceful shutdown handlers
- HTTP client connection pooling
- Comprehensive error handling
- Build ID deployment verification

### 12-Factor Compliance
- **Config** - All configuration via environment variables
- **Dependencies** - Explicit dependency declaration in pom.xml
- **Dev/Prod Parity** - Same PostgreSQL database in all environments
- **Logs** - Structured logging to stdout
- **Disposability** - Fast startup, graceful shutdown
- **Port Binding** - Self-contained service on configurable port

## Quick Start

### Prerequisites

- Java 21+
- Docker and Docker Compose
- Maven 3.8+
- Zoom Server-to-Server OAuth credentials

### 1. Start PostgreSQL Database

```bash
docker-compose up -d
```

This starts PostgreSQL on `localhost:5432` with:
- Database: `zoomdb`
- Username: `postgres`
- Password: `postgres`

### 2. Set Environment Variables

Create a `.env` file (git-ignored):

```bash
# Zoom API Credentials
ZOOM_ACCOUNT_ID=your_account_id
ZOOM_CLIENT_ID=your_client_id
ZOOM_CLIENT_SECRET=your_client_secret

# Optional: Override defaults
PORT=8080
LOG_LEVEL=INFO
BUILD_ID=local-dev
```

### 3. Build and Run

```bash
# Install dependencies and build
mvn clean install

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## API Endpoints

### Health & Monitoring

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Health check with build ID verification |
| `GET /health/live` | Kubernetes liveness probe |
| `GET /health/ready` | Kubernetes readiness probe |
| `GET /actuator/health` | Detailed health status |
| `GET /actuator/metrics` | Application metrics |
| `GET /actuator/info` | Application information |

### Zoom API

| Endpoint | Purpose |
|----------|---------|
| `GET /api/test/token` | Test OAuth token retrieval |
| `GET /api/test/config` | Verify configuration loading |
| `GET /api/user/me` | Get current user information |
| `GET /api/user/{userId}` | Get specific user by ID or email |

### Documentation

| Endpoint | Purpose |
|----------|---------|
| `GET /swagger-ui.html` | Interactive API documentation |
| `GET /v3/api-docs` | OpenAPI 3.0 specification (JSON) |

## Configuration

All configuration is environment-based (12-Factor III: Config):

### Required Environment Variables

```bash
ZOOM_ACCOUNT_ID      # Your Zoom account ID
ZOOM_CLIENT_ID       # Your OAuth client ID
ZOOM_CLIENT_SECRET   # Your OAuth client secret
```

### Optional Environment Variables

```bash
PORT=8080                    # Server port (default: 8080)
LOG_LEVEL=INFO              # Logging level (default: INFO, use TRACE for debugging)
BUILD_ID=local-dev          # Build identifier for deployment tracking
DATABASE_URL                # PostgreSQL connection URL
DATABASE_USERNAME           # Database username
DATABASE_PASSWORD           # Database password
```

## Development

### Running Tests

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Run specific test class
mvn test -Dtest=PIIRedactionServiceTest
```

### Code Quality

This project follows Fellow engineering standards:

#### Golden Rules Compliance
- No hardcoded values or mock data in production paths
- Real tests that verify specific behavior
- Structured logging with appropriate levels
- Build ID deployment verification
- PII redaction in all logs

#### Security Checklist
- All secrets in environment variables
- SQL injection prevention (JPA/Hibernate)
- Input validation at API boundaries
- PII protection in logs
- Secure token caching
- Thread-safe concurrent access

### Project Structure

```
src/
├── main/java/com/zacknetic/zoomintegration/
│   ├── config/              # Configuration classes
│   │   ├── ZoomConfig.java       # Zoom API configuration
│   │   ├── HttpClientConfig.java # HTTP client singleton
│   │   ├── OpenApiConfig.java    # Swagger documentation
│   │   └── GracefulShutdown.java # Shutdown handlers
│   ├── health/              # Health check endpoints
│   ├── security/            # Security utilities
│   │   └── redaction/       # PII redaction service
│   └── zoom/
│       ├── auth/            # OAuth authentication
│       ├── api/             # Zoom API clients
│       ├── models/          # Data models
│       └── retry/           # Retry logic utilities
└── test/java/               # Comprehensive test coverage
    ├── unit tests           # Fast, isolated tests
    └── integration tests    # Full Spring context tests
```

## Deployment

### Build ID Verification

Every deployment includes a build ID for verification:

```bash
# Set BUILD_ID in your CI/CD pipeline
export BUILD_ID=$(git rev-parse --short HEAD)

# Verify deployment
curl http://localhost:8080/health
{
  "status": "UP",
  "buildId": "abc1234",
  "version": "0.0.1-SNAPSHOT",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Docker Deployment

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar

ENV BUILD_ID=unknown
ENV PORT=8080

EXPOSE ${PORT}

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:${PORT}/health/live || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build
mvn clean package -DskipTests
docker build -t zoom-integration:latest .

# Run
docker run -p 8080:8080 \
  -e ZOOM_ACCOUNT_ID=xxx \
  -e ZOOM_CLIENT_ID=xxx \
  -e ZOOM_CLIENT_SECRET=xxx \
  -e BUILD_ID=$(git rev-parse --short HEAD) \
  zoom-integration:latest
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zoom-integration
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: zoom-integration:latest
        ports:
        - containerPort: 8080
        env:
        - name: ZOOM_ACCOUNT_ID
          valueFrom:
            secretKeyRef:
              name: zoom-secrets
              key: account-id
        - name: BUILD_ID
          value: "abc1234"
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

## Monitoring

### Prometheus Metrics

Metrics are exposed at `/actuator/prometheus`:

```bash
# Scrape metrics
curl http://localhost:8080/actuator/prometheus
```

### Key Metrics

- `http_server_requests_seconds` - Request duration histograms
- `jvm_memory_used_bytes` - Memory usage
- `jvm_gc_pause_seconds` - Garbage collection metrics
- `hikaricp_connections_active` - Database connection pool

## Security Considerations

### PII Redaction

All logs automatically redact:
- Email addresses
- Phone numbers
- SSN
- Credit card numbers

```java
// Logs are automatically redacted
log.info("User registered: {}", email);
// Output: "User registered: u***@e***.[REDACTED]"
```

### Token Security

- Tokens cached in-memory only
- Thread-safe access with AtomicReference
- Automatic expiry with 5-minute buffer
- No tokens in logs (masked to first/last 10 chars)

## Troubleshooting

### Common Issues

**Issue**: Database connection refused
```bash
# Solution: Ensure PostgreSQL is running
docker-compose ps
docker-compose up -d
```

**Issue**: OAuth authentication fails
```bash
# Solution: Verify environment variables
curl http://localhost:8080/api/test/config
```

**Issue**: Tests fail with connection errors
```bash
# Solution: Tests use in-memory mock server, check test logs
mvn test -X
```

## Contributing

This project follows Fellow engineering standards. Before contributing:

1. Read Fellow principles in `/.claude/CLAUDE.md`
2. Ensure all tests pass: `mvn test`
3. Verify no hardcoded secrets or PII in logs
4. Add tests for new functionality
5. Update documentation

## License

MIT License - See LICENSE file for details

## Support

- Documentation: `/swagger-ui.html`
- Health Status: `/health`
- Metrics: `/actuator/metrics`

Built with Fellow engineering principles: Security-First, Production Mindset, 12-Factor Compliance.
