# Étude couleurs UX · Palette Nordique (retenue)

Oct 24, 2026 · @Hayes · Remplace l'étude précédente · Complément du document B (B7)

## 1. La palette en une page

Nordique : un cobalt net pour l'action, des neutres gris-bleu, une lavande réservée aux émotions, et deux teintes hors de l'axe bleu (vert-bleu, ocre) pour que les types d'entrées restent distincts. Vingt-deux couleurs en tout, aucune autre n'est autorisée dans l'interface.

| Rôle | Token | Hex | Où |
| --- | --- | --- | --- |
| Fond de page | `surface-ground` | #EEF1F6 | Marges, espace entre les cartes |
| Surface | `surface-raised` | #FFFFFF | Cartes, panneaux, en-têtes, zones de lecture |
| Surface creusée | `surface-sunken` | #E4E8F0 | Champs désactivés, chips neutres, pistes de curseur |
| Bordure fine | `border-subtle` | #D6DCE6 | Séparateurs, contours de cartes |
| Bordure forte | `border-strong` | #B9C2D1 | Contours de champs de saisie |
| Texte | `text-primary` | #1E2633 | Tout texte courant |
| Texte secondaire | `text-secondary` | #5A6679 | Libellés, métadonnées, légendes |
| Texte désactivé | `text-disabled` | #8A94A6 | Placeholders uniquement |
| Primaire | `primary` | #3A63C2 | Bouton principal, lien, onglet actif, focus, séance en cours |
| Primaire pressé | `primary-hover` | #2C4C99 | Survol, pressé, texte sur teinte primaire |
| Teinte primaire | `primary-tint` | #E3EAF8 | Fond des états actifs, halo de focus |
| Ressenti | `feeling` | #8B7BD6 | Pastille, icône, courbe, curseur d'intensité |
| Teinte ressenti | `feeling-tint` | #ECE8F8 | Chip ressenti, onglet Ressenti sélectionné |
| Texte ressenti | `feeling-text` | #4E3F9E | Texte sur teinte ressenti |
| Note | `entry-note` | #7F8EA8 | Pastille et icône |
| Événement | `entry-event` | #3A63C2 | Pastille et icône (même teinte que le primaire, forme losange) |
| Situation | `entry-situation` | #2F4F73 | Pastille, bande de durée |
| Séance | `entry-session` | #3E8E7E | Pastille, cadre de séance |
| Médicament | `entry-medication` | #B07A2A | Pastille, capsule |
| Erreur | `danger` | #9A2F22 sur #FBE9E7 | Erreurs système, actions destructives |
| Avertissement | `warning` | #7A5A12 sur #FBF1DC | Séance non clôturée, brouillons, flags praticien |
| Succès | `success` | #1E6B45 sur #E4F2EA | Confirmations |

## 2. Pourquoi Nordique pour un produit de santé mentale

La palette dit « outil numérique sérieux » avant de dire « cabinet » : c'est un choix de positionnement, assumé, qui place le produit du côté des logiciels professionnels modernes plutôt que des applications de bien-être.

### Forces

