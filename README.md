# psymate-api

API de PsyMate (Spring Boot 4.1, Java 21, PostgreSQL 16 + pgvector). Prérequis : JDK 21, Docker.

```bash
make up          # Postgres (:5432) + Mailpit (SMTP :1025, interface http://localhost:8025)
make run         # API sur http://localhost:8080 (profil local)
make test        # ./mvnw verify (Testcontainers, Docker requis)
make db-reset    # recrée la base locale depuis zéro (supprime le volume)
make down        # arrête les conteneurs
```

Santé : http://localhost:8080/actuator/health. `make seed` et `make contract` sont des
emplacements réservés (M7-01 et M0-05).
