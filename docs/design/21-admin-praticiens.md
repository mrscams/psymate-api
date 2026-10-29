# Écran 21 · Admin › Praticiens

> Spécification pour agents. Maquette : `21-admin-praticiens.html` / `.png`. Palette : Nordique (barre admin sombre). Document A, module `identity.admin` (`/api/admin/practitioners`), section A10 ; Document C, C6, **tâche M1-14b** (G Q27). **Ne dépend jamais des modules cliniques** (règle ArchUnit).
>
> Mis à jour le 29 octobre 2026 (G) : tâche backend **M1-14b** créée (Q27) ; l'invitation nominative est retirée (inscription ouverte, Q21) ; l'onglet Signalements est V1.1 (Q26) ; l'onglet Vocabulaire administre la liste fermée des spécialités et publics de l'annuaire (Q15) ; `kind = 'suspended'` (E).

## Objectif
Vue de tous les comptes praticiens pour l'équipe d'administration : état de vérification, publication dans l'annuaire, sécurité du compte, et les deux actions de compte (suspendre, réactiver). Rien sur les patients : pas de compteur, pas de dernière activité clinique.

## Route & module
- Route : `/admin/praticiens` (rôle `admin`, 2FA obligatoire).
- API : `GET /api/admin/practitioners?q&status&profession&directory&cursor` → `{ items[], total, counts{approved, under_review, needs_more, suspended} }` ; chaque item : `id, displayName, email, profession, city, verification{status, since, overdue}, directory{published}, twoFactorEnabled, createdAt, lastLoginAt, purgeAt?`.
- `POST /api/admin/practitioners/{id}/suspend` `{ reason }` → révoque toutes les sessions, bloque la connexion, e-mail au praticien avec le motif, ligne `verification_decision` (`kind = 'suspended'`), `security_event` (`admin.suspend`), événement `PractitionerSuspended` (dépublie la fiche, M5-06).
- `POST /api/admin/practitioners/{id}/reactivate` `{ reason }` → statut précédent restauré (`approved` ou `docs_pending`), e-mail, `verification_decision` (`kind = 'reactivated'`).
- Export CSV : mêmes colonnes que le tableau, filtres appliqués, jamais de données cliniques ; l'export est journalisé.
- *Retiré (G Q21)* : `POST /api/admin/practitioners/invite`. L'inscription est ouverte ; un administrateur qui veut inviter quelqu'un lui envoie simplement le lien `psymate.com/inscription`.

## Structure
1. **Barre admin** (fond `#1E2633`) : logo PsyMate + « Admin », onglets Validation (badge n), **Praticiens**, Vocabulaire (liste fermée de l'annuaire), Journal ; Signalements en V1.1 ; rappel « Aucune donnée patient n'est accessible depuis l'administration » ; avatar admin.
2. **En-tête** : « Praticiens · 47 » + répartition (approuvés / en vérification / compléments demandés / suspendus), recherche, « Exporter CSV ».
3. **Filtres** : segments Tous / Approuvés / En vérification / Suspendus ; select profession ; select annuaire (tous / publiés / non publiés).
4. **Tableau** : Praticien (initiales, nom, e-mail), Profession · ville, Vérification (badge coloré + délai ; « hors délai » au-delà de 2 jours ouvrés, comme l'écran 20), Annuaire (Publié / Non publié / —), 2FA (Activée / Non), Inscrit · dernière connexion (+ « purge des données le … » pour un compte refusé), Actions (« Ouvrir le dossier » vers l'écran 20, « Voir la fiche », « Décision », « Suspendre… », « Réactiver… »).
5. **Pagination** par curseur « 6 sur 47 · Afficher plus ».
6. **Note de bas de page** : effet de la suspension, périmètre de l'administration.

## Interactions
- « Suspendre… » → boîte de dialogue : motif obligatoire (min. 20 caractères), case « envoyer le motif au praticien » cochée et non décochable, confirmation ; après : badge « Suspendu », sessions révoquées (le praticien reçoit `403 ACCOUNT_SUSPENDED` à sa prochaine requête et est redirigé vers l'écran 27 ; pas de WebSocket en V1).
- « Réactiver… » → motif obligatoire ; si le compte avait été **refusé**, réactiver le renvoie en `docs_pending` (il doit redéposer), pas en `approved`.
- Un admin ne peut pas suspendre son propre compte ni le dernier admin actif.
- Tri par colonne sur Praticien, Vérification, Inscrit ; filtres dans l'URL (`?status=under_review&overdue=1`).
- La ligne d'un praticien « hors délai » s'affiche avec un point ambre ; l'onglet Validation reprend le même compteur.

## Tokens
- Badges de vérification : Approuvé `#E4F2EA`/`#1E6B45`, En vérification `#E3EAF8`/`#2C4C99`, Complément demandé `#FBF1DC`/`#7A5A12`, Refusé / Suspendu `#FBE9E7`/`#9A2F22`.
- Bouton « Suspendre… » : texte `#9A2F22` sur fond blanc, bordure `#F0C9C4` ; « Réactiver… » : `#1E6B45`.
- Barre admin : `#1E2633`, onglet actif souligné `#8DB7FF`.

## Accessibilité
- Tableau `<table>` avec `<caption>` masqué visuellement et `<th scope="col">` ; tri exposé par `aria-sort`.
- Boîtes de dialogue de suspension/réactivation : `role="dialog"`, focus sur le champ motif, bouton destructif en dernier.
- Les badges colorés portent un texte explicite ; aucun état transmis par la couleur seule.

## Critères d'acceptation
- [ ] Utilisateur sans rôle `admin` ou sans 2FA → `403` sur `/api/admin/*` et redirection côté client.
- [ ] Suspendre avec motif → sessions du praticien invalides (test : un appel `/api/me` du praticien renvoie `403 ACCOUNT_SUSPENDED`, puis l'écran 27 s'affiche), e-mail envoyé contenant le motif, ligne de journal visible dans l'écran 22, fiche publique en `404` en moins d'une minute.
- [ ] Suspendre sans motif ou motif < 20 caractères → `422`.
- [ ] Réactiver un compte `rejected` → statut `docs_pending` ; un compte suspendu après approbation → `approved`.
- [ ] Export CSV → colonnes exactes du tableau, aucune colonne liée aux patients ; une ligne « export » apparaît dans le journal.
- [ ] Test ArchUnit : `identity.admin` n'importe aucun package de `clinical`, `patients`, `linking`, `overview`, `search`.

## Hors périmètre V1
Invitation nominative (G Q21), signalements (V1.1, G Q26), rôles admin différenciés (lecture seule / vérificateur), suppression définitive manuelle d'un compte (la purge est automatique), statistiques d'usage.
