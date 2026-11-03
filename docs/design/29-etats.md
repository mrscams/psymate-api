# Planche 29 · États vides, chargement, erreurs et notifications

> Spécification pour agents. Maquette : `29-etats.html` / `.png`. Palette : Nordique. Document B, flux 1 et B7 ; Document C, C5 (« chaque écran a ses trois états »). Composant partagé `shared/ui/state` posé en M1-07, revue de tous les écrans en M6-03, onboarding et aide en M7-03.

## Objectif
Donner à chaque écran ses trois états (vide, chargement, erreur) avec un seul composant et un seul vocabulaire visuel, afin qu'aucun écran ne soit jamais blanc, que toute erreur soit actionnable (réessayer, corriger, attendre) et qu'un nouveau praticien saisisse sa première entrée en moins de 3 minutes. La planche fait autorité sur la forme ; chaque fiche d'écran garde ses phrases propres.

## Composants
- **État** (`shared/ui/state`) : entrées `kind: 'empty' | 'loading' | 'error'`, `title`, `message`, `action?: {label, shortcut?, handler}`, `details?` (texte technique repliable, affiché seulement en `error`). Il prend la taille du bloc qu'il remplace : page, colonne, carte ou cellule. `loading` rend un squelette aux dimensions du contenu (variantes `row`, `card`, `block`, `profile`) ; `empty` rend l'illustration gris-bleu, le titre, la phrase et l'action ; `error` rend l'icône d'erreur, le titre, la phrase, « Réessayer » et le détail.
- **Toast** (`shared/ui/toast`) : `level: 'success' | 'info' | 'warning'`, `message`, `action?` (ex. « Annuler »), pile de 3 au plus en bas à droite, 6 s par défaut, fermable. Jamais de toast d'erreur : une erreur s'affiche là où elle s'est produite.
- **Bandeau global** (`shared/ui/banner`) : `level: 'warning' | 'success' | 'info'`, sous la barre d'application, sur toutes les pages, non fermable tant que l'état dure. Alimenté par le statut de vérification du compte (fiche 13) et par l'état réseau (hors ligne).
- **Confirmation destructive** (`shared/ui/confirm-destructive`) : boîte modale, `title`, `description`, `confirmWord?` (nom à retaper), bouton `--danger`. Le bouton reste inactif tant que le mot diffère ; sans `confirmWord`, le bouton est actif d'emblée.

## Structure
1. **Le composant État** : les trois variantes côte à côte, à l'échelle d'un bloc de timeline (vide avec « Saisir une entrée N », chargement `aria-busy`, erreur avec détail « 503 · service indisponible · réessayer »).
2. **États vides par écran** : six vignettes de 360 px (liste des patients à la première connexion avec patient DEMO, vue d'ensemble sans entrée, timeline vide, graphe sans lien, recherche sans résultat, annuaire sans résultat).
3. **Chargement** : squelettes d'une ligne de tableau, d'une carte de timeline, d'un bloc de vue d'ensemble, de la fiche publique.
4. **Erreurs** : 422 champ par champ, 403 e-mail non confirmé, 403 compte suspendu, 409 lien existant, 423 verrouillage avec compte à rebours, 429, hors ligne, 500 avec identifiant de demande.
5. **Notifications** : trois toasts, bandeau global ambre de la fiche 13, boîte d'archivage d'un patient.
6. **Page d'aide des raccourcis** : panneau `N` `/` `L` `1` `2` `3` `M` `Échap` `Ctrl+Entrée` `Ctrl+S` et règles d'affichage.

## Règles d'usage
- **Vide** quand la requête a réussi et renvoie zéro élément ; le titre dit ce qui manque, la phrase dit comment le faire apparaître, l'action est le geste le plus court (jamais plus d'une action primaire). Une liste filtrée vide propose « Effacer les filtres » ou « Élargir la période », pas « Créer ».
- **Chargement** seulement au premier rendu ou au-delà des fenêtres préchargées ; une liste déjà affichée reste visible et grisée (300 ms) au lieu de redevenir squelette. L'en-tête patient s'affiche avant les blocs.
- **Erreur** dans le bloc concerné, les autres blocs restent lisibles ; le contenu précédent n'est jamais vidé. Niveau *erreur* (`--danger`) pour un échec système ou un refus définitif (5xx, 403 suspendu, 423) ; niveau *avertissement* (`--warning`) quand une action du praticien ou l'attente suffit (403 e-mail, 409, 429, hors ligne).
- **Jamais de rouge sur un patient, une entrée ou une émotion** : le `--danger` porte l'action destructive ou l'état système, le nom du patient reste en `--text-primary` ; les flags restent ambre (`#B8862E`).
- **Information jamais portée par la couleur seule** : icône + mot du niveau (« Entrée enregistrée », « Avertissement », « Trop de tentatives »).
- Textes i18n FR d'abord (`fr.json` obligatoire, `en.json` non bloquant, G Q23) ; les messages de champ viennent du `message` de l'API, déjà dans la langue d'`Accept-Language`, le client n'en traduit aucun.

