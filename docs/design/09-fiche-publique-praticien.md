# Écran 9 · Fiche publique d'un praticien

> Spécification pour agents. Maquette : `09-fiche-publique-praticien.html` / `.png`. Palette : Nordique. Document B, écran 9, flow 7 (visiteur) ; Document A, module `directory` (H11, F22–F27, UC-13–15, table `practitioner_profile`), tâches M5-02 et M5-03.
>
> Mis à jour le 29 octobre 2026 (G) : URL publique `psymate.com/praticiens/{slug}` ; nom d'application PsyMate dans l'en-tête ; limitation de taux avec Bucket4j (Q24) ; « Signaler un problème » est V1.1 (Q26) ; pas de pied de page « numéros d'urgence » (Q20) ; spécialités et publics issus de la liste fermée (Q15).

## Objectif
Un **visiteur non connecté** découvre un praticien (photo, profession, présentation, spécialités, modalités, adresse) et le contacte **uniquement par les moyens que le praticien a choisis de publier**. Aucune donnée patient n'est jamais exposée ici.

## Route & module
- Route publique : `/praticiens/:slug` (hors shell authentifié ; layout `PublicLayout`, SSR ou prerender souhaitable pour le SEO en V1.1).
- API : `GET /api/public/directory/practitioners/{slug}` → profil **publié** uniquement (`published = true`), sinon 404 ; `POST /api/public/directory/practitioners/{slug}/contact` (formulaire).
- Côté praticien : `/api/me/profile` (écran 10 « Mon profil public ») alimente cette fiche ; opt-in explicite `published`.
- Store : `PublicPractitionerStore` : `profile`, `contactForm`, `sent`, `error`.

## Structure de l'écran
1. **En-tête public** (différent de l'app) : logo PsyMate + « Annuaire des praticiens », liens *Annuaire*, *Comment ça marche*, bouton secondaire **« Espace praticien »** (→ connexion).
2. **Fil d'Ariane** : Annuaire › Profession › Ville › Nom.
3. **Colonne principale** (fluide, min 560 px) :
   - **Carte identité** : photo (ou initiales sur `#E3EAF8`), nom, badge *Profil vérifié* (si identité validée par l'admin), puce **profession** (`Psychologue` / `Psychiatre` / `Coach` — jamais « personal coach »), titre + numéro professionnel (ADELI/RPPS, facultatif), ville + modalités, délai de réponse déclaré, disponibilité (*Accepte de nouveaux patients* / *Liste d'attente* / *Complet*), boutons **« Prendre contact »** (ancre `#contact`) et *Partager*.
   - **Présentation** : texte libre du praticien (Markdown restreint : paragraphes, gras, listes ; pas de liens externes hors site déclaré).
   - **Pratique** (grille 2 col.) : *Spécialités* (puces, libellés de la liste fermée), *Publics*, *Modalités* (cabinet / visio / domicile, avec état proposé / non proposé), *Langues & tarifs* (tarif optionnel, mention conventionnement libre).
   - **Formulaire de contact** (`#contact`) : nom, e-mail, objet (liste), message, case de consentement RGPD, bouton **« Envoyer le message »**. Texte d'avertissement : ne pas écrire d'informations médicales détaillées.
4. **Colonne latérale** (320–380 px) :
   - **Contact** : uniquement les moyens cochés par le praticien (téléphone, e-mail, site) ; note « Seuls les moyens de contact choisis… ».
   - **Cabinet** : mini-carte (image statique ou lien « Ouvrir dans un plan », pas de carte interactive tierce en V1), adresse, accès, accessibilité (étage, ascenseur).
   - **Horaires** de consultation (tableau simple).
   - Mention « Informations publiées par la praticienne elle-même » (le lien « Signaler un problème » arrive en V1.1).
5. **Pied de page** public : mentions légales, confidentialité, accessibilité. Pas de numéros d'urgence (G Q20).

## Règles
- Champs affichés = champs **renseignés et publiés** ; une section vide n'est pas rendue (pas de « — »).
- Le formulaire de contact : honeypot + limitation `5 / h / IP` (Bucket4j) + délai minimal de soumission 3 s (anti-spam, A4.10) ; e-mail transmis au praticien via Mailpit en dev ; message **non stocké** au-delà de 30 j (table `contact_request`, purge planifiée) et jamais lié à un dossier patient.
- Lecture publique : 60 requêtes / minute / IP (M5-02).
- Profession : `psychologist | psychiatrist | coach` → libellés FR `Psychologue | Psychiatre | Coach` (ou « Coach mental » si le praticien le choisit), EN `Psychologist | Psychiatrist | Coach`.
- Photo : upload praticien, recadrée carré, max 512 px, servie en `/public/media/…` ; fallback initiales.
- Pas de notes, avis, étoiles ni classement en V1.

## Tokens
- Mêmes tokens que l'app (fond `#EEF1F6`, cartes `#FFFFFF`, bordure `#D6DCE6`, primaire `#3A63C2`), rayon de carte 14 px (plus doux que l'app : 10 px) pour le registre « grand public ».
- Badge vérifié `--success #1E6B45` sur `#E4F2EA` ; puce profession `--primary-text #2C4C99` sur `#E3EAF8`.
- Modalités proposées : carré `#3E8E7E` ; non proposées : `#B9C2D1` + texte `#5A6679`.
- Identifiants / numéros / horaires / tarif : JetBrains Mono.

## Accessibilité & SEO
- `<h1>` = nom ; sections `<section aria-labelledby>` ; `<address>` pour l'adresse ; `<form>` avec `<label for>` sur chaque champ, erreurs inline `aria-describedby`.
- Carte statique = `role="img" aria-label` ; icônes décoratives `aria-hidden`.
- Balises `<title>`, `meta description`, `og:*`, JSON-LD `Person`/`MedicalBusiness` minimal (nom, profession, ville) ; `noindex` tant que `published = false`.
- Fonctionne à 390 px : colonnes empilées, formulaire 1 colonne, boutons pleine largeur.

## Critères d'acceptation
- [ ] Profil non publié → 404 (et pas 403) ; profil publié → 200 avec uniquement les champs publiés.
- [ ] Envoi du formulaire : e-mail reçu dans Mailpit avec nom/e-mail/objet/message ; 6e envoi en 1 h → 429 RFC 9457.
- [ ] Aucune requête vers `/api/patients*` depuis le layout public (test Playwright réseau).
- [ ] Lighthouse accessibilité ≥ 95 sur la fiche de seed « Claire Moreau ».
- [ ] Libellé « Coach » affiché pour `profession = coach` dans les deux langues.

## Hors périmètre V1
Prise de rendez-vous en ligne, avis, messagerie bidirectionnelle, carte interactive, paiement, signalement d'une fiche (V1.1).
