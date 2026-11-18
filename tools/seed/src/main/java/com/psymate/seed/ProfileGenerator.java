package com.psymate.seed;

import com.psymate.seed.Plan.ProfileItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Générateur PUR et déterministe du Profil 360 d'un patient ordinaire (M13-03, J3) : 0 à 25
 * informations réparties sur les quatre rubriques, dont environ un patient sur cinq sans aucune
 * information (état vide de l'onglet). Dans {@code history}, un mélange de périodes avec année («
 * 2009 », « Vers 2012 », « 2004 – 2007 »), de libellés sans année (« Enfance ») et d'informations
 * sans période ; les années tombent entre la naissance du patient et aujourd'hui.
 *
 * <p>Tout est fictif : phrases génériques, sans nom propre, aucune donnée réelle.
 */
final class ProfileGenerator {

  /** Plafond du nombre d'informations par patient ordinaire (le plafond de l'API est 500). */
  static final int MAX_ITEMS = 25;

  /** Part des patients sans aucune information. */
  static final double EMPTY_RATIO = 0.2;

  /** Les quatre rubriques, dans l'ordre de lecture de l'API (G Q64). */
  enum Category {
    HISTORY("history"),
    FAMILY("family"),
    HEALTH("health"),
    VALUES_GOALS("values_goals");

    final String code;

    Category(String code) {
      this.code = code;
    }
  }

  /** Un souvenir type : âge possible du patient au moment des faits et phase de vie. */
  private record Memory(String title, String detail, int minAge, int maxAge, String phase) {}

  /** Une information sans période. */
  private record Fact(String title, String detail) {}

  private static final List<Memory> HISTORY =
      List.of(
          new Memory(
              "Étés à la campagne chez les grands-parents",
              "En garde le souvenir d'une maison pleine et de longues journées dehors.",
              4,
              11,
              "Enfance"),
          new Memory(
              "Naissance d'un petit frère",
              "Se souvient d'avoir dû « devenir grand » du jour au lendemain.",
              2,
              10,
              "Enfance"),
          new Memory(
              "Déménagement dans une autre région",
              "Changement d'école en cours d'année, difficile à vivre.",
              6,
              13,
              "Enfance"),
          new Memory(
              "Divorce des parents", "Garde alternée jusqu'à l'adolescence.", 6, 15, "Enfance"),
          new Memory(
              "Moqueries répétées au collège",
              "N'en a parlé à personne à l'époque.",
              11,
              14,
              "Collège"),
          new Memory(
              "Redoublement de la seconde",
              "Vécu comme une honte, puis comme un nouveau départ.",
              15,
              17,
              "Adolescence"),
          new Memory("Premier emploi d'été dans une boulangerie", null, 16, 19, "Adolescence"),
          new Memory("Baccalauréat obtenu avec mention", null, 17, 19, "Adolescence"),
          new Memory(
              "Départ du domicile familial pour les études",
              "Premier logement indépendant, entre liberté et solitude.",
              18,
              21,
              "Études"),
          new Memory(
              "Année d'études à l'étranger",
              "Dit y avoir « appris à se débrouiller ».",
              19,
              24,
              "Études"),
          new Memory("Service civique dans une association", null, 18, 25, "Jeunesse"),
          new Memory(
              "Premier poste en contrat à durée indéterminée",
              null,
              21,
              28,
              "Début de la vie professionnelle"),
          new Memory(
              "Début de la vie de couple actuelle",
              "Rencontre par des amis communs.",
              20,
              40,
              "Vie adulte"),
          new Memory("Installation dans la ville actuelle", null, 20, 45, "Vie adulte"),
          new Memory(
              "Naissance du premier enfant",
              "Grossesse attendue, premiers mois épuisants.",
              24,
              40,
              "Vie adulte"),
          new Memory("Achat d'un appartement", null, 27, 50, "Vie adulte"),
          new Memory(
              "Licenciement économique",
              "Six mois de recherche d'emploi, vécus comme une remise en cause.",
              25,
              58,
              "Vie adulte"),
          new Memory(
              "Accident de voiture sans blessure grave",
              "Appréhension de la conduite pendant plusieurs mois.",
              18,
              55,
              "Vie adulte"),
          new Memory(
              "Reconversion professionnelle",
              "Formation suivie le soir pendant deux ans.",
              30,
              55,
              "Vie adulte"),
          new Memory("Séparation après une longue vie commune", null, 28, 60, "Vie adulte"),
          new Memory(
              "Décès d'un grand-parent proche",
              "Premier deuil vécu de près.",
              10,
              45,
              "Vie adulte"),
          new Memory(
              "Hospitalisation d'un parent",
              "A assuré les allers-retours à l'hôpital pendant un mois, sans relais.",
              16,
              60,
              "Vie adulte"));