## Correspondance avec le contrat
Tous les codes ci-dessous sont documentés dans `docs/api-reference.md` (réponses `application/problem+json`, RFC 9457 : `status`, `code`, `detail`, `errors[{field, code?, message}]` et champs complémentaires).

| HTTP | `code` | Où | État affiché |
|---|---|---|---|
| 401 | `UNAUTHENTICATED` | toute route | redirection vers 11 Connexion, retour à l'URL demandée après connexion |
| 401 | `INVALID_CREDENTIALS` | `POST /api/auth/login` | message unique sous le formulaire (e-mail inconnu, mot de passe ou code faux) |
| 403 | `EMAIL_NOT_CONFIRMED` | toute route, login | bloc avertissement « E-mail non confirmé » + « Renvoyer l'e-mail » (`POST /api/auth/email/resend`, 1/min, 5/j) |
| 403 | `ACCOUNT_SUSPENDED` | `POST /api/auth/login` | bloc erreur « Compte suspendu », redirection vers 27 ; un compte `closed` renvoie le même code sans recours |
| 403 | `PATIENT_FORBIDDEN` | patients, entrées, liens, timeline | état erreur du bloc « Ce dossier ne vous est pas accessible », sans « Réessayer » |
| 409 | `LINK_EXISTS` (+ `existingLinkId`) | `POST /api/entries/{id}/links` | avertissement « Ce lien existe déjà · Modifier le type ? », « Modifier le type » ouvre `PATCH /api/links/{existingLinkId}` |
| 422 | `VALIDATION_FAILED` + `errors[]` | tout formulaire | résumé `role="alert"` en tête avec liens, message sous chaque champ ; `code` de champ absent dans le module patients |
| 423 | `ACCOUNT_LOCKED` (+ `retryAfterSeconds`) | login, changement de mot de passe, 2FA | bloc erreur avec compte à rebours, bouton inactif jusqu'à zéro |
| 429 | `RATE_LIMITED` (+ `retryAfterSeconds`) | toute route | avertissement « Trop de requêtes, réessayez dans N s », rejeu automatique, saisie conservée (G Q24 : 600 req/min/session) |
| 5xx, réseau | `detail` générique ou aucune réponse | toute route | état erreur du bloc + « Réessayer » ; sans réponse du tout : bandeau « Connexion perdue · vos brouillons sont conservés » |

## Tokens
- Erreur `#9A2F22` sur `#FBE9E7` ; avertissement `#7A5A12` sur `#FBF1DC` ; succès `#1E6B45` sur `#E4F2EA` ; information `#2C4C99` sur `#E3EAF8`.
- Squelettes `--surface-sunken #E4E8F0`, formes du type d'entrée conservées ; illustration d'état vide en `--border-strong #B9C2D1` et `#E4E8F0`, jamais de couleur de famille d'émotion.
- Compte à rebours, codes HTTP, en-têtes, identifiants : JetBrains Mono ; touches de raccourci : `kbd` mono 11 px, bordure basse 2 px.

