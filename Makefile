.PHONY: up down seed perf test verify-fast run contract db-reset progress log admin

up:
	docker compose up -d --wait

down:
	docker compose down

# Donnees fictives (M7-01, tools/seed) : make seed SIZE=small|medium|demo ; l API doit tourner en profil local.
SIZE ?= small

seed:
	./mvnw -q -f tools/seed/pom.xml -DskipTests -Dspotless.check.skip=true package
	java -cp tools/seed/target/classes com.psymate.seed.Seed --size $(SIZE)

test:
	./mvnw verify

# Meme verification que ./mvnw verify, en parallele comme la CI : build une fois, puis unitaires + tranches
# d'IT (tools/it-slices.txt) chacune dans son processus Maven ; resume par voie. Docker requis.
# Sous Windows : bash tools/verify-fast.sh (Git Bash). ./mvnw verify reste la reference avant une PR.
verify-fast:
	bash tools/verify-fast.sh

# Tests de charge (M7-01) : dossier de 2 000 entrees / 1 500 liens, rapport dans target/perf-report.md. Docker requis, 5 a 10 min.
perf:
	./mvnw -B -Pperf verify

run:
	./mvnw spring-boot:run

# Regenere contract/openapi.yaml (seul ContractIT tourne, en mode ecriture) ; a commiter ensuite.
contract:
	./mvnw -B -q -Dcontract.write=true -Dit.test=ContractIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dspotless.check.skip=true verify

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

# Premier administrateur (M7-08, G Q5) : `make admin EMAIL=...` ; sans EMAIL, ADMIN_EMAIL de .env.
# Le mot de passe a usage unique est affiche une seule fois dans le terminal (voir docs/ops/admin.md).
# Charge .env si present ; EMAIL en ligne de commande prime, l'environnement n'est pas consulte.
-include .env
ifneq ($(origin EMAIL),command line)
EMAIL := $(ADMIN_EMAIL)
endif

admin:
	@test -n "$(EMAIL)" || { echo "EMAIL manquant : make admin EMAIL=... (ou ADMIN_EMAIL dans .env)"; exit 1; }
	./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local,cli -Dspring-boot.run.arguments="--create-admin=$(EMAIL)"