  private static final List<Fact> FAMILY =
      List.of(
          new Fact("Mère, infirmière à la retraite", "Appels fréquents, relation proche."),
          new Fact("Père, artisan menuisier", "Peu de contacts depuis l'adolescence."),
          new Fact("Sœur aînée", "Confidente, habite à une heure de route."),
          new Fact("Frère cadet", "Se voient surtout aux fêtes de famille."),
          new Fact(
              "Partenaire de vie depuis plusieurs années",
              "Soutien au quotidien, quelques tensions sur le partage des tâches."),
          new Fact("Deux enfants à l'école primaire", null),
          new Fact("Grand-mère paternelle", "Figure rassurante de l'enfance."),
          new Fact("Ami d'enfance", "Se voient une fois par mois, toujours disponible."),
          new Fact("Collègue de confiance", null),
          new Fact("Voisine âgée, aidée chaque semaine", "S'y sent utile."),
          new Fact("Beaux-parents", "Relations courtoises mais distantes."),
          new Fact("Chien adopté au refuge", "Promenade matin et soir, « une vraie routine »."),
          new Fact("Groupe d'amis de la chorale", null),
          new Fact("Oncle et parrain", "Présent dans les moments difficiles."));

  private static final List<Fact> HEALTH =
      List.of(
          new Fact("Migraines depuis l'adolescence", "Traitées à la demande par le médecin."),
          new Fact("Asthme léger", null),
          new Fact("Hypothyroïdie traitée", "Bilan sanguin tous les six mois."),
          new Fact("Douleurs lombaires chroniques", "Séances de kinésithérapie par périodes."),
          new Fact(
              "Épisode dépressif il y a plusieurs années",
              "Suivi psychologique de huit mois, sans traitement."),
          new Fact("Troubles du sommeil intermittents", null),
          new Fact("Allergie aux pollens", null),
          new Fact("Appendicectomie dans l'enfance", null),
          new Fact("Consommation d'alcool occasionnelle", "Aucun excès rapporté."),
          new Fact("Arrêt du tabac il y a cinq ans", null),
          new Fact("Course à pied deux fois par semaine", "Dit que « ça vide la tête »."),
          new Fact("Suivi régulier par le médecin traitant", null),
          new Fact("Tension artérielle surveillée", null));

  private static final List<Fact> VALUES_GOALS =
      List.of(
          new Fact("Retrouver du temps pour soi", null),
          new Fact("Reprendre une activité artistique", "Le dessin, abandonné après le lycée."),
          new Fact("Changer de poste d'ici deux ans", null),
          new Fact("La famille passe avant tout", null),
          new Fact("Tient à son indépendance", "Refuse de demander de l'aide, même quand il faut."),
          new Fact("Apprendre à dire non au travail", null),
          new Fact("Projet de vivre à la campagne", "En parle depuis plusieurs années."),
          new Fact("S'engager dans une association", null),
          new Fact("Faire un long voyage", "Un projet repoussé depuis les études."),
          new Fact("Préparer un concours de la fonction publique", null),
          new Fact("Honnêteté envers ses proches", "Une valeur héritée de sa mère."),
          new Fact("Courir un semi-marathon", null),
          new Fact("Ralentir le rythme et mieux dormir", null),
          new Fact("Fonder une famille", null));

