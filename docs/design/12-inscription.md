# Écran 12 · Inscription praticien

> Spécification pour agents. Maquette : `12-inscription.html` / `.png`. Palette : Nordique. Document A, module `identity` (M1-10) ; `directory` pour la profession. Suivi par l'écran 13 (dossier professionnel) et l'écran 20 (validation admin).
>
> Mis à jour le 29 octobre 2026 (G) : **inscription ouverte à tous** (Q21), sans code d'invitation ; nom d'application **PsyMate** ; mot de passe compromis vérifié contre une liste locale (pas d'appel externe) ; limites de taux avec Bucket4j (Q24) ; le n° ADELI/RPPS reste recommandé et jamais bloquant à l'écran 13 (Q14).

## Objectif
Créer un compte praticien en trois minutes, avec la profession et les coordonnées du cabinet, puis orienter vers le dépôt des justificatifs. Le praticien peut utiliser l'outil clinique dès l'e-mail confirmé ; l'annuaire et le badge « Profil vérifié » attendent la validation administrateur.

## Route & module
- Route publique : `/inscription` (layout `PublicLayout`). Ouverte à tous ; les seuls freins sont la confirmation d'e-mail et les limites de taux (G Q21).
- API : `POST /api/auth/register` `{firstName, lastName, email, password, profession, professionalNumber?, phone, address{street, postalCode, city}, remoteOnly, acceptTerms, newsletter}` → `201 {practitionerId}` + e-mail de confirmation (lien valable 24 h, `POST /api/auth/email/confirm {token}`). E-mail déjà utilisé → `201` identique (anti-énumération) + e-mail « vous avez déjà un compte ».
- Table `practitioner` (créée complète par V01, voir E3.2) : `phone`, `address_street`, `address_postal_code`, `address_city`, `remote_only`, `verification_status` (`text` + `CHECK`, défaut `email_pending`), `verified_at`, `verified_by`.
- Mot de passe : ≥ 12 caractères, vérifié contre une liste locale de mots de passe compromis (pas d'appel externe HIBP en V1), force affichée en 4 barres (zxcvbn ≥ 3 requis).
- Profession : `psychologist | psychiatrist | coach` ; détermine les valeurs par défaut des préférences (`vocabulary.subjectLabel`, `entryTypes.medicationVisible`).

## Structure
1. **En-tête minimal** : logo PsyMate + « Déjà un compte ? Se connecter ».
2. **Formulaire** (colonne principale) en trois sections numérotées :
   - **1 · Qui vous êtes** : prénom, nom, e-mail professionnel (aide : sert à la connexion et aux messages de l'annuaire).
   - **2 · Votre profession** : trois cartes radio (Psychologue / Psychiatre / Coach) avec une phrase sur ce que ça change ; n° professionnel (ADELI/RPPS, facultatif ici et recommandé à l'écran 13 pour psychologues et psychiatres, jamais bloquant : G Q14) ; **téléphone** (jamais affiché sans accord) ; **adresse du cabinet** (rue, code postal, ville) ; case « J'exerce uniquement en visio » (désactive l'adresse) ; encart ambre annonçant le dépôt des justificatifs (diplôme et pièce d'identité) et la validation sous 2 jours ouvrés.
   - **3 · Votre accès** : mot de passe + confirmation, jauge de force, rappel 2FA recommandée.
   - **Consentements** : CGU + politique de confidentialité (obligatoire, avec la phrase « responsable du traitement / hébergeur »), nouveautés bêta (facultatif).
   - Bouton **Créer mon compte** + mention de l'e-mail de confirmation.
3. **Colonne latérale** : « Ensuite » en 4 étapes (confirmer l'e-mail, déposer les justificatifs, validation admin, profil public), « Vos données » (hébergement, chiffrement, cloisonnement par praticien, export), mention bêta.
4. À 390 px : une colonne, cartes de profession empilées.

## Validation côté client
- Inline au `blur`, message sous le champ, `aria-describedby` ; le bouton reste actif (erreurs résumées en haut après tentative).
- Code postal FR 5 chiffres ; téléphone normalisé E.164 côté serveur.
- `remoteOnly` coché → champs adresse désactivés et non requis.

## Après soumission
- Page « Vérifiez votre boîte mail » avec bouton « Renvoyer » (limité à 1 / min et 5 / jour par compte, écran 25).
- Premier login après confirmation → redirection vers `/mon-compte/dossier-professionnel` (écran 13) avec bandeau d'état ; possibilité de passer (« Plus tard »).

## Tokens
- Sections blanches, bordure `#D6DCE6`, rayon 12 px ; numéros d'étape en JetBrains Mono sur `#E3EAF8`.
- Carte de profession sélectionnée : bordure 2 px `--primary`, fond `#E3EAF8`, texte `#2C4C99`.
- Encart d'information : `--warning #7A5A12` sur `#FBF1DC` ; jauge de force : `--success #1E6B45`.

## Accessibilité
- Profession = `<fieldset>` + `<legend>` + `<input type="radio">` réels dans des `<label>`.
- Autocomplétion navigateur : `given-name`, `family-name`, `email`, `tel`, `street-address`, `postal-code`, `address-level2`, `new-password`.
- Jauge de force accompagnée d'un texte (« Bon · 14 caractères… »), jamais couleur seule.

## Critères d'acceptation
- [ ] Inscription complète → `201`, e-mail Mailpit reçu (expéditeur `test@gmail.com` en dev), `verification_status = email_pending`.
- [ ] E-mail existant → même réponse `201` et e-mail « déjà un compte » (pas d'énumération).
- [ ] Mot de passe « motdepasse1234 » refusé (liste compromise) ; « Vendôme-Lyon-2026!x » accepté.
- [ ] Profession `coach` → préférences par défaut `subjectLabel = client`, `medicationVisible = false`.
- [ ] `remoteOnly = true` sans adresse → `201` ; `remoteOnly = false` sans adresse → `422` RFC 9457 avec le champ fautif.
- [ ] Lien de confirmation expiré (> 24 h) → page d'erreur avec renvoi.
- [ ] Aucun code d'invitation n'est demandé ni accepté.

## Hors périmètre V1
Inscription d'un cabinet de groupe / secrétariat, import depuis un autre logiciel.