| Force | Ce que ça apporte au praticien |
| --- | --- |
| Cobalt saturé mais sombre (#3A63C2) | L'action principale se repère instantanément, même dans une timeline dense ; contraste 5,7:1 sur blanc |
| Neutres bleutés | Impression de précision et de propreté ; le blanc des cartes ressort davantage que sur un fond chaud |
| Lavande isolée pour les ressentis | Une seule couleur signifie « émotion » dans tout le produit : timeline, graphe, vue d'ensemble, recherche |
| Faible saturation globale hors primaire | Le dossier reste calme ; aucune couleur ne juge le patient |
| Cohérence avec les codes du secteur | Le bleu est la couleur attendue d'un outil de santé en Europe ; aucune explication à donner |

### Risques et traitement

| Risque | Gravité | Traitement dans cette étude |
| --- | --- | --- |
| Fond froid fatigant sur de longues sessions | Moyenne | Zones de lecture sur blanc ; fond visible seulement dans les marges ; réglage « contraste doux » ; mode sombre dès la V1 (section 6) |
| Trois gris-bleus trop proches (situation, séance, médicament dans la maquette initiale) | Élevée | Séance passée en vert-bleu #3E8E7E, médicament en ocre #B07A2A, situation assombrie en #2F4F73 ; formes d'icônes distinctes (section 4) |
| Primaire et événement de la même teinte | Faible | L'événement ne porte le cobalt que sur une pastille de 8 px et un losange ; jamais sur un fond ni un bouton |
| Lavande et famille d'émotion « peur » en concurrence | Moyenne | La famille peur / anxiété est alignée sur la lavande (#7A68C9) ; les autres familles s'en écartent nettement |
| Image « tech » trop froide pour un psychologue libéral | Moyenne | Typographie humaniste (Inter), coins arrondis 8-12 px, langage de l'interface chaleureux ; la couleur n'est pas le seul vecteur de ton |

### Ce que la palette ne fait jamais

- Colorer un patient, une entrée ou une émotion en rouge.
- Utiliser un dégradé, un aplat coloré de carte ou une ombre teintée.
- Mettre deux couleurs d'action sur le même écran.
- Porter une information par la couleur seule.

## 3. Système de couleur

Quatre groupes, chacun avec une règle d'emploi stricte : les neutres construisent, le premier guide, la lavande signale l'émotion, la signalétique parle du système.

### Neutres (90 % de chaque écran)

| Token | Hex | Rôle |
| --- | --- | --- |
| `surface-ground` | #EEF1F6 | Jamais sous un texte de corps ; uniquement marges et interstices |
| `surface-raised` | #FFFFFF | Toute zone où l'on lit ou saisit |
| `surface-sunken` | #E4E8F0 | Désactivé, chips neutres, pistes ; jamais pour un texte secondaire |
| `border-subtle` | #D6DCE6 | 1 px ; séparateurs et cartes |
| `border-strong` | #B9C2D1 | 1 px ; champs de saisie au repos, 1,5 px cobalt au focus |
| `text-primary` | #1E2633 | Corps, titres |
| `text-secondary` | #5A6679 | Libellés 11-13 px, métadonnées |
| `text-disabled` | #8A94A6 | Placeholders ; jamais un libellé porteur |

Échelle complète pour les besoins intermédiaires (ombres, survols, graphiques) : 0 #FFFFFF · 10 #EEF1F6 · 20 #E4E8F0 · 30 #D6DCE6 · 40 #B9C2D1 · 50 #8A94A6 · 60 #5A6679 · 70 #3A4454 · 80 #1E2530 · 90 #151A22 · 100 #1E2633.

### Primaire : cobalt

| Token | Hex | Emploi autorisé |
| --- | --- | --- |
| `primary` | #3A63C2 | Un bouton principal par zone visible, liens, onglet actif (soulignement 2 px), anneau de focus, pastille « séance en cours », marqueurs de séance sur la courbe |
| `primary-hover` | #2C4C99 | Survol et pressé ; texte sur `primary-tint` |
| `primary-tint` | #E3EAF8 | Fond des états actifs et du halo de focus (4 px), chip « en cours », bandeau d'information |
| `primary-on` | #FFFFFF | Texte sur `primary` |

Interdit : aplat de carte, fond de panneau, texte courant, icône décorative.

### Accent : lavande, réservée au ressenti

| Token | Hex | Emploi |
| --- | --- | --- |
| `feeling` | #8B7BD6 | Pastille et icône cercle du ressenti, courbe d'intensité, piste remplie et poignée du curseur 0-10 |
| `feeling-tint` | #ECE8F8 | Chip « Ressenti », onglet Ressenti sélectionné dans l'éditeur, fond de l'aperçu de capture |
| `feeling-text` | #4E3F9E | Texte et intensité sur `feeling-tint` |

La lavande ne sert à rien d'autre : pas de lien, pas de bouton, pas de flag. Quand un praticien voit de la lavande, c'est une émotion.

### Signalétique système (trois couleurs)

| Rôle | Fond | Texte / icône | Déclencheurs |
| --- | --- | --- | --- |
| Erreur / destructif | #FBE9E7 | #9A2F22 | Échec réseau, validation, « Archiver », « Retirer le lien » |
| Avertissement | #FBF1DC | #7A5A12 | Séance non clôturée, brouillons en attente, projection obsolète |
| Succès | #E4F2EA | #1E6B45 | « Enregistré », synchronisation terminée |
| Information | #E3EAF8 | #2C4C99 | Aide contextuelle, introduction des suggestions IA |

### Flags praticiens

Une couleur unique, ambre #B8862E sur #FBF1DC, pour tous les flags posés à la main (« à surveiller », « relance », « urgence »), distingués par icône et libellé. L'ambre partage la teinte de l'avertissement à dessein : un flag est une vigilance du praticien, pas une alerte du système, et il ne doit jamais être rouge.

## 4. Couleurs méties

Les couleurs qui signifient quelque chose dans le dossier : le type d'une entrée, la famille d'une émotion, la force d'un ressenti. Elles n'apparaissent que sur de petites surfaces (pastille, icône, bordure, segment), jamais en aplat.

### Types d'entrées (6)

| Type | Hex | Fond de chip | Forme d'icône | Luminosité relative |
| --- | --- | --- | --- | --- |
| Note | #7F8EA8 | #E4E8F0 | Rectangle (page) | claire |
| Événement | #3A63C2 | #E3EAF8 | Losange | moyenne, saturée |
| Situation | #2F4F73 | #E1E9F2 | Barre horizontale (durée) | sombre |
| Ressenti | #8B7BD6 | #ECE8F8 | Cercle | moyenne, violette |
| Séance | #3E8E7E | #E2F1EE | Carré arrondi (cadre) | moyenne, verte |
| Médicament | #B07A2A | #FAF0DC | Capsule | moyenne, chaude |

Lecture de la série : trois bleus séparés par la luminosité (clair, moyen, sombre), un violet, un vert, un ocre. En vision deutéranope, la séance et le médicament restent différents par la luminosité et la forme ; en noir et blanc, la forme seule suffit.

### Familles d'émotions (8)

Utilisées sur les pastilles de ressenti, la courbe de tendance et les facettes de recherche. Une émotion personnalisée hérite de la couleur de sa famille.

| Famille | Hex | Valence | Choix |
| --- | --- | --- | --- |
| Joie | #D9A426 | positive | Doré ; seule couleur chaude claire de la palette |
| Confiance | #3E8E7E | positive | Même vert-bleu que la séance : contextes disjoints, aucun conflit à l'écran |
| Peur / anxiété | #7A68C9 | négative | Alignée sur la lavande du ressenti, un cran plus saturée |
| Surprise | #2E9AA8 | neutre | Turquoise |
| Tristesse | #3C66A6 | négative | Bleu, plus terne que le cobalt |
| Dégoût | #6E7F3E | négative | Olive |
| Colère | #B8463C | négative | Brique, distincte du rouge d'erreur #9A2F22 |
| Anticipation | #D07A2C | neutre | Orange |

Les quatre familles négatives diffèrent par la luminosité (violet moyen, bleu moyen-sombre, olive sombre, brique moyen) et chaque pastille porte une icône de famille.

### Intensité et valence

- **Intensité 0-10** : opacité de la couleur de famille par paliers — 35 % (0-2), 55 % (3-5), 80 % (6-8), 100 % (9-10). Pas de dégradé continu : illisible à 11 px.
- **Curseur d'intensité** : piste `surface-sunken`, remplissage et poignée en `feeling`, valeur en `feeling-text`.
- **Valence sur la courbe de tendance** : ligne en `feeling` ; en V2 quand la valence est connue, zone sous la courbe à 12 % d'opacité, #ECE8F8 pour le négatif, #E4F2EA pour le positif ; en V1 (valence nulle) aucune zone.
- Marqueurs sur la courbe : séances en `primary`, changements de médicament en `entry-medication`.

### Liens et suggestions IA

| Élément | Style |
| --- | --- |
| Lien accepté | Trait #8A94A6, 1,5 px, flèche pleine |
| Types de liens | Même couleur, styles de trait : `triggers` plein, `worsens` tiret long, `relieves` tiret court, `co_occurs` pointillé sans flèche, `follows` plein fin, `treats` double |
| Lien sélectionné | `primary`, 2,5 px |
| Suggestion IA `proposed` (V2) | Pointillé #B9C2D1, étiquette « suggéré » en `text-secondary` ; jamais colorée, jamais lavande |
| Lien rejeté ou remplacé | Non affiché |

Les liens n'ont pas de couleur propre : dans un graphe de cent arêtes, des traits colorés étoufferaient les nœuds, qui sont la seule chose à lire.

## 5. Application écran par écran

Pour chaque écran du document B : ce qui est en cobalt, ce qui est en lavande, ce qui reste neutre. La règle d'or se vérifie à chaque ligne : un seul élément cobalt par zone, la lavande seulement sur des ressentis.

| Écran | Cobalt (`primary`) | Lavande (`feeling`) | Couleurs métier | Tout le reste |
| --- | --- | --- | --- | --- |
| Liste des patients | « Nouveau patient », ligne sélectionnée (fond `primary-tint`) | Pastilles d'émotions dominantes des 30 jours (famille) | — | Neutres ; flags en ambre |
| Vue d'ensemble | Onglet actif, « Démarrer la séance », pastille « séance en cours », marqueurs de séance sur la courbe | Courbe d'intensité, pastilles des ressentis dans « cette séance » | Situations actives en `entry-situation`, médicament actif en `entry-medication` | Cartes blanches sur fond #EEF1F6 ; titres de blocs en `text-secondary` majuscules |
| Timeline | Carte sélectionnée (bordure 1,5 px), filtres actifs (chip `primary-tint`) | Cartes de ressentis : bordure gauche 3 px et pastille ; opacité selon l'intensité | Bordure gauche 3 px de la couleur du type ; bandes de situations en cours en `entry-situation` à 25 % | Axe et graduations en `border-subtle` ; pastille « dit en séance du… » en `text-secondary` |
| Graphe | Nœud et arêtes sélectionnés | Nœuds ressentis (cercles) | Nœuds colorés par type, forme par type ; super-nœuds regroupés en `surface-sunken` | Arêtes #8A94A6 ; suggestions V2 en pointillé #B9C2D1 |
| Éditeur d'entrée | « Enregistrer », focus des champs, bandeau de séance | Onglet Ressenti sélectionné (`feeling-tint`), curseur d'intensité, aperçu | Onglets de type : pastille de la couleur du type, texte neutre | Fond blanc, bordures `border-strong` sur les champs |
| Barre de capture | Bordure 1,5 px et halo `primary-tint` au focus, chip « déclenché par… » | Chip « Ressenti » de l'aperçu | Chips neutres pour motion, intensité, date, séance | Texte en monospace `text-primary` |
| Éditeur de lien | « Créer le lien », type sélectionné (fond `primary-tint`) | Cible ou source si c'est un ressenti (pastille) | Pastilles de type sur source et cible | Phrase lisible en `text-primary`, justification en `text-secondary` |
| Recherche | Facette cochée, résultat sélectionné | Pastilles des résultats de type ressenti | Pastille de type sur chaque résultat | Surlignage des mots trouvés : fond #FBF1DC (ambre très pâle), jamais cobalt ni lavande |
| Paramètres | Bouton « Enregistrer », interrupteurs actifs | Aperçu d'une émotion personnalisée | — | Neutres |
| Annuaire public | « Rechercher », « Voir le profil », onglet actif, chip « Accepte de nouveaux patients » (`primary-tint`) | — (aucun ressenti sur une page publique) | Profession : psychologue en `feeling-tint` / `feeling-text`, psychiatre en #E1E9F2 / #2F4F73 | Fiches blanches, fond #EEF1F6, tags en `surface-sunken` |

Deux exceptions délibérées : le surlignage de recherche utilise l'ambre pâle pour ne pas se confondre avec une sélection cobalt ; l'annuaire public emploie la lavande pour la profession « psychologue » parce qu'aucun ressenti n'y apparaît, et qu'elle y rappelle l'identité du produit.

## 6. Mode sombre et réglage « contraste doux »

Deux réponses au seul vrai défaut de la palette, le fond froid : un mode sombre complet dès la V1, et un réglage qui adoucit le fond clair sans changer le reste.

### Mode sombre

Mêmes rôles, mêmes règles ; les neutres restent bleutés pour garder l'identité, le cobalt et la lavande sont éclaircis pour rester lisibles sur fond foncé.

| Token | Clair | Sombre |
| --- | --- | --- |
| `surface-ground` | #EEF1F6 | #151A22 |
| `surface-raised` | #FFFFFF | #1E2530 |
| `surface-sunken` | #E4E8F0 | #283040 |
| `border-subtle` | #D6DCE6 | #334052 |
| `border-strong` | #B9C2D1 | #4A586E |
| `text-primary` | #1E2633 | #E6EAF2 |
| `text-secondary` | #5A6679 | #A3ADC0 |
| `primary` | #3A63C2 | #7A9BE8 |
| `primary-hover` | #2C4C99 | #9DB4E6 |
| `primary-tint` | #E3EAF8 | #243457 |
| `feeling` | #8B7BD6 | #A99BE6 |
| `feeling-tint` | #ECE8F8 | #2F2A4E |
| `feeling-text` | #4E3F9E | #CFC6F2 |

Types d'entrées en sombre : note #9DA9BE, événement #7A9BE8, situation #7F9CC2, séance #6BB5A5, médicament #D6A24E ; chips en fond de la couleur à 18 % d'opacité sur `surface-raised`. Familles d'émotions : mêmes teintes, luminosité +15 %.

Le texte sur fond sombre reste à 4,5:1 minimum (vérifié : #E6EAF2 sur #1E2530 = 12,4:1 ; #A3ADC0 sur #1E2530 = 6,6:1 ; #7A9BE8 sur #1E2530 = 5,1:1).

### Réglage « contraste doux »

Dans les préférences, un interrupteur remplace le fond #EEF1F6 par #F3F4F7 (moins bleuté) et la bordure fine par #DDE1E8. Rien d'autre ne change : les cartes, le cobalt et la lavande restent identiques, donc l'identité reste reconnaissable. Ce réglage est enregistré par praticien et suit l'utilisateur d'un appareil à l'autre.

### Bascule

Le mode suit la préférence système par défaut (`prefers-color-scheme`), avec un choix manuel clair / sombre / système dans les préférences. Le changement est instantané, sans rechargement, grâce aux variables CSS de la section 8.

## 7. Accessibilité

La palette est conforme WCAG 2.1 AA pour tout texte et tout élément porteur de sens ; les couleurs métier sont toujours doublées par une forme ou un libellé.

### Contrastes vérifiés (mode clair)

| Couple | Rapport | Statut |
| --- | --- | --- |
| `text-primary` #1E2633 sur blanc | 15,1:1 | AAA |
| `text-primary` sur `surface-ground` #EEF1F6 | 13,2:1 | AAA |
| `text-secondary` #5A6679 sur blanc | 6,2:1 | AA (AAA en 18 px+) |
| `text-secondary` sur `surface-ground` | 5,4:1 | AA |
| Blanc sur `primary` #3A63C2 | 5,7:1 | AA |
| `primary-hover` #2C4C99 sur `primary-tint` #E3EAF8 | 7,6:1 | AAA |
| `feeling-text` #4E3F9E sur `feeling-text` tint #ECE8F8 | 7,4:1 | AAA |
| `feeling` #8B7BD6 sur blanc (icône, 8 px+) | 3,6:1 | AA graphique (3:1) ; jamais pour du texte |
| `entry-note` #7F8EA8 sur blanc (icône) | 3,3:1 | AA graphique |
| `entry-situation` #2F4F73 sur blanc | 8,7:1 | AAA |
| `entry-session` #3E8E7E sur blanc (icône) | 3,9:1 | AA graphique |
| `entry-medication` #B07A2A sur blanc (icône) | 3,4:1 | AA graphique |
| Erreur #9A2F22 sur #FBE9E7 | 7,2:1 | AAA |
| Avertissement #7A5A12 sur #FBF1DC | 6,8:1 | AAA |

Conséquence : les couleurs de types et de familles sont réservées aux icônes, pastilles, bordures et segments (seuil 3:1) ; le texte d'une chip est toujours en `text-primary` ou en teinte « texte » dédiée (`feeling-text`, #2F4F73…), jamais dans la couleur de la pastille.

### Daltonisme

| Déficience | Couples à risque | Garantie |
| --- | --- | --- |
| Deutéranopie / protanopie (rouge-vert) | Séance vert-bleu vs médicament ocre ; colère brique vs dégoût olive | Luminosité différente dans chaque couple ; formes et icônes distinctes |
| Tritanopie (bleu-jaune) | Cobalt vs lavande ; tristesse vs peur | Lavande plus claire que le cobalt ; icône de famille sur chaque pastille |
| Achromatopsie | Tout | Six formes d'icônes de type, six styles de traits de lien, libellé sur chaque émotion |

### Autres garanties

- Anneau de focus cobalt 2 px + halo 4 px `primary-tint` sur tout élément interactif, visible sur fond blanc comme sur `surface-ground`.
- Les états (actif, sélectionné, désactivé) combinent couleur et autre signal : soulignement, graisse, opacité, icône.
- `prefers-reduced-motion` respecté ; aucune information portée par une animation de couleur.
- Test automatisé axe-core sur chaque écran dans la suite e2e ; test unitaire de contraste sur le fichier de tokens (section 8).

## 8. Mise en œuvre

Une seule source de vérité, un fichier de tokens, lu par Angular Material, par les composants maison (timeline, barre de capture) et par les bibliothèques de graphe et de courbe ; un test empêche toute dérive.

### Thème Angular Material (M3)

```scss
@use '@angular/material' as mat;

$nordique-primary: (
  50: #E3EAF8, 100: #C3D2F0, 200: #9DB4E6, 300: #7A9BE8,
  400: #557CD6, 500: #3A63C2, 600: #2C4C99, 700: #243E7C,
  800: #1C3060, 900: #142245,
  contrast: (50: #1E2633, 100: #1E2633, 200: #1E2633, 300: #1E2633,
             400: #FFFFFF, 500: #FFFFFF, 600: #FFFFFF, 700: #FFFFFF,
             800: #FFFFFF, 900: #FFFFFF)
);

$nordique-neutral: (
  0: #FFFFFF, 10: #EEF1F6, 20: #E4E8F0, 30: #D6DCE6, 40: #B9C2D1,
  50: #8A94A6, 60: #5A6679, 70: #3A4454, 80: #1E2530, 90: #151A22, 100: #1E2633
);

$nordique-error: (
  50: #FBE9E7, 500: #9A2F22, 700: #7A2419,
  contrast: (50: #1E2633, 500: #FFFFFF, 700: #FFFFFF)
);
```

### Variables CSS (lues par tous les composants)

```css
:root {
  --surface-ground: #EEF1F6; --surface-raised: #FFFFFF; --surface-sunken: #E4E8F0;
  --border-subtle: #D6DCE6;  --border-strong: #B9C2D1;
  --text-primary: #1E2633;   --text-secondary: #5A6679; --text-disabled: #8A94A6;
  --primary: #3A63C2; --primary-hover: #2C4C99; --primary-tint: #E3EAF8; --primary-on: #FFFFFF;
  --feeling: #8B7BD6; --feeling-tint: #ECE8F8; --feeling-text: #4E3F9E;
  --entry-note: #7F8EA8; --entry-event: #3A63C2; --entry-situation: #2F4F73;
  --entry-feeling: #8B7BD6; --entry-session: #3E8E7E; --entry-medication: #B07A2A;
  --emotion-joy: #D9A426; --emotion-trust: #3E8E7E; --emotion-fear: #7A68C9; --emotion-surprise: #2E9AA8;
  --emotion-sadness: #3C66A6; --emotion-disgust: #6E7F3E; --emotion-anger: #B8463C; --emotion-anticipation: #D07A2C;
  --danger: #9A2F22; --danger-bg: #FBE9E7; --warning: #7A5A12; --warning-bg: #FBF1DC;
  --success: #1E6B45; --success-bg: #E4F2EA; --flag: #B8862E; --flag-bg: #FBF1DC;
  --link: #8A94A6; --link-proposed: #B9C2D1; --search-highlight: #FBF1DC;
}
:root[data-theme="dark"] {
  --surface-ground: #151A22; --surface-raised: #1E2530; --surface-sunken: #283040;
  --border-subtle: #334052;  --border-strong: #4A586E;
  --text-primary: #E6EAF2;   --text-secondary: #A3ADC0; --text-disabled: #6F7A90;
  --primary: #7A9BE8; --primary-hover: #9DB4E6; --primary-tint: #243457;
  --feeling: #A99BE6; --feeling-tint: #2F2A4E; --feeling-text: #CFC6F2;
  --entry-note: #9DA9BE; --entry-event: #7A9BE8; --entry-situation: #7F9CC2;
  --entry-feeling: #A99BE6; --entry-session: #6BB5A5; --entry-medication: #D6A24E;
}
:root[data-theme="soft"] { --surface-ground: #F3F4F7; --border-subtle: #DDE1E8; }
```

Cytoscape (graphe) et ECharts (courbe) lisent ces variables au démarrage et à chaque changement de thème ; les exports PDF utilisent le thème clair quel que soit le réglage de l'écran.

### Test de non-régression

Un test unitaire charge le fichier de tokens, calcule le rapport de contraste de chaque couple déclaré (texte sur surface, texte sur teinte, blanc sur primaire) et échoue sous 4,5:1 pour le texte et 3:1 pour les icônes. Un second test vérifie qu'aucun composant Angular ne contient de valeur hexadécimale en dur (règle ESLint `no-hardcoded-color`). Ces deux tests tournent dans la CI et protègent la palette contre un agent qui « ajusterait » une couleur.

### Règles d'usage

| À faire | À ne pas faire |
| --- | --- |
| Un seul élément cobalt par zone visible | Deux boutons pleins côte à côte |
| Lavande uniquement sur un ressenti | Lavande sur un lien, un bouton, un flag, une suggestion |
| Couleur métier sur pastille, icône, bordure 3 px, segment | Aplat de carte, fond de panneau coloré |
| Texte de chip en `text-primary` ou teinte « texte » | Texte dans la couleur de la pastille |
| Rouge pour les erreurs système et les actions destructives | Rouge sur un patient, une entrée, une émotion |
| Fond #EEF1F6 dans les marges, blanc sous le texte | Texte de corps directement sur #EEF1F6 |
| Tokens partout | Valeur hexadécimale dans un composant |
| Forme ou libellé en plus de la couleur | Information portée par la couleur seule |

##*À valider avec les praticiens pilotes** : les huit couleurs de familles d'émotions, en même temps que le vocabulaire (document A, A4.5), et le réglage « contraste doux » lors des premiers tests sur de longues sessions.
