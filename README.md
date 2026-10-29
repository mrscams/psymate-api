# psymate-api

PsyMate clinical follow-up platform API (Spring Boot 4.1, Java 21, PostgreSQL 17).

## Requirements

- JDK 21
- Docker Desktop running (Postgres for local dev and for tests)

## Run

```bash
./mvnw spring-boot:run
```

Spring Boot starts Postgres from `compose.yaml` automatically
(database `psymate`, user/password `psymate`, port 5432) and applies Flyway migrations
from `src/main/resources/db/migration`.

- Ping: http://localhost:8080/api/public/ping
- Health: http://localhost:8080/actuator/health

Every other endpoint requires authentication. Security is a placeholder for now (see `config/SecurityConfig`).

## Test

```bash
./mvnw verify
```

Tests start a throwaway Postgres with Testcontainers.
