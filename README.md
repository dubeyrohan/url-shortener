# URL Shortener Service

A production-grade URL shortener built with Spring Boot 3, Redis caching, and PostgreSQL — designed to demonstrate backend system design, caching strategies, and clean REST API patterns.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)

---

## Features

- **Short URL generation** using Base62 encoding (62^7 ≈ 3.5 trillion combinations)
- **Redis caching** with cache-aside pattern, 5-minute TTL
- **PostgreSQL persistence** with indexed lookups
- **Click analytics** — each redirect increments a counter
- **TTL-based expiration** for short URLs (optional)
- **Per-IP rate limiting** using token bucket algorithm (Bucket4j)
- **Input validation** with Jakarta Bean Validation
- **Global exception handling** with structured error responses
- **Health checks** via Spring Boot Actuator
- **Fully Dockerized** — single `docker-compose up` for the full stack

---

## Architecture

```
┌──────────┐         ┌──────────────────┐         ┌──────────┐
│  Client  │────────▶│  UrlController   │────────▶│  Redis   │
└──────────┘         │  (REST API)      │◀────────│  (cache) │
                     └────────┬─────────┘         └──────────┘
                              │ cache miss
                              ▼
                     ┌──────────────────┐
                     │   UrlService     │
                     │  (cache-aside)   │
                     └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │  PostgreSQL DB   │
                     └──────────────────┘
```

### Caching Strategy

This service uses the **cache-aside pattern**:

1. **Read path:** check Redis first → on miss, query DB → populate cache for next request
2. **Write path:** persist to DB → eagerly write to cache (write-through) so first read is a hit
3. **TTL:** 5 minutes — balances cache hit rate vs. memory usage. URLs are immutable, so longer TTLs are safe; this value can be tuned based on traffic patterns

### Why Redis over in-memory cache?

- Survives application restarts
- Shared across multiple service instances (horizontal scalability)
- Industry-standard for distributed caching at scale

---

## Quick Start

### Run with Docker Compose (recommended)

```bash
git clone https://github.com/<your-username>/url-shortener.git
cd url-shortener
docker-compose up --build
```

The service will be available at `http://localhost:8080`.

### Run locally (without Docker)

Requires Java 17, Maven 3.9+, PostgreSQL 16, Redis 7.

```bash
# Start Postgres and Redis somewhere first
mvn spring-boot:run
```

---

## API Reference

### Create short URL

```http
POST /api/v1/urls
Content-Type: application/json

{
  "longUrl": "https://example.com/some/very/long/path",
  "ttlSeconds": 3600
}
```

**Response 201:**

```json
{
  "shortCode": "aB3xK9z",
  "shortUrl": "http://localhost:8080/aB3xK9z",
  "longUrl": "https://example.com/some/very/long/path",
  "createdAt": "2025-04-24T10:15:00",
  "expiresAt": "2025-04-24T11:15:00",
  "clickCount": 0
}
```

### Redirect

```http
GET /{shortCode}
```

Returns `301 Moved Permanently` with `Location` header pointing to the original URL.

### Get URL stats

```http
GET /api/v1/urls/{shortCode}
```

Returns the same payload as creation, with current `clickCount`.

### Health check

```http
GET /actuator/health
```

---

## Testing

```bash
mvn test
```

Includes unit tests covering:

- URL creation with collision handling
- Cache hit path
- Cache miss → DB fallback → cache population
- Not-found error handling

---

## Design Decisions

### Why Base62 over UUID or sequential IDs?

- **UUIDs** are too long (36 chars) for short URLs
- **Sequential IDs** leak business information (number of URLs created) and are predictable
- **Base62** of length 7 gives ~3.5T combinations, URL-safe characters, and no information leakage

### Why cache-aside over write-through-only?

Cache-aside is more resilient to cache failures — if Redis is down, the system degrades gracefully to DB-only reads. Write-through-only means cache failures could cause data inconsistency.

### Why store click counts in DB instead of Redis only?

Persistence. Redis is an ephemeral cache; click counts need to survive Redis restarts and evictions. The trade-off is write load on the DB, which could be batched or moved to a streaming pipeline (Kafka → analytics DB) for higher scale.

### Rate limiting trade-offs

The current implementation uses **in-memory** Bucket4j buckets — fine for single-instance deployment. For multi-instance deployment, Bucket4j supports a Redis-backed proxy to share state across instances.

---

## Production Considerations

This project is built as a learning/portfolio piece, but here's what I would add for a true production deployment:

- **Distributed rate limiting** via Bucket4j Redis proxy
- **Cache stampede protection** using request coalescing or probabilistic early expiration
- **Bloom filter** in front of DB lookups to short-circuit known-missing keys
- **Read replicas** for the DB to scale read traffic
- **Async click counting** via Kafka → batch writes to DB
- **Custom alias support** (vanity URLs)
- **JWT-based auth** for per-user URL ownership
- **Prometheus metrics** + Grafana dashboards
- **Load testing** with k6 or Gatling

---

## Tech Stack

| Layer            | Technology                            |
|------------------|---------------------------------------|
| Language         | Java 17                               |
| Framework        | Spring Boot 3.2                       |
| Persistence      | Spring Data JPA + PostgreSQL 16       |
| Cache            | Spring Data Redis + Redis 7           |
| Rate Limiting    | Bucket4j 8                            |
| Validation       | Jakarta Bean Validation               |
| Build            | Maven 3.9                             |
| Containerization | Docker, Docker Compose                |
| Testing          | JUnit 5, Mockito, AssertJ             |

---

## Project Structure

```
src/main/java/com/rohan/urlshortener/
├── controller/   # REST endpoints
├── service/      # Business logic, caching, rate limiting
├── repository/   # JPA repositories
├── model/        # JPA entities
├── dto/          # Request/response DTOs
├── exception/    # Custom exceptions + global handler
└── config/       # Spring configuration
```

---

## License

MIT — feel free to fork and adapt.

---

**Built by Rohan Dubey** · [LinkedIn](https://linkedin.com/in/rohandubey3598)
