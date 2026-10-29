.PHONY: up down seed test run contract db-reset

up:
	docker compose up -d --wait

down:
	docker compose down

seed:
	@echo "seed : le generateur arrive avec M7-01 (tools/seed), rien a faire pour l'instant."

test:
	./mvnw verify

run:
	./mvnw spring-boot:run

contract:
	@echo "contract : la generation de contract/openapi.yaml arrive avec M0-05, rien a faire pour l'instant."

db-reset:
	docker compose down -v
	docker compose up -d --wait
