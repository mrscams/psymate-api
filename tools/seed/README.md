# tools/seed : données fictives et tests de charge (M7-01)

Générateur de patients fictifs pour PsyMate (C3, E5.5, G Q22). Il n'écrit **jamais** en SQL : tout passe par l'API REST d'une instance locale, donc par les mêmes règles que l'application (séance obligatoire, versions, matrice des liens E5.3, `author_type = 'practitioner'`).

Projet Maven autonome (`com.psymate.tools:psymate-seed`), **sans aucune dépendance d'exécution** : JDK 21 seul, JSON maison (`Json`), `java.net.http` pour les appels.

## Prérequis

```bash
make up      # Postgres + Mailpit (Mailpit lit les e-mails de confirmation des comptes du seed)
make run     # API sur http://localhost:8080, profil local (par défaut)
```

## Commandes

```bash
make seed SIZE=small    # défaut : 5 patients, environ 200 entrées chacun (moins de 2 minutes)
make seed SIZE=medium   # 100 patients ordinaires + 5 dossiers lourds (voir « Tailles »)
make seed SIZE=demo     # le patient DEMO seul
make perf               # tests de charge : ./mvnw -B -Pperf verify, rapport dans target/perf-report.md
```

`make seed` compile l'outil (`./mvnw -q -f tools/seed/pom.xml ... package`) puis lance :

```bash
java -cp tools/seed/target/classes com.psymate.seed.Seed --size small|medium|demo \
     [--api http://localhost:8080] [--mailpit http://localhost:8025] [--workers N] [--seed 20261101]
```

- `--workers` : nombre de workers parallèles (défaut : le plus petit de 8 et du nombre de patients à créer). Chaque worker a ses propres sessions HTTP et donc sa propre enveloppe de limitation de débit (600 requêtes par minute et par session, G Q24) ; un `429` est rejoué après `Retry-After`.
- `--seed` : graine du générateur (défaut `20261101`). Même graine, même jour : même jeu de données. Les dates sont relatives à aujourd'hui (minuit UTC), toutes les séances générées sont passées.

Sous Windows sans shell POSIX, `make` ne sait pas lancer `./mvnw` : exécuter les deux commandes ci-dessus à la main (ou depuis Git Bash).

## Comptes et mot de passe

| Praticien | E-mail | Profession | Patients (small, medium) |
| --- | --- | --- | --- |
| Claire Martin | `claire.seed@example.test` | psychologue | environ 40 % ; le patient DEMO |
| Karim Haddad | `karim.seed@example.test` | psychiatre | environ 60 %, surtout les médicaments |

