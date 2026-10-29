# Écran 10 · Mon compte › Profil public (affichage et modification)

> Spécification pour agents. Maquette : `10-mon-profil-public.html` / `.png`. Palette : Nordique. Document B, écran 10, flow 8 ; Document A, module `directory` (`practitioner_profile`, `/api/me/profile`, M5-04). Dépend de la vérification (écran 13/20) pour la publication.
>
> Mis à jour le 29 octobre 2026 (G Q15) : spécialités et publics viennent d'une **liste fermée** servie par `GET /api/vocabulary/directory` (table `directory_vocabulary`, modifiable par l'admin, M1-14b) ; aucune saisie libre.

## Objectif
Le praticien remplit et modifie sa fiche publique, choisit précisément ce qui est affiché (chaque moyen de contact a son interrupteur), voit en direct ce que verra un visiteur, et publie ou dépublie d'un geste.

## Route & module
- Route : `/mon-compte/profil-public` ; navigation Mon compte (Profil public · Préférences · Émotions personnalisées · Compte et sécurité · Données et export).
- API : `GET /api/me/profile`, `PATCH /api/me/profile` (merge JSON, enregistrement automatique après 800 ms d'inactivité, indicateur « Enregistré · il y a … »), `POST /api/me/profile/photo` (multipart, recadrage carré, 512 px max, EXIF supprimé), `PATCH /api/me/profile/publish {published: bool}`, `GET /api/vocabulary/directory` (listes fermées : spécialités, publics, modalités, langues).
- Règle de publication : `published` ne peut passer à `true` que si `verification_status = approved` **et** nom affiché, profession, ville **et** ≥ 1 moyen de contact affiché. Sinon `409` avec la liste des manques, que l'écran affiche sous l'interrupteur.
- Spécialités et publics : codes de la liste fermée uniquement ; un code hors liste ou désactivé est refusé `422`.
- Les champs vides ne sont **pas rendus** sur la fiche publique (écran 9) ; l'aperçu applique la même règle.

## Structure
1. **Barre d'application** (Patients / Mon compte).
2. **Navigation du compte** (220 px, gauche) : nom, profession · ville ; entrées avec pastille verte « publié » sur Profil public.
3. **Formulaire** (colonne centrale) :
   - En-tête : titre, phrase « rien n'est publié sans l'interrupteur », état d'enregistrement.
   - **Identité** : photo (initiales par défaut, bouton Changer), nom affiché, profession (Psychologue / Psychiatre / Coach / Coach mental), titre affiché, n° professionnel (affiché si rempli), **disponibilité** segmentée (Accepte de nouveaux patients · Liste d'attente · Complet).
   - **Présentation et pratique** : présentation (1 200 caractères, compteur), spécialités (puces, 8 max, choisies dans la liste fermée de l'annuaire), publics (liste fermée), modalités (cabinet / visio / domicile), langues, tarif (facultatif), durée de séance.
   - **Moyens de contact** : téléphone, e-mail public, site web — chacun avec un interrupteur **Afficher** ; délai de réponse (liste, dont « Ne pas afficher »). Rappel : le formulaire de contact de la fiche est toujours actif et relaie par e-mail.
   - **Cabinet et horaires** : adresse, accès, accessibilité du lieu, plages horaires (ajouter une plage).
4. **Colonne droite** (320–380 px) :
   - **Apparaître dans l'annuaire** : interrupteur *Publié* (vert), URL de la fiche en mono (`psymate.com/praticiens/{slug}`), phrase sur la dépublication, liste de contrôle (✓ nom/profession/ville, ✓ un contact affiché, ⚠ photo absente → initiales), boutons *Ouvrir la fiche publique* et *Copier le lien*.
   - **Aperçu · ce que voit un visiteur** : carte réduite (photo, nom, puce profession, ville, disponibilité, début de présentation, 3 spécialités + `+n`, contacts affichés, bouton factice « Prendre contact »), mise à jour pendant la saisie ; note « les champs vides ne sont pas affichés ».
5. À 390 px : navigation en onglets horizontaux, aperçu sous le formulaire.

## Interactions
- Enregistrement automatique : état *Enregistrement… / Enregistré · il y a n s / Erreur, réessayer* ; aucun bouton « Enregistrer ».
- Interrupteur *Publié* : confirmation inline pour la dépublication (« La fiche disparaît immédiatement ») ; pas de modale.
- Changement de profession : met à jour la puce de l'aperçu ; si passage à `coach`, proposer le libellé « Coach » ou « Coach mental ».
- Puces (spécialités, publics) : sélection dans la liste fermée avec filtre par texte ; `Entrée` ajoute l'élément surligné, `Retour arrière` sur champ vide retire la dernière ; aucune création de valeur libre (si rien ne correspond : « Pas dans la liste · proposer une spécialité » envoie un simple e-mail à l'administrateur, hors V1 si trop coûteux).
- Prévisualisation complète : *Ouvrir la fiche publique* ouvre l'écran 9 en mode « aperçu » même si non publié (bandeau « aperçu — non publié »).

## Tokens
- Sections blanches, rayon 12 px ; interrupteurs `accent-color: #3A63C2`, *Publié* en `#1E6B45`.
- Disponibilité active : `#E4F2EA` / `#1E6B45`.
- Puces : `#EEF1F6` avec bouton × rond `#D6DCE6`.
- Aperçu : rayon 14 px (registre public), puce profession `#E3EAF8` / `#2C4C99`.

## Accessibilité
- Tous les interrupteurs = `<input type="checkbox" role="switch">` dans un `<label>`.
- Puces : bouton de retrait avec `aria-label="Retirer X"`.
- Aperçu : `aria-live="polite"` désactivé (trop bavard) ; à la place, un bouton « Rafraîchir l'aperçu » pour lecteurs d'écran.
- Contraste : `#5A6679` sur blanc pour les aides ; `#8A94A6` réservé aux mentions ≥ 12 px non essentielles.

## Critères d'acceptation
- [ ] Modifier le nom → `PATCH` après 800 ms, « Enregistré » affiché, aperçu mis à jour.
- [ ] `verification_status ≠ approved` → interrupteur *Publié* désactivé avec motif « Dossier en cours de vérification ».
- [ ] Aucun contact affiché → publication refusée `409` avec `missing: ["contact"]`, message sous l'interrupteur.
- [ ] Spécialité hors liste → impossible à saisir dans l'interface ; envoyée quand même → `422`.
- [ ] Dépublier → `GET /api/public/directory/practitioners/{slug}` répond `404` immédiatement.
- [ ] Photo 8 Mo JPEG → recadrée 512 px, EXIF absent (test).
- [ ] Champ tarif vide → absent de la fiche publique et de l'aperçu.

## Hors périmètre V1
Plusieurs cabinets, agenda en ligne (hors V1, G Q25), galerie photos, avis.