  private ProfileGenerator() {}

  /**
   * Profil d'un patient né en {@code birthYear}, l'année courante étant {@code currentYear}.
   *
   * @param rnd générateur propre au profil (le dossier n'en dépend pas)
   * @return les informations dans l'ordre d'écriture, mélangé entre rubriques
   */
  static List<ProfileItem> generate(Random rnd, int birthYear, int currentYear) {
    if (rnd.nextDouble() < EMPTY_RATIO) {
      return List.of();
    }
    int age = currentYear - birthYear;
    Map<Category, List<Integer>> remaining = new EnumMap<>(Category.class);
    remaining.put(Category.HISTORY, indices(HISTORY.size()));
    remaining.put(Category.FAMILY, indices(FAMILY.size()));
    remaining.put(Category.HEALTH, indices(HEALTH.size()));
    remaining.put(Category.VALUES_GOALS, indices(VALUES_GOALS.size()));
    remaining.get(Category.HISTORY).removeIf(i -> HISTORY.get(i).minAge() > age);

    int count = 1 + rnd.nextInt(MAX_ITEMS);
    List<ProfileItem> items = new ArrayList<>();
    while (items.size() < count) {
      Category category = pick(rnd);
      List<Integer> pool = remaining.get(category);
      if (pool.isEmpty()) {
        if (remaining.values().stream().allMatch(List::isEmpty)) {
          break;
        }
        continue;
      }
      int index = pool.remove(rnd.nextInt(pool.size()));
      items.add(
          switch (category) {
            case HISTORY -> memory(rnd, HISTORY.get(index), birthYear, currentYear);
            case FAMILY -> fact(rnd, category, FAMILY.get(index));
            case HEALTH -> fact(rnd, category, HEALTH.get(index));
            case VALUES_GOALS -> fact(rnd, category, VALUES_GOALS.get(index));
          });
    }
    return List.copyOf(items);
  }

  /** Rubrique tirée : l'histoire est la plus fournie, comme dans la maquette. */
  private static Category pick(Random rnd) {
    double r = rnd.nextDouble();
    if (r < 0.40) {
      return Category.HISTORY;
    } else if (r < 0.65) {
      return Category.FAMILY;
    } else if (r < 0.82) {
      return Category.HEALTH;
    }
    return Category.VALUES_GOALS;
  }

  private static ProfileItem memory(Random rnd, Memory m, int birthYear, int currentYear) {
    String detail = rnd.nextDouble() < 0.5 ? m.detail() : null;
    double mode = rnd.nextDouble();
    if (mode < 0.5) {
      int maxAge = Math.min(m.maxAge(), currentYear - birthYear);
      int year = birthYear + m.minAge() + rnd.nextInt(maxAge - m.minAge() + 1);
      String label =
          switch (rnd.nextInt(3)) {
            case 0 -> Integer.toString(year);
            case 1 -> "Vers " + year;
            default -> year + " – " + Math.min(currentYear, year + 1 + rnd.nextInt(4));
          };
      if (label.endsWith(" – " + year)) {
        label = Integer.toString(year); // intervalle vide : l'année seule
      }
      return new ProfileItem(Category.HISTORY.code, m.title(), detail, label, year);
    } else if (mode < 0.8) {
      return new ProfileItem(Category.HISTORY.code, m.title(), detail, m.phase(), null);
    }
    return new ProfileItem(Category.HISTORY.code, m.title(), detail, null, null);
  }

  private static ProfileItem fact(Random rnd, Category category, Fact f) {
    String detail = rnd.nextDouble() < 0.6 ? f.detail() : null;
    return new ProfileItem(category.code, f.title(), detail, null, null);
  }

  private static List<Integer> indices(int n) {
    List<Integer> list = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      list.add(i);
    }
    return list;
  }
}
