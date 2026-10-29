# Écran 16 · Fiche patient (création et modification)

> Spécification pour agents. Maquette : `16-fiche-patient.html` / `.png` (état « modification » ; la création est le même formulaire vide). Palette : Nordique. Document A, module `patients` (`POST /api/patients`, `GET/PATCH /api/patients/{id}`, `POST /api/patients/{id}/archive`).

## Objectif
Un seul formulaire pour créer un patient en trente secondes (seul le **nom affiché** est obligatoire) et pour compléter la fiche au fil du suivi. Rien de ce qui est saisi ici n'est une entrée clinique : la fiche décrit la personne et le cadre du suivi, la timeline décrit ce qui se passe.

## Route & module
- Routes : `/patients/nouveau` (création) et `/patients/{id}/fiche` (modification). Après création → redirection vers la vue d'ensemble, vide, barre de saisie focalisée.
- API : `POST /api/patients` `{ displayName, firstName?, lastName?, birthDate?, pronoun?, phone?, email?, context{situation, activity, referredBy, initialReason, history}, followUp{since, rhythm, modality, durationMin, fee?, prescriber?}, flags[], tags[] }` → `201`. `PATCH` partiel ; chaque modification crée une version (`patient_version`, historique consultable : « 3 versions »).
- À la création, le serveur crée automatiquement une **séance d'accueil** (`session`, type `intake`) datée du jour : toute entrée saisie ensuite a une séance de rattachement.
- `initialReason` renseigné → proposition (non automatique) de créer une **situation** « Motif initial » après l'enregistrement.
- Drapeaux (`patient_flag`) : `watch` (à surveiller, avec raison obligatoire), `risk` (risque : idées suicidaires, mise en danger ; affiché en premier dans la liste), `shared_care` (suivi conjoint). Jamais déduits automatiquement.
- Tags : communs à tous les patients du praticien (`practitioner_tag`) ; un tag identique à une émotion du vocabulaire est refusé (`422`).
- Archivage : `POST /archive` → disparaît de la liste et de l'agenda, dossier conservé intégralement, restauration possible ; aucune suppression physique en V1.

## Structure
1. **Barre d'application** + fil d'Ariane `Patients / Marie D. / Fiche`.
2. **Titre** « Modifier la fiche · Marie D. » (création : « Nouveau patient ») + méta (créée le, n séances, dernière modification).
3. **Colonne principale** (max 880 px), sections en cartes :
   - **Identité** : nom affiché (obligatoire, aide : « un prénom et une initiale suffisent »), prénom, nom, date de naissance (→ âge et phases de vie), pronom/accord (Elle / Il / Iel / Neutre), téléphone, e-mail (« pour les rappels, plus tard »).
   - **Contexte** : situation, activité, adressée par, motif initial, **histoire et éléments à garder en tête** (texte libre, visible dans la vue d'ensemble) + lien « Créer les entrées depuis ce texte » (ouvre la barre de saisie préremplie, ligne par ligne).
   - **Suivi** : suivie depuis (mois), rythme habituel (hebdo / 1 à 2 semaines / mensuel / irrégulier), modalité par défaut (cabinet / visio / téléphone), durée de séance (défaut = préférence `sessions.defaultDurationMin`), tarif `[TARIF]`, médecin / prescripteur.
   - **Drapeaux** : trois cartes-cases (à surveiller avec date et raison, risque, suivi conjoint) ; champ « Raison du drapeau » affiché quand « à surveiller » est coché.
   - **Tags du patient** : chips avec suppression + saisie avec autocomplétion sur les tags existants.
   - Pied : « Enregistrer », « Annuler », mention « chaque modification est horodatée ».
   - **Zone de danger** (bordure pointillée) : « Archiver ce patient » + explication.
4. **Colonne latérale** (320 px) : **aperçu de l'en-tête** (tel qu'il s'affichera : nom, âge, suivie depuis, drapeaux), encart « À la création d'un patient » (séance d'accueil, motif → situation, arrivée sur la vue d'ensemble), encart **Confidentialité** (coordonnées jamais synchronisées avec Google ni exportées dans l'annuaire ; lien historique des versions).

## Interactions
- Validation à la soumission uniquement, erreurs ancrées sous le champ (`aria-describedby`) ; date de naissance future ou > 120 ans → erreur.
- L'aperçu de l'en-tête se met à jour en direct (signal dérivé du formulaire).
- Cocher « risque » n'exige pas de raison mais affiche un rappel : « Les éléments de risque se notent dans la timeline (note privée) ».
- « Archiver… » → boîte de confirmation avec le nom du patient à retaper ; redirection vers la liste avec toast « Marie D. archivée · Restaurer ».
- Création : Ctrl/Cmd+Entrée enregistre depuis n'importe quel champ.

## Tokens
- Cartes de drapeau cochées : « à surveiller » `#FBF1DC` / `#7A5A12`, « risque » `#FBE9E7` / `#9A2F22`, « suivi conjoint » `#E3EAF8` / `#2C4C99`.
- Champs monospace (`JetBrains Mono`) pour les dates et le téléphone ; placeholders `[TARIF]` en `#8A94A6`.
- Zone d'archivage : bordure `1.5px dashed #B9C2D1`, bouton `#9A2F22` sur fond blanc.

## Accessibilité
- Chaque carte de section est un `<fieldset>` avec `<legend>` (texte visuel en petites capitales).
- Drapeaux = `<input type="checkbox">` réels dans un `<label>` englobant.
- Chips de tags : bouton de suppression avec `aria-label="Retirer le tag #travail"`.

## Critères d'acceptation
- [ ] `POST` avec seulement `displayName` → `201`, une séance `intake` créée le même jour, redirection vers `/patients/{id}` avec barre de saisie focalisée.
- [ ] `displayName` vide → `422` serveur et erreur client sans appel réseau.
- [ ] Tag égal à une émotion (ex. « anxiété ») → refus avec message explicite.
- [ ] Coché « à surveiller » sans raison → erreur ; avec raison → en-tête du patient et liste affichent le drapeau avec sa date.
- [ ] Modifier le rythme → `patient_version` n+1 ; l'historique liste la différence champ par champ.
- [ ] Archiver puis restaurer → aucune entrée ni lien perdu ; l'agenda retrouve les rendez-vous futurs.

## Hors périmètre V1
Photo du patient, documents joints, import depuis un autre logiciel, partage avec un confrère.
