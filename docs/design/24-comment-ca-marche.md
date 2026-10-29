# Écran 24 · Comment ça marche (page publique)

> Spécification pour agents. Maquette : `24-comment-ca-marche.html` / `.png`. Palette : Nordique, layout public (même bandeau que 09 et 11). Module `directory` (contenu statique), aucun appel API.
>
> Mis à jour le 29 octobre 2026 (G) : nom PsyMate ; l'agenda Google est hors V1 (Q25), ne pas le promettre ; le signalement d'une fiche est V1.1 (Q26) ; pas de numéros d'urgence en pied de page (Q20) ; inscription ouverte (Q21) ; vérification humaine sous 2 jours ouvrés ; justificatifs attendus : diplôme + pièce d'identité (Q14).

## Objectif
Expliquer en une page, aux deux publics, ce que fait PsyMate et ce qu'il ne fait pas : les visiteurs comprennent ce que garantit le badge « Profil vérifié » ; les praticiens comprennent le parcours inscription → vérification et l'étanchéité entre administration et dossiers.

## Route & module
- Route : `/comment-ca-marche`, publique, indexable (`<meta name="robots" content="index,follow">`), rendue côté serveur ou prérendue (Angular SSR/prerender) pour le référencement.
- Contenu : fichiers i18n (`fr` d'abord, `en` quand disponible, G Q23), pas de CMS en V1. Les chiffres (délais, 24 h, 90 jours, 30 jours) sont des constantes partagées avec le backend (`verification.sla`, `email.confirmation.ttl`, `documents.purgeDays`, `account.deletionGraceDays`) injectées au build pour éviter toute divergence.

## Structure
1. **Bandeau public** : logo PsyMate, navigation (Annuaire des praticiens, **Comment ça marche**, Confidentialité), « Se connecter », « Créer un compte praticien ».
2. **Intro** : surtitre, titre « Un outil de suivi pour les praticiens, un annuaire vérifié pour le public », paragraphe sur les deux publics.
3. **Deux parcours** (grille 2 colonnes) : « Vous cherchez un praticien » (fiches vérifiées, formulaire de contact relayé sans copie, pas de compte) → « Parcourir l'annuaire » ; « Vous êtes praticien » (inscription ouverte, outil dès confirmation e-mail, fiche et badge après vérification) → « Créer un compte ». Ne pas mentionner l'agenda Google ni le signalement (plus tard).
4. **Quatre étapes** (liste ordonnée, numéros monospace) : Créer le compte · Confirmer l'e-mail · Déposer son diplôme et sa pièce d'identité · Vérification humaine (sous 2 jours ouvrés, ne bloque pas l'outil).
5. **Bloc sombre « Ce que nous ne voyons jamais »** : administration sans accès clinique + 3 garanties (hébergement France, contact relayé, justificatifs purgés à 90 jours).
6. **FAQ** (`<details>`, 4 questions : qui peut s'inscrire, dossier refusé, compte patient, export/suppression).
7. **Pied de page** public (mentions légales, confidentialité, accessibilité ; pas de numéros d'urgence, G Q20).

## Interactions
- Aucune logique ; les `<details>` sont natifs. Les deux premières questions sont ouvertes par défaut.
- Liens d'action : « Parcourir l'annuaire » → écran 8 ; « Créer un compte » → écran 12 ; « Se connecter » → écran 11.
- Sur mobile : les grilles passent en une colonne (`minmax(0,1fr)` + `flex-wrap`), le bandeau replie la navigation sous le logo.

## Tokens
- Surtitre `#3A63C2` capitales espacées ; icônes de parcours sur fonds `#E3EAF8` (visiteur) et `#E2F1EE` (praticien).
- Bloc sombre : fond `#1E2633`, texte secondaire `#C5CCD8`, puces `#8DB7FF`.
- Cartes : `#FFFFFF`, bordure `#D6DCE6`, rayon 12–14 px.

## Accessibilité
- Un seul `<h1>` ; sections nommées par `aria-labelledby`.
- Étapes = `<ol>` ; FAQ = `<details>/<summary>` (clavier natif).
- Contraste du bloc sombre : `#C5CCD8` sur `#1E2633` ≥ 7:1.

## Critères d'acceptation
- [ ] Page accessible sans session, rendue sans JavaScript (contenu complet dans le HTML prérendu).
- [ ] Les constantes affichées (24 h, 90 jours, 30 jours, 2 jours ouvrés) proviennent de la configuration partagée ; changer `documents.purgeDays` côté backend change le texte au build suivant.
- [ ] Aucune mention de l'agenda Google ni du signalement dans le texte V1 (test sur le contenu).
- [ ] Lighthouse accessibilité ≥ 95 ; aucun texte < 4,5:1.
- [ ] Largeur 390 px : pas de défilement horizontal, grilles en une colonne.

## Hors périmètre V1
Tarification, témoignages, blog, versions par profession.
