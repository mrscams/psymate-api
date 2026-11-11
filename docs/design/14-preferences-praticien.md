# Écran 14 · Mon compte › Préférences praticien

> Spécification pour agents. Maquette : `14-preferences-praticien.html` / `.png`. Palette : Nordique. Document A, module `identity` (`practitioner.preferences JSONB`, `/api/me/preferences`, M1-12) ; `vocabulary` pour les émotions personnalisées ; M6-04 pour le thème.
>
> Mis à jour le 29 octobre 2026 (G) : `sessions.defaultDurationMin` vaut **60** par défaut ; `locale` `fr` par défaut, l'anglais pouvant être incomplet (Q23) ; promotion d'une émotion custom au vocabulaire global = V2 (Q8).
>
> Mis à jour le 11 novembre 2026 (UX-F1, audit UX du 9 novembre) : maquette alignée sur le délai de correction de 1 h 15 (G Q31) ; résumé « Compte et sécurité » limité à ce que le contrat fournit (trois tuiles, connexion précédente sans ville ni lien de journal). Voir « Écarts avec la maquette ».

## Objectif
Un seul endroit pour tout ce qui est mémorisé **par compte** et retrouvé sur tous les appareils : affichage, vocabulaire, émotions personnalisées, sécurité. Chaque réglage s'enregistre immédiatement.

## Route & module
- Route : `/mon-compte/preferences`.
- API : `GET /api/me/preferences` → objet plat ; `PATCH /api/me/preferences` (merge JSON RFC 7396, clés inconnues ignorées). Clés V1 :
  | Clé | Valeurs | Défaut |
  |---|---|---|
  | `overview.view` | `simple` \| `complete` | `simple` |
  | `overview.window` | `30d` \| `90d` \| `12m` | `90d` |
  | `timeline.groupBy` | `session` \| `month` \| `chronological` | `session` |
  | `theme` | `light` \| `soft` \| `dark` \| `system` | `light` |
  | `locale` | `fr` \| `en` | `fr` |
  | `vocabulary.subjectLabel` | `patient` \| `client` | selon profession (`coach` → `client`) |
  | `entryTypes.medicationVisible` | bool | `psychiatrist` → true, sinon false |
  | `entries.defaultVisibility` | `clinical` \| `private` | `clinical` |
  | `sessions.defaultDurationMin` | 15–180 | 60 (G) |
- Store : `PreferencesStore` chargé au login, mis à jour de façon optimiste, repli sur la dernière valeur serveur en cas d'erreur ; `localStorage.prefs` = cache de démarrage uniquement.
- Les bascules présentes ailleurs (vue d'ensemble, fenêtre, regroupement timeline) écrivent **les mêmes clés** : une seule source de vérité.
- Délai de correction sans version (1 h 15, G Q31) : affiché en lecture seule, fixé par `versioning.grace-period` côté serveur en V1.

## Structure
1. **Barre d'application** + **navigation du compte** (Préférences actif).
2. **Affichage** : vue d'ensemble par défaut (Simple / Complète), fenêtre d'analyse (30 j / 90 j / 1 an), timeline (Par séance / Par mois / Chronologique), thème (Clair / Doux / Sombre / Système, avec vignette), langue (Français ; Anglais avec la mention « traduction partielle » tant que les fichiers EN sont en retard).
3. **Vocabulaire et saisie** : désignation (Patient·e / Client·e), type médicament visible (interrupteur), visibilité par défaut (Clinique / Privée), durée de séance par défaut (60 min), délai de correction (lecture seule).
4. **Émotions personnalisées · n** : tableau (émotion, famille avec pastille couleur, nombre d'utilisations, statut *custom*), lien « Gérer la liste complète » (page dédiée, écran 17 : ajouter, renommer, fusionner ; la promotion au vocabulaire global est V2).
5. **Compte et sécurité** (résumé) : trois tuiles : **e-mail de connexion** (`GET /api/auth/me`) avec *Modifier* ; **double authentification** (état lu dans `twoFactorEnabled` de `GET /api/auth/me`, lien *Codes de secours* sans compteur) ; **sessions actives** (« n appareils », nombre de `sessions[]` de `GET /api/me/security`, action *Déconnecter les autres* = `POST /api/auth/sessions/revoke-others`). Sous les tuiles : « Connexion précédente : date · appareil » (`lastLogin` de `GET /api/me/security`, sans ville) ; lien « Tout voir » vers l'écran 18. Pas de lien « Journal de sécurité » (hors contrat, fiche 18).

## Interactions
- Contrôles segmentés `role="group"` + `aria-pressed` ; thème en `<input type="radio">` réels (vignettes = `<label>`).
- Changement de thème : appliqué immédiatement (`data-theme` sur `<html>`), persistance serveur en arrière-plan.
- Changement de langue : recharge l'application sur la locale (`@angular/localize` build par locale).
- `medicationVisible = false` masque le type dans la barre de commande (`/med` renvoie un message) et la timeline, mais ne supprime rien : les entrées existantes restent visibles avec une mention.
- Désignation « client » : remplace les libellés `patient` partout via les fichiers de traduction (`$localize` avec contexte `subject`), jamais les données.

## Tokens
- Segments actifs `#E3EAF8` / `#2C4C99` ; vignettes de thème : clair `#EEF1F6`, doux `#F4F6F9`, sombre `#1A2130`.
- Familles d'émotions : pastilles aux couleurs `--emo-*` (joie `#D9A426`, peur `#7A68C9`, tristesse `#3C66A6`…).
- Sécurité : état activé `--success`.

## Accessibilité
- Chaque ligne de réglage = libellé + aide (`aria-describedby`) + contrôle.
- Tableau des émotions : `<th scope="col">`.
- Vignettes de thème : le texte (« Clair », « Sombre ») porte l'information, la vignette est `aria-hidden`.

## Critères d'acceptation
- [ ] Changer la vue d'ensemble ici puis ouvrir un patient → vue appliquée ; la bascule sur la page patient et ce réglage restent synchronisés.
- [ ] `PATCH {"theme":"dark"}` → `data-theme="dark"` immédiat, persistant après reconnexion sur un autre navigateur.
- [ ] Clé inconnue dans le `PATCH` → ignorée, `200`, pas d'erreur.
- [ ] Praticien `coach` nouvellement inscrit → `subjectLabel = client`, `medicationVisible = false` sans action ; tout compte neuf lit `sessions.defaultDurationMin = 60` et `locale = fr`.
- [ ] Désactiver le type médicament puis `/med sertraline` dans la barre → message « type masqué dans vos préférences » + lien.
- [ ] Tableau des émotions : compteur d'utilisations exact vs `entry_feeling` (test d'intégration).

## Écarts avec la maquette
- **Délai de correction sans version** : 1 h 15 (G Q31, `versioning.grace-period = 75m`), pas 5 min. Maquette corrigée (UX-F1).
- **Résumé sécurité** : `lastLogin.city` vaut toujours `null` en V1 et le journal de sécurité praticien est hors contrat (fiche 18) : la ligne devient « Connexion précédente : date · appareil », sans ville ni lien « Journal de sécurité ». Maquette corrigée (UX-F1). Les trois tuiles sont conservées : chacune s'appuie sur une route du contrat (`GET /api/auth/me`, `GET /api/me/security`, `POST /api/auth/sessions/revoke-others`).

## Hors périmètre V1
Préférences par patient, thèmes personnalisés, délégation de compte (secrétariat), préférences d'agenda (hors V1, G Q25).
