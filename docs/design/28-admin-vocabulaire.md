# Écran 28 · Admin · Vocabulaire de l'annuaire

> Spécification pour agents. Maquette : `28-admin-vocabulaire.html` / `.png`. Palette : Nordique (barre admin sombre). Document A, module `identity.admin` + façade `directory.api` (M1-14b, G Q15). Tâche web : M7-07. **Ne dépend jamais des modules cliniques.**
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : barre admin sans onglet Signalements (déjà le cas de cette maquette, G Q26) ; le lien du `409` mène à `/admin/praticiens` sans filtre « publiés », que le contrat n'offre pas (fiche 21). Voir « Écarts avec la maquette ».

## Objectif
Administrer la liste fermée des spécialités et des publics de l'annuaire (G Q15) : ajouter, renommer, ordonner, désactiver ou réactiver une entrée. C'est la liste que les praticiens voient dans « Mon profil public » (écran 10) et que les visiteurs voient dans les filtres de l'annuaire (écran 11). On désactive, on ne supprime jamais. Aucune donnée patient.

## Route & module
- Route : `/admin/vocabulaire` (rôle `admin`, 2FA obligatoire ; garde de rôle côté front + `403 ADMIN_ROLE_REQUIRED` / `TWO_FACTOR_REQUIRED` côté API ; un praticien qui tape `/admin` est redirigé vers son tableau de bord).
- `GET /api/admin/vocabulary/directory?kind` → `{ items[] }` : toutes les entrées, actives ou non, des deux types ou d'un seul (`kind = specialty | audience`), triées par type, rang, libellé français ; non paginé. Item : `kind, code, labelFr, labelEn (null possible), sortOrder, active, usedByPublishedProfiles`.
- `POST /api/admin/vocabulary/directory` `{ kind, code, labelFr, labelEn?, sortOrder? }` → `201` entrée active ; sans `sortOrder`, rang suivant le dernier du type. `409 VOCABULARY_EXISTS` si le code existe pour ce type ; `422` : `REQUIRED` (code, labelFr), `INVALID_FORMAT` (code : minuscules, chiffres, underscores, 2 à 40 caractères, commence par une lettre), `TOO_LONG` (libellés > 120), `OUT_OF_RANGE` (sortOrder hors 0–32767), `INVALID_VALUE` (kind).
- `PATCH /api/admin/vocabulary/directory/{kind}/{code}` `{ labelFr?, labelEn?, sortOrder?, active? }` → `200` ; modification partielle, `labelEn` vide efface le libellé anglais ; le code et le type ne changent jamais. `409 VOCABULARY_IN_USE` (+ `usedByPublishedProfiles`) si on désactive une entrée utilisée par au moins une fiche publiée ; `404 VOCABULARY_NOT_FOUND`.
- Ce que voient les praticiens : `GET /api/vocabulary/directory` → `{ specialties[], audiences[], modalities[], languages[] }`, entrées actives seulement, dans l'ordre d'affichage ; `modalities` (in_person, video, home) et `languages` (15 codes ISO 639-1) sont des listes fixes du contrat.