## Accessibilité
- `empty` et le résumé de recherche : `role="status"` ; `error`, résumé 422, 403, 409, 423 : `role="alert"` ; `loading` : conteneur `aria-busy="true"`, texte masqué « Chargement… » lu une fois, squelettes `aria-hidden`.
- Compte à rebours (423, 429) : `aria-live="polite" aria-atomic="true"`, annoncé à la minute, pas à la seconde ; hors ligne : `role="status" aria-live="polite"`.
- 422 : le résumé reçoit le focus à la soumission puis le premier champ fautif ; chaque champ fautif porte `aria-invalid="true"` et `aria-describedby` vers son message ; les champs valides gardent leur saisie.
- « Réessayer » rend le focus au bloc rechargé ; la boîte de confirmation est `role="dialog" aria-modal="true"`, focus initial sur le champ, `Échap` annule ; squelettes et illustrations sans animation sous `prefers-reduced-motion`.

## Critères d'acceptation
- [ ] Chaque écran (01, 02, 02b, 04, 06, 07, 09, 10, 13, 14, 16, 17, 20 à 27) a ses trois états en revue visuelle et un e2e par état (M6-03).
- [ ] Un nouveau praticien saisit sa première entrée en moins de 3 minutes depuis la liste vide, avec ou sans le patient DEMO (M7-03, test utilisateur).
- [ ] 422 : messages sous les champs fautifs, résumé en tête, focus sur le premier champ fautif, champs valides conservés.
- [ ] 429 : message avec le délai de `retryAfterSeconds`, requête rejouée à zéro, aucune perte de saisie.
- [ ] Hors ligne pendant la saisie : brouillon conservé localement, renvoyé au retour du réseau, toast de succès.
- [ ] Patient DEMO supprimé en un clic et une confirmation simple, sans retaper le nom ; un vrai patient s'archive seulement après avoir retapé son nom exact.
- [ ] Aucune couleur `--danger` sur un nom de patient, une entrée ou une émotion (test axe-core + revue) ; zéro violation axe « serious » sur le panneau des raccourcis.

## Écarts avec la maquette
- Touche `?` pour ouvrir l'aide des raccourcis : non spécifiée dans B. **À suivre** par défaut (hors champ de saisie), en plus du lien « Voir les raccourcis » et du menu du compte.
- Patient « Nora K. » dans les vignettes sans entrée : données fictives de maquette, **à ignorer** (le seed n'a que Marie D. et le patient DEMO).
- Identifiant de demande `req_…` et en-tête `X-Request-Id` sur le 500 : aucun en-tête de ce nom dans le contrat. **À afficher seulement si l'API renvoie un en-tête**, à vérifier côté API ; sinon montrer `detail` seul.
- Durées : toasts 6 s, redirection 5 s après 403 suspendu : valeurs par défaut proposées, **à suivre** sauf décision contraire en M6-03.
- Effet de `M` (timeline chronologique ↔ par séance, graphe complet ↔ centré) : extrapolé de B « bascule de mode » de la timeline ; **à suivre** pour la timeline, à confirmer pour le graphe avec la fiche 04.
- Valeur et message du 422 : le module patients renvoie `errors[] {field, message}` sans `code` (schéma FieldProblem) ; la planche a été corrigée (date future, pas de code affiché). Les codes de champ des autres modules ne s'affichent jamais, ils servent aux tests.

## Hors périmètre V1
Mode sombre des états (variables de l'étude §8, M6), déclinaison mobile, toast d'erreur, états des écrans agenda (15, 15b, G Q25) et signalements (23, V1.1), message d'introduction des suggestions IA (préparé pour V2, `ai`).
