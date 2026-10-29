# Écran 17 · Mon compte › Émotions personnalisées

> Spécification pour agents. Maquette : `17-emotions-personnalisees.html` / `.png`. Palette : Nordique. Document A, module `vocabulary` (tables réelles en E3.5 : `emotion` avec `scope`, `family`, `promoted_to` ; `emotion_merge`), tâche M1-02 ; complète la section « Émotions personnalisées » de `14-preferences-praticien.md`.
>
> Mis à jour le 29 octobre 2026 (G Q8, Q11) : la **fusion** est livrée en V1 ; la **promotion au vocabulaire global** (« Proposer au global », admin › Vocabulaire) est **V2** : les éléments correspondants ci-dessous sont à ne pas implémenter. Les 29 émotions globales de E5.2 sont livrées telles quelles. Les noms d'API et de colonnes réels sont ceux de E et de F (M1-02) : `code` au lieu d'`id`, `original_emotion_code` au lieu d'`original_emotion_label`.

## Objectif
Laisser chaque praticien étendre le vocabulaire des ressentis sans casser l'analyse : toute émotion personnalisée est rattachée à une **famille** (8 familles inspirées de Plutchik), ce qui lui donne une couleur dans les courbes et une place dans le graphe. Les émotions utilisées ne se suppriment pas : elles se fusionnent ou se désactivent.

## Route & module
- Route : `/mon-compte/emotions`.
- API :
  - `GET /api/vocabulary/emotions` → globales (version, 29 entrées, `family`, `valence`, `arousal`, `labelFr`, `labelEn`) + personnalisées du praticien (`scope = custom`, `usageCount`, `patientCount`, `active`).
  - `POST /api/me/emotions` `{ labelFr, labelEn?, family, valence?, arousal? }` → `201` ; `409` si libellé identique (insensible à la casse et aux accents) à une émotion globale ou personnalisée active.
  - `PATCH /api/me/emotions/{code}` (renommer, compléter valence/activation, `active`).
  - `POST /api/me/emotions/{code}/merge` `{ targetCode }` → réécrit `entry_feeling.emotion_code`, conserve `original_emotion_code` ; réversible 30 jours (`emotion_merge` avec `undo_until`, `POST /api/me/emotions/merges/{id}/undo`).
  - *V2* : `POST /api/me/emotions/{code}/propose` → demande de promotion examinée dans l'admin › Vocabulaire ; si acceptée, l'émotion devient globale et les ressentis du praticien pointent vers la globale (`promoted_to`). **Non implémenté en V1.**
- Valence (−2…+2) et activation (0…4) : facultatives en V1, affichées « compléter » quand absentes ; obligatoires à partir de la V2 (IA).

## Structure
1. **Barre d'application** + **navigation « Mon compte »** (Émotions personnalisées actif).
2. **Introduction** : rôle du vocabulaire global et des personnalisées.
3. **Ajouter une émotion** (carte-formulaire) : libellé (avec détection « Proche de “Anxiété” (global). Vérifiez que ce n'est pas la même chose »), libellé anglais, **famille** (8 radios avec pastille couleur), bloc dépliable « Valence et activation » (deux curseurs avec libellé textuel de la valeur), bouton « Ajouter à mon vocabulaire », mention de visibilité.
4. **Mes émotions · n** (tableau) : émotion + badge (*perso* ; *promue au global* en V2), famille (pastille), valence · activation (ou « compléter »), utilisée (n fois · n patients), créée le, actions **Renommer / Fusionner…** (ou **Désactiver** si peu utilisée). *V2* : **Proposer au global** et la ligne promue « Vos 12 ressentis pointent désormais vers “Soulagement” (global). Rien à faire. »
5. **Confirmation de fusion** (en ligne, sous la ligne concernée) : « Fusionner “Lassitude” dans “Tristesse” (global) ? Les 3 ressentis garderont leur intensité et leur texte… Réversible pendant 30 jours. » + Fusionner / Annuler.
6. **Vocabulaire global · 29 émotions · version n** : référence en lecture seule, par famille.

## Interactions
- Détection de proximité pendant la saisie (`GET /api/vocabulary/emotions/similar?q=`, trigram + unaccent, seuil 0,6) : avertissement, pas un blocage, sauf égalité stricte (`409`).
- Renommer : édition en place ; les ressentis passés affichent le nouveau libellé.
- Fusionner : cible = toute émotion globale ou personnalisée active de **la même famille** par défaut ; changer de famille demande une confirmation supplémentaire (les courbes changent de couleur).
- Désactiver : l'émotion disparaît de la saisie mais reste lisible sur les anciens ressentis ; réactivable.
- *V2* : Proposer au global → bouton passe à « Proposée · en examen » ; la décision admin arrive par notification.
- Les émotions personnalisées sont proposées dans la barre de commande (`/ressenti`) avec le badge « perso », après les globales de même préfixe.

## Tokens
- Pastilles de famille : joie `#D9A426`, confiance `#3E8E7E`, peur `#7A68C9`, surprise `#2E9AA8`, tristesse `#3C66A6`, dégoût `#6E7F3E`, colère `#B8463C`, anticipation `#D07A2C`.
- Badge *perso* : fond `#ECE8F8`, texte `#4E3F9E` ; badge *promue* (V2) : `#E4F2EA` / `#1E6B45`.
- Avertissement de proximité : `#FBF1DC` / `#7A5A12` ; confirmation de fusion : fond `#EEF1F6`, bordure `#B9C2D1`.

## Accessibilité
- Familles = `<fieldset>` + radios réels ; la couleur est doublée par le nom.
- Curseurs = `<input type="range">` avec `aria-valuetext` (« plutôt désagréable »).
- Tableau avec `<th scope="col">` ; la confirmation de fusion reçoit le focus à l'ouverture et le rend au bouton « Fusionner… » à la fermeture.

## Critères d'acceptation
- [ ] `POST` « anxiete » (sans accent) alors que « Anxiété » est globale → `409` ; « Appréhension sociale » → `201` avec avertissement de proximité côté client seulement.
- [ ] Fusion de « Lassitude » (3 ressentis) dans « Tristesse » → les 3 `entry_feeling` pointent vers `sadness`, `original_emotion_code` = code de Lassitude, intensité et texte inchangés ; annulation possible 30 jours puis impossible.
- [ ] Une émotion avec `usageCount > 0` n'expose pas « Supprimer » ; `DELETE` → `405`.
- [ ] Désactivée → absente des suggestions de la barre de commande, toujours affichée sur les anciens ressentis.
- [ ] *V2* : promotion acceptée par un admin → `promoted_to` renseigné, les courbes du patient conservent la même famille et la même couleur.
- [ ] Valence/activation absentes → cellule « compléter » cliquable ; renseignées → la courbe de valence de la vue d'ensemble les prend en compte.

## Hors périmètre V1
Promotion au vocabulaire global (V2, G Q8), vocabulaire partagé entre praticiens d'un même cabinet, traduction automatique des libellés, émotions par patient.
