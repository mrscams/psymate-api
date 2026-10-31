.PHONY: up down seed test run contract db-reset progress log

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

# Suivi d'avancement (D9) : la page docs/progress/index.html est regeneree a partir des evenements.
progress:
	node tools/progress/progress.mjs build

# Un agent qui termine, se bloque ou passe en revue enregistre un evenement :
#   make log TASK=M0-04 AGENT=backend-module-dev STATUS=done SUMMARY="..." [PR=url] [NOTES="..."] [NEXT="..."]
log:
	node tools/progress/progress.mjs add --task "$(TASK)" --agent "$(AGENT)" --status "$(STATUS)" --summary "$(SUMMARY)" --notes "$(NOTES)" --pr "$(PR)" --next "$(NEXT)" --branch "$(BRANCH)" --at "$(AT)"
