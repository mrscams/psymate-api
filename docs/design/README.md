# docs/design — maquettes Nordique pour les agents

Mis à jour le 29 octobre 2026 : renommages et décisions de docs/G-decisions.md (PsyMate, psymate-web ; agenda hors V1, signalements V1.1, tâche M1-14b). Quand une fiche et G se contredisent, G l'emporte.

Chaque écran est livré en trois fichiers du même nom :

| Fichier | Usage par l'agent |
|---|---|
| `NN-ecran.md` | **Lire en premier.** Objectif, route, API, structure, règles, tokens, accessibilité, critères d'acceptation. C'est la source de vérité fonctionnelle. |
| `NN-ecran.html` | Maquette statique autonome (ouvrir dans un navigateur ; Google Fonts Inter + JetBrains Mono). Référence de mise en page et de contenu exemple. **Ne pas copier-coller dans Angular** : reproduire la structure avec Angular Material M3 + tokens SCSS. Le nom d'application affiché est **PsyMate** même si une maquette montre encore « Suivi ». |
| `NN-ecran.png` | Capture 1440 px de large, pour comparaison visuelle rapide et revue. |

## Écrans livrés

| N° | Écran | Doc B | Module(s) backend | Feature Angular |
|---|---|---|---|---|
| 01 | Liste des patients | écran 1 | `patients`, `search` | `patients` |
| 02 | Vue d'ensemble patient · **complète** | écran 2 | `overview`, `clinical`, `events` | `patient-overview` |
| 02b | Vue d'ensemble patient · **simple « Récit »** (défaut) | écran 2 | idem | `patient-overview` |
| 04 | Graphe patient | écran 4 | `linking`, `clinical` | `patient-graph` |
| 07 | Recherche dans le dossier | écran 7 | `search` | `patient-search` |
| 09 | Fiche publique d'un praticien | écran 9 | `directory` | `public-directory` |
| 10 | Mon profil public (affichage + modification) | écran 10 | `directory`, `identity` | `account-profile` |
| 11 | Connexion | — | `identity` | `auth` |
| 12 | Inscription (profession, téléphone, adresse) | — | `identity` | `auth` |
| 13 | Dossier professionnel (justificatifs, vérification) | — | `identity.verification` | `account-verification` |
| 14 | Préférences praticien | — | `identity`, `vocabulary` | `account-preferences` |
| 15 | Agenda synchronisé Google (détail à droite) | — | **hors V1 (G Q25)** : aucun module, aucune tâche | `agenda` (plus tard) |
| 20 | Admin · Validation des praticiens | — | `identity.admin` (M1-14) | `admin` |
| 06 | Éditeur de lien (panneau sur le graphe) | écran 6 | `linking` | `patient-graph` |
| 15b | Panneau « Nouveau rendez-vous » (récurrence, aperçu Google, conflits) | — | **hors V1 (G Q25)** | `agenda` (plus tard) |
| 16 | Fiche patient (création + modification, drapeaux, tags, archivage) | — | `patients` | `patient-form` |
| 17 | Émotions personnalisées (ajout, fusion ; promotion en V2) | — | `vocabulary` | `account-emotions` |
| 21 | Admin · Praticiens (tous les comptes, suspendre / réactiver) | — | `identity.admin` (M1-14b) | `admin` |
| 22 | Admin · Journal (décisions, accès documents, sécurité ; append-only) | — | `identity.admin` (M1-14b, vue `admin_journal_v`) | `admin` |
| 23 | Admin · Signalements de l'annuaire | — | `directory`, `identity.admin` — **V1.1 (G Q26)** | `admin` (plus tard) |
| 24 | Comment ça marche (page publique) | — | `directory` (statique) | `public-pages` |
| 25 | Confirmation d'e-mail (en attente + lien expiré) | — | `identity` | `auth` |
| 26 | Mot de passe oublié (demande + nouveau mot de passe) | — | `identity` | `auth` |
| 27 | Compte suspendu / refusé (lecture seule, export, recours) | — | `identity`, `identity.verification` (recours : M1-14b ; export : M6-05) | `auth` |

Encore à maquetter : 3 Timeline (canevas Nordique existant, à ajouter au pack), 5 saisie en séance et 8 annuaire (à repasser en Nordique), historique des versions, planche d'états vides/erreur, admin › Vocabulaire de l'annuaire (liste fermée des spécialités et publics, M1-14b), déclinaison mobile.

## Parcours praticien (décision du 25 oct.)
`12 Inscription` (ouverte à tous, G Q21) → e-mail confirmé → `13 Dossier professionnel` (accès à l'outil clinique **ouvert** dès cette étape) → `20 Validation admin` → `approved` → `10 Profil public` peut être publié → `09 Fiche publique` visible. Étape e-mail : `25 Confirmation`. Refus ou suspension : `27 Compte suspendu` (lecture seule + recours), piloté depuis `21 Praticiens` ; signalements d'une fiche : `23` (V1.1). États et verrous : tableau dans `13-dossier-professionnel.md`.

## Conventions communes
- Palette et tokens : `Etude-couleurs-Nordique.md`. Aucune couleur en dur (`eslint no-hardcoded-color`).
- Éléments partagés : barre d'application ; en-tête patient (01/02/04/07/06/16) ; navigation « Mon compte » (10/14/13/17) ; layout public (09/11/12/24/25/26/27) ; barre admin (20/21/22/23) ; panneau latéral `role="dialog"` (06/15b/15). À implémenter une fois dans `shared/ui/*`.
- Formes par type d'entrée : note rectangle, événement losange, situation barre, ressenti cercle, séance carré arrondi, médicament capsule.
- Le lavande `#8B7BD6` est réservé aux ressentis ; les couleurs de familles d'émotions servent uniquement aux courbes et pastilles de famille.
- Préférences par compte (`practitioner.preferences` JSONB) : une seule source de vérité pour les bascules présentes sur plusieurs écrans (`14-preferences-praticien.md`).
- Textes : français d'abord (G Q23) ; les fichiers de traduction EN peuvent suivre sans bloquer une tâche. Pas de pied de page « numéros d'urgence » sur les pages publiques (G Q20).
- Textes et exemples (Marie D., Claire Moreau, Dr Lenoir…) sont des données de seed fictives ; `[TARIF]`, `[TÉLÉPHONE]`, `[E-MAIL]` sont des champs à renseigner.

## Prompt type pour un agent frontend
```
Implémente l'écran décrit dans docs/design/10-mon-profil-public.md (dépôt psymate-web).
Référence visuelle : docs/design/10-mon-profil-public.html (et .png).
Contrat API : openapi/openapi.yaml au tag épinglé dans CONTRACT_TAG (GET/PUT /api/me/profile).
Décisions : docs/G-decisions.md l'emporte sur la fiche en cas de contradiction.
Contraintes : Angular 22 standalone (zoneless, Signal Forms), Material 22 (M3), @ngrx/signals, tokens de docs/design/Etude-couleurs-Nordique.md,
tests Vitest + un scénario Playwright par critère d'acceptation du .md.
Ne modifie pas les composants partagés sans ouvrir une tâche séparée.
```