Mot de passe unique des deux comptes : `Donnees-Fictives-2026` (valeur de développement, sans valeur hors du poste local). Les comptes sont créés au premier lancement (inscription, confirmation de l'e-mail par le lien lu dans Mailpit, connexion) ; ensuite le seed se contente de se connecter.

## Tailles

| Taille | Contenu | Durée indicative |
| --- | --- | --- |
| `demo` | 1 patient `DEMO · Camille R.` (Claire), 18 mois, **exactement 20 séances**, 88 entrées écrites à la main (`DemoStory`) | environ 5 s |
| `small` | 5 patients ordinaires (2 chez Claire, 3 chez Karim), 180 à 220 entrées chacun sur 1 à 4 ans | environ 20 s |
| `medium` | **100 patients ordinaires** (environ 200 entrées) **+ 5 dossiers lourds** (2 000 entrées, environ 1 500 liens, 10 ans) | environ 9 min d'écriture (voir ci-dessous) |

**Pourquoi `medium` n'est pas « 100 patients × 2 000 entrées »** : le seed passe par l'API, et un dossier de 2 000 entrées sur 10 ans représente environ 2 500 requêtes (entrées, ouverture et clôture de chaque séance, corrections). 100 de ces dossiers demanderaient 250 000 requêtes, soit plus de 50 minutes au débit autorisé de 8 workers (8 × 600 par minute), sans compter le temps serveur : intenable pour un jeu de données de développement. `medium` est donc « 100 × environ 200 + 5 lourds » ; les cibles de latence de A6 se mesurent sur les dossiers lourds (5 000 entrées au plus dans A6), pas sur le nombre de patients, car toutes les requêtes sont bornées à un patient (index sur `patient_id`). `make perf` mesure un dossier lourd isolé, dans une base de test jetable.

Mesure de `medium` (8 workers, API locale, poste de développement) : environ 50 000 requêtes, **9 minutes** d'écriture, plus le contrôle final (lecture seule, 32 000 éléments de timeline). La limite de 600 requêtes par minute et par session est le facteur limitant : 8 workers donnent au mieux 4 800 requêtes par minute. Plus de workers accélèrent d'autant (`--workers 16`), au prix d'une charge plus forte sur l'API locale.

## Ce que génère le seed (C3)

- Prénoms fictifs (plus de 60) et initiale du nom ; naissance entre 1958 et 2006 (adulte au début de l'historique) ; `displayName` du type `SEED · Léa M.` (ou `DEMO · Camille R.`).
- Historique de 1 à 4 ans (dossiers lourds : 10 ans ; DEMO : 18 mois) ; séances toutes les 1 à 3 semaines, de 8 h à 19 h UTC, 20 à 60 minutes, surtout en présentiel.
- Situations thématiques (travail, couple, famille, deuil, santé, études, argent, sommeil, logement, amitiés, projets) qui s'ouvrent à une séance et se ferment 1 à 18 mois plus tard (PATCH), 3 au plus actives en même temps, 2 à 3 encore ouvertes à la fin.
- Ressentis dont l'émotion vient des situations actives et dont l'intensité vaut 2 à 4, plus 2 par situation négative active, plus un bruit, bornée à 0..10 (sans situation active : calme, sérénité, joie, intérêt, entre 2 et 6) ; la vue d'ensemble et le graphe montrent donc des motifs.
- Événements, notes de séance (corpus de plus de 40 phrases), médicaments avec changements de posologie (nouvelle entrée et fin de période posée sur la précédente), surtout chez Karim.
- Liens manuels plausibles qui respectent la matrice E5.3, sans doublon, 20 au plus par entrée, vers des entrées antérieures seulement ; les dossiers lourds ont des situations pivots (plus de 80 liens).
- Environ 10 % de dates imprécises (jour, mois, année, période, phase de vie « enfance »), environ 5 % d'entrées privées (notes, ressentis, événements), tags du patient posés sur environ 30 % des entrées.
- `DemoStory` (G Q22) : récit écrit à la main de Camille R. : surmenage au travail, arrêt, sertraline 50 puis 100 mg, reprise à mi-temps, tensions de couple, deuil d'une grand-mère raconté sept mois après (précision « mois »), réflexion sur le travail. Deux situations restent actives, trois entrées sont privées. **À relire par le propriétaire du projet** avant d'être utilisé pour l'onboarding.

Le plan est produit par un générateur pur et déterministe (`Scenario`, sans HTTP) puis vérifié (`Scenario.validate` : séance pour toute entrée, matrice, doublons, précisions, visibilités) **avant** la première écriture.

## Idempotence

Avant d'écrire, le seed liste les patients du praticien (toutes les pages). Un patient du plan dont le `displayName` existe déjà est **ignoré** (compté « déjà présent »). Relancer `make seed` ne duplique donc rien ; seul un plan plus grand ajoute les patients manquants. Si l'écriture d'un patient échoue en route, son dossier partiel est archivé (suppression logique) pour qu'une relance le recrée proprement.

## Garde-fous (code de sortie 2, rien n'est écrit)

1. `--api` et `--mailpit` doivent viser `localhost`, `127.0.0.1` ou `::1`.
2. `GET /actuator/health` doit répondre 200 : sinon « L'API locale ne répond pas sur http://localhost:8080 : lance make up puis make run. »
3. `GET /actuator/info` doit contenir `"profile":"local"` (indicateur posé par `application.yml` dans le seul profil `local`) : sinon « L'API ne tourne pas en profil local : seed refusé. »
4. Mailpit doit répondre.

Codes de sortie : `0` tout est écrit et le contrôle final passe ; `1` erreur d'exécution (réponse HTTP inattendue : statut et corps `problem+json` affichés) ou contrôle final en échec ; `2` garde-fou ou option invalide.

## Contrôle final

Lecture seule, par l'API : pour chaque patient du plan (créé ou déjà présent), pagination de la timeline (200 par page, `includePrivate=true`) et vérification que `authorType` vaut `practitioner` partout et que toute entrée autre qu'une séance porte un `sessionId`. Affiche par exemple : `Contrôle : 1252 entrées, author_type = practitioner partout, session_id présent partout : OK`.

## Limites

- La séance d'accueil de chaque patient est créée de façon asynchrone par l'application (événement `PatientCreated`), à l'instant de l'écriture : elle est datée d'aujourd'hui et le seed n'y rattache rien (il rattache chaque entrée à une séance explicite qu'il a lui-même ouverte).
- `closedAt` d'une séance est l'instant de clôture, donc celui du seed, pas la fin historique de la séance (qui est `occurred.to`).
- Les corrections (PATCH) créent des versions, sauf dans la fenêtre de grâce de 1 h 15 : comme le seed écrit vite, la plupart des corrections de situations et de médicaments modifient l'entrée en place.
- Les tags du praticien (`travail`, `couple`, `famille`...) sont créés au besoin ; un tag déjà présent est réutilisé.
- Le seed ne supprime rien : `make db-reset` repart d'une base vide.
- L'idempotence se fait sur le nom d'affichage : deux graines différentes (ou deux tailles) peuvent produire le même `SEED · Prénom I.` ; ce patient est alors considéré comme déjà présent et n'est pas recréé.

## Tests de charge (`make perf`)

Le profil Maven `perf` du pom racine ajoute `tools/seed/src/main/java` aux sources de **test** (sans copie ni dépendance) et compile `src/test/java/com/psymate/perf`, ignoré du build par défaut (`./mvnw verify`, CI comprise). `HeavyDossierPerfIT` construit un dossier lourd par l'API (MockMvc, Testcontainers `pgvector/pgvector:pg16`), attend le drainage des événements, lance `ANALYZE`, puis mesure (5 échauffements, 40 exécutions) la timeline, la recherche, la vue d'ensemble et le graphe, et écrit `target/perf-report.md` : p50, p95, max et cible A6 par requête. La cible n'est pas bloquante (le test n'échoue qu'au-delà de 5 s) : c'est le rapport qui dit si elle est tenue.

Paramètres : `-Dperf.entries=2000 -Dperf.links=1500`.