## Structure
1. **Barre admin** (fond `#1E2633`) : « PsyMate · Administration », onglets Validation (badge n), Praticiens, Journal, **Vocabulaire** ; Signalements en V1.1 (G Q26) ; rappel « Aucune donnée patient n'est accessible depuis l'administration » ; avatar admin.
2. **En-tête** : « Vocabulaire de l'annuaire » + explication en une ligne (liste fermée vue par les praticiens et les visiteurs ; on désactive, on ne supprime jamais).
3. **Sous-onglets** `role="tablist"` : Spécialités (n) · Publics (n) · Modalités (n, « fixe ») · Langues (n, « fixe ») ; case « Afficher les désactivées ».
4. **Tableau** : Code (JetBrains Mono), Libellé FR, Libellé EN (« — » si null), Ordre, Utilisation (« Utilisée par N profils publiés »), État (pastille + « Active » / « Désactivée »), Actions (Renommer · Désactiver… ou Réactiver). Ligne désactivée grisée sur `#F7F9FC`, libellé barré.
5. **Formulaire « Ajouter une spécialité »** (ou « un public » selon l'onglet) : code, libellé FR, libellé EN facultatif, bouton cobalt ; rappel des règles de format sous le formulaire.
6. **Note de bas de page** : modalités et langues en lecture seule ; effet d'une désactivation sur les profils.

## Interactions
- « Désactiver… » → boîte de dialogue `role="dialog"` ; si l'API répond `409 VOCABULARY_IN_USE`, encart danger « Utilisée par N profils publiés : demandez d'abord aux praticiens de la retirer ou choisissez une spécialité de remplacement », lien vers `/admin/praticiens`, bouton « Fermer » ; la ligne reste active.
- « Réactiver » → `PATCH { active: true }` sans confirmation ; l'entrée réapparaît aussitôt dans `GET /api/vocabulary/directory`.
- « Renommer » → panneau inline sous la ligne : libellé FR (obligatoire), libellé EN (vide = effacé), ordre ; note « le code ne change jamais ; les profils gardent la spécialité » ; « Désactiver » de la même ligne inactif pendant l'édition ; succès → toast « Libellé mis à jour · visible immédiatement dans l'annuaire ».
- Ajout : `409 VOCABULARY_EXISTS` affiché sous le champ code (« Ce code existe déjà pour ce type ») ; les `422` sont affichés champ par champ depuis `errors[]`.
- Sous-onglet et case « désactivées » dans l'URL (`?kind=audience&inactive=1`) ; après chaque écriture, la liste est rechargée depuis `GET /api/admin/vocabulary/directory`.

## Tokens
- Pastilles d'état : Active `#1E6B45`, Désactivée `#B9C2D1` ; ligne désactivée fond `#F7F9FC`, texte `#8A94A6`.
- Encart 409 : fond `#FBE9E7`, bordure `#E8C1BB`, texte `#9A2F22` ; toast succès : bordure gauche `#1E6B45`, icône sur `#E4F2EA`.
- Bouton primaire `#3A63C2` / `#FFFFFF` ; sous-onglet actif souligné `#3A63C2`, texte `#2C4C99` ; codes en JetBrains Mono `#2F4F73`.

## Accessibilité
- Sous-onglets `role="tablist"` / `role="tab"` (`aria-selected`, flèches gauche/droite) et `role="tabpanel"` ; tableau avec `<caption>` masqué et `<th scope="col">`.
- Boîte de dialogue `role="dialog" aria-modal="true"` avec `aria-labelledby` / `aria-describedby`, focus initial sur « Fermer », Échap ferme ; l'encart d'erreur porte `role="alert"`.
- Toast `role="status" aria-live="polite"`, fermable au clavier ; panneau « Renommer » annoncé par `aria-expanded` / `aria-controls` sur le bouton ; aucun état transmis par la couleur seule.

## Critères d'acceptation
- [ ] Utilisateur sans rôle `admin` ou sans 2FA → `403` sur `/api/admin/vocabulary/*` et redirection côté client ; un praticien qui ouvre `/admin` est redirigé.
- [ ] Ajouter une spécialité → `201`, elle apparaît dans le tableau et dans `GET /api/vocabulary/directory` immédiatement (test : un praticien la voit dans « Mon profil public » sans rechargement serveur supplémentaire).
- [ ] Code dupliqué → `409 VOCABULARY_EXISTS` affiché sur le champ code, formulaire conservé.
- [ ] Désactiver une spécialité utilisée par un profil publié → `409 VOCABULARY_IN_USE`, boîte de dialogue avec le compteur, la ligne reste « Active ».
- [ ] Désactiver une spécialité inutilisée → elle disparaît de `GET /api/vocabulary/directory` et reste visible dans le tableau avec « Désactivée » ; « Réactiver » la ramène.
- [ ] Renommer → le code est inchangé (`PATCH` sur le même `{kind}/{code}`), le nouveau libellé est visible dans l'annuaire public ; libellé EN vidé → `labelEn` null.
- [ ] Aucune donnée patient dans les appels de cet écran : ArchUnit côté API (`identity.admin` n'importe ni `clinical`, ni `patients`, ni `linking`, ni `overview`, ni `search`), garde côté front (aucun service patient injecté dans la feature admin).
- [ ] Navigation complète au clavier (onglets, tableau, dialogue, panneau inline, toast) ; axe-core sans violation sur les trois états.

## Écarts avec la maquette
- Modalités et Langues sont fixes (pas administrables par l'endpoint) → onglets en lecture seule.
- Le 409 ne renvoie qu'un compteur → la boîte de dialogue affiche « Utilisée par N profils publiés » et un lien vers `/admin/praticiens` (filtre par spécialité à prévoir en V1.1, pas de liste nominative).
- Pas de date de désactivation dans le DTO → ne pas l'afficher.
- **Filtre « publiés » de l'écran 21** : `GET /api/admin/practitioners` n'a pas de paramètre `directory` (contrat) ; le lien du `409` ouvre `/admin/praticiens` sans filtre (UX-F1). La maquette ne montre pas l'URL : inchangée.
- **Barre admin** : déjà sans Signalements et dans l'ordre de la fiche 00 ; maquette inchangée (UX-F1).
- Les 18 spécialités fictives ne sont pas le seed (le seed réel est la migration V04, `docs/E-modele-donnees.md`).

## Hors périmètre V1
Suppression physique d'une entrée (jamais), administration des modalités et des langues, spécialité de remplacement automatique lors d'une désactivation, filtre des praticiens par spécialité (V1.1), historique des renommages dans le Journal, import/export CSV du vocabulaire, signalements (G Q26).
