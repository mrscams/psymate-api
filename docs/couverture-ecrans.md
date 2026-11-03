# Couverture des écrans V1 par les fiches design

2 novembre 2026 · Comparaison entre les écrans que la documentation demande (carte des écrans B2/B8, flux B3, tâches `web` de C2/C6, contrat d'API) et les fiches livrées dans `design/` (24 fiches `.md`, chacune avec son `.html` et son `.png`). Pour chaque manque : ce qui existe déjà pour le remplacer, la tâche qui en a besoin, et si cela peut attendre.

## 1. Ce qui est couvert

| N° | Écran | Fiche | Maquette | Tâches web |
| --- | --- | --- | --- | --- |
| 01 | Liste des patients | `01-liste-patients.md` | html + png | M1-06 |
| 02 | Vue d'ensemble complète | `02-vue-ensemble-patient.md` | html + png | M4-05 |
| 02b | Vue d'ensemble simple « Récit » | même fiche (section vue simple) | `02b-vue-ensemble-simple.html` + png | M4-07 |
| 04 | Graphe | `04-graphe-patient.md` | html + png | M2-06 |
| 06 | Éditeur de lien (panneau) | `06-editeur-de-lien.md` | html + png | M2-04 |
| 07 | Recherche dans le dossier | `07-recherche-dossier.md` | html + png | M4-04, M4-08 |
| 09 | Fiche publique d'un praticien | `09-fiche-publique-praticien.md` | html + png | M5-05 |
| 10 | Mon profil public | `10-mon-profil-public.md` | html + png | M5-04 |
| 11 | Connexion (avec bloc OTP) | `11-connexion.md` | html + png | M0-07, M1-15 |
| 12 | Inscription | `12-inscription.md` | html + png | M0-07, M1-15 |
| 13 | Dossier professionnel | `13-dossier-professionnel.md` | html + png | M1-15 |
| 14 | Préférences | `14-preferences-praticien.md` | html + png | M1-16 |
| 16 | Fiche patient (création, modification, archivage) | `16-fiche-patient.md` | html + png | M1-06 |
| 17 | Émotions personnalisées | `17-emotions-personnalisees.md` | html + png | M1-09 |
| 20 | Admin · Validation des praticiens | `20-admin-validation-praticiens.md` | html + png | M7-07 |
| 21 | Admin · Praticiens | `21-admin-praticiens.md` | html + png | M7-07 |
| 22 | Admin · Journal | `22-admin-journal.md` | html + png | M7-07 |
| 24 | Comment ça marche | `24-comment-ca-marche.md` | html + png | M5-05 |
| 25 | Confirmation d'e-mail | `25-confirmation-email.md` | html + png | M0-07, M1-15 |
| 26 | Mot de passe oublié | `26-mot-de-passe-oublie.md` | html + png | M0-07 |
| 27 | Compte suspendu / refusé | `27-compte-suspendu.md` | html + png | M1-15 (état), M7-07 (recours) |

Hors V1, fiches conservées mais rien à implémenter : 15 Agenda, 15b Nouveau rendez-vous (G Q25), 23 Signalements (V1.1, G Q26).

## 2. Ce qui manque

Classé par urgence : **bloquant** = une tâche d'un lot proche ne peut pas démarrer sans ; **avant son lot** = à produire avant la tâche concernée, mais rien ne presse ; **peut attendre** = fin de V1.

| Écran manquant | Ce qui existe déjà | Tâche(s) web | Verdict |
| --- | --- | --- | --- |
| **5 Éditeur d'entrée** (panneau six types, précision temporelle, curseur d'intensité, séance présélectionnée, brouillon, « Plus » replié, création d'émotion custom en ligne) | B4 écran 5 (tableau des 7 zones), B5 (barre de commande, rail « Lié à »), maquette en ancienne palette dans `UI/Patient en séance · gris-bleu lavande` (export de canevas, pas encore en `.html` autonome) | M1-08 (lot 2), puis M2-05, M6-02 | **Bloquant.** C'est l'écran central du produit, la saisie en séance. B4 est assez précis pour écrire la fiche sans nouvelle maquette ; une maquette Nordique est souhaitable mais la fiche suffit pour lancer M1-08. |
| **Historique des versions** (panneau, entrées et fiche patient) | B2 (panneau V), B4 écran 5 zone 7, fiche 16 (« 3 versions »), API `GET /api/entries/{id}/versions`, `GET /api/patients/{id}/versions` | M1-08 (critère « corriger et voir 2 versions »), M1-06 | **Bloquant** avec la fiche 5 : une section de la fiche 5 et de la fiche 16 suffit (liste des versions, diff champ par champ, `grace-period` 1 h 15 affichée). |
| **Composants partagés** : barre d'application, en-tête patient permanent (nom, âge, drapeaux, séance courante, Démarrer / Clôturer, barre de capture), navigation Mon compte, layout public, barre admin, panneau latéral `role="dialog"`, composant « état vide / chargement / erreur » | Dispersés dans les fiches 01/02/04/07/06/16 (en-tête), 10/13/14/17 (Mon compte), 09/11/12/24–27 (public), 20–22 (admin) ; B2 « En-tête patient permanent » ; `design/README.md` « Conventions communes » | M1-07 (lot 2) les pose une fois pour tous | **Bloquant** pour M1-07 : pas une maquette à dessiner, mais une fiche `00-composants-partages.md` à extraire des fiches existantes, sinon chaque agent réinvente l'en-tête et les états. |
| **Compte et sécurité** (Mon compte) : activer / désactiver la 2FA (QR + secret, vérification, codes de secours, régénération), changer le mot de passe, changer l'e-mail, déconnecter les autres sessions, dernière connexion | Fiche 14 section 5 : un résumé avec un lien « Tout voir » vers une page qui n'a ni fiche ni tâche ; API complète : `/api/me/2fa/enable`, `/verify`, `/disable`, `/recovery-codes/regenerate`, `/api/auth/password/change`, `/api/auth/email/change` + `/confirm`, `/api/auth/sessions/revoke-others`, `/api/auth/me` | **Aucune tâche web** ; M1-15 ne couvre que le bloc OTP de la connexion | **Bloquant avant M7-07, à faire dès le lot 1.** Sans cet écran, aucun administrateur ne peut entrer : `make admin` (M7-08, G Q5) impose le changement de mot de passe puis la 2FA au premier login. Proposer une fiche 18 et une tâche `M1-17 web / account-security` à la suite de M1-15. |
| **3 Timeline** (axe vertical, cartes par type, bandes de situations, zoom, virtual scroll, modes chronologique / par séance, filtres, vue décennie) | B4 écran 3, A6 ; maquette **Nordique** dans `UI/Timeline · mode chronologique · palette Nordique` (export de canevas ; le README design dit « canevas existant, à ajouter au pack ») | M3-02, M3-03, M3-04 (lot 3) | **Avant son lot.** Fiche à écrire depuis B4 + html/png à exporter de la maquette existante. Rien ne bloque les lots 0–2. |
| **8 Annuaire public** (recherche, filtres, tri, liste / carte) | B4 écran 8, B3 flux 7 ; maquette en ancienne palette dans `UI/Annuaire public · gris-bleu lavande` (export de canevas) | M5-05 (lot 3) | **Avant son lot.** La fiche 09 fixe déjà le layout public ; la fiche 8 décrit la liste et les filtres (contrat : `GET /api/public/directory`, pagination par page). |
| **Données et export** (Mon compte) : exporter un patient (PDF / Markdown), exporter tout le compte | Fiche 27 ne couvre que le compte suspendu ; API `GET /api/patients/{id}/export`, `POST /api/me/export`, `GET …/{jobId}`, `…/file` (M6-05) | **Aucune tâche web** (M6-05 est `api / clinical` seulement) | **Peut attendre** (lot 5), mais il faut une tâche : un bouton « Exporter » dans la fiche 16 ou l'en-tête patient, et une page Mon compte › Données. Proposer `M6-06 web / account-data`. |
| **Admin · Vocabulaire de l'annuaire** (liste fermée des spécialités et publics, activer / désactiver) | Fiche 21 mentionne l'onglet ; `design/README.md` « encore à maquetter » ; API `GET/POST/PATCH /api/admin/vocabulary/directory` (M1-14b, G Q15) | M7-07 (lot 5) | **Peut attendre.** Petite liste CRUD ; fiche courte à écrire avant M7-07, pas de maquette indispensable. |
| **Planche d'états vides / chargement / erreur** | C5 « chaque écran a ses trois états », B3 flux 1 (état vide première connexion) | M6-03 (lot 5) pour la revue de tous les écrans | **Peut attendre** comme planche ; mais le **composant** « état » doit être dans la fiche composants partagés dès le lot 2, sinon M6-03 refait tous les écrans. |
| **Page d'aide des raccourcis, onboarding (patient DEMO, premier message)** | B3 flux 1, M7-03 ; `N` `/` `L` `1` `2` `3` `Échap` `M` listés en B2 | M7-03 (lot 6) | **Peut attendre.** |
| **Barre de commande et rail « Lié à »** | B5 complet (grammaire, aperçu, exemples), `UI/Patient en séance` | M6-01, M6-02, M2-05 | **Peut attendre** (lot 5) ; à décrire dans la fiche 5, l'en-tête (M1-07) réserve la place. |
| **Premier login administrateur** (mot de passe à usage unique → changement obligatoire → 2FA forcée) | G Q5, M7-08 ; `POST /api/auth/password/change` | M7-07 | Couvert par la fiche « Compte et sécurité » ci-dessus + une redirection forcée ; à noter dans cette fiche. |
| **Déclinaison mobile** | B7 (tablette, cible 44 px) ; `design/README.md` « encore à maquetter » | — | **Après V1.** |

## 3. Ce que cela change pour le document H

1. **Avant le lot 2** (M1-06, M1-07, M1-08, M1-09) : écrire `00-composants-partages.md` et `05-editeur-entree.md` (avec l'historique des versions et la place de la barre de commande). Une maquette Nordique de l'écran 5 est un plus, pas un préalable.
2. **Lot 1** : ajouter `M1-17 Compte et sécurité` (fiche 18 à écrire) après M1-15 ; c'est aussi ce qui permet de tester la 2FA de la connexion de bout en bout.
3. **Avant le lot 3** : fiches 3 (timeline) et 8 (annuaire), html/png exportés des maquettes de `UI/` (la timeline est déjà en Nordique ; l'annuaire et la saisie en séance sont à repasser en Nordique, cf. `00-contexte.md` « Maquettes »).
4. **Lot 5** : ajouter `M6-06 Données et export` et une fiche courte « Admin › Vocabulaire ».
5. **Lot 6** : rien à maquetter avant ; M7-03 et M7-04 s'appuient sur B3 et B7.

## 4. État au 2 novembre 2026 (soir)

Les sept écrans manquants ont maintenant leur **fiche `.md` et leur maquette `.html`** dans `design/` : 03 Timeline, 05 Éditeur d'entrée (avec l'historique des versions), 08 Annuaire public, 18 Compte et sécurité, 19 Données et export, 28 Admin · Vocabulaire, 29 Planche d'états. Chaque fiche a une section « Écarts avec la maquette » qui dit ce que l'agent suit et ce qu'il ignore. La fiche `00-composants-partages.md` est écrite ; les tâches web **M1-17** et **M6-06** et la série API **M10** sont dans `C-plan.md` C9. Pas de `.png` pour ces fiches (décision du 2 novembre : la maquette `.html` suffit).

Écarts avec le contrat découverts en écrivant les fiches, devenus les tâches **M10-01 à M10-07** (C9 ; aucune ne bloque les lots 0–2, M10-01 à M10-05 avant le lot 3 web, tag `contract-v0.10.0`) :

| Écart | Conséquence web en V1 | Suite proposée |
| --- | --- | --- |
| `GET /api/vocabulary/directory` exige une session praticien | Les pages publiques (08) ne peuvent pas charger les libellés de la liste fermée | Endpoint public `GET /api/public/directory/vocabulary` (ou lever l'authentification sur l'existant) ; en attendant, libellés embarqués depuis le seed V04 |
| `GET /api/public/directory` n'a pas de paramètre `specialties` (seulement `q`, `city`, `lat/lng/radiusKm`, `professions`, `modalities`, `audiences`, `languages`, `accepting`, `sort`, `page`) | Pas de filtre ni de facette par spécialité dans l'annuaire ; le sélecteur envoie le code dans `q` | Ajouter `specialties` et sa facette (M5-02 bis) |
| Aucun endpoint ne liste les sessions actives ni la dernière connexion | Carte Sessions réduite à « Déconnecter les autres appareils » ; carte Dernière connexion hors V1 | `GET /api/me/security` en V1.1 |
| `POST /api/auth/email/change` ne couvre qu'un compte `email_pending` | Bouton « Modifier l'e-mail » masqué pour un compte confirmé | Étendre l'endpoint (confirmation sur la nouvelle adresse) |
| Pas de restauration d'une version d'entrée (`POST /api/entries/{id}/restore` = désarchivage) | Historique des versions en lecture seule | Rien en V1 ; à décider en V1.1 |
| Export du compte : pas de mot de passe d'archive, pas de liste des jobs, pas de progression | Un seul job visible, états queued / running / done / failed / 410 | Rien en V1 |
| 409 `VOCABULARY_IN_USE` ne renvoie qu'un compteur | Pas de liste nominative des profils concernés ; lien vers Praticiens | Filtre par spécialité sur `GET /api/admin/practitioners` en V1.1 |
| `MeResponse` n'expose ni `role`, ni `twoFactorEnabled`, ni « changement de mot de passe requis » | L'état « première connexion admin » est déclenché par les 403 `PASSWORD_CHANGE_REQUIRED` / `TWO_FACTOR_REQUIRED` | Exposer ces trois champs dans `GET /api/auth/me` pour éviter un aller-retour |
| Module `patients` : `errors[]` sans `code` de champ (seulement `field`, `message`) | Les messages 422 viennent du serveur tels quels | Harmoniser avec les autres modules (`FieldProblem` avec `code`) |

Les trois maquettes de `UI/` (Timeline, Patient en séance, Annuaire) ne sont pas copiées dans `design/` : ce sont des exports de canevas (`Main.dc.html` + scripts de l'outil de maquettage), pas des `.html` statiques autonomes comme les autres fiches. Il faut en tirer un `.html` autonome et un `.png` au moment d'écrire la fiche. Aucun de ces fichiers n'est du code du projet : `psymate-web` est en Angular.
