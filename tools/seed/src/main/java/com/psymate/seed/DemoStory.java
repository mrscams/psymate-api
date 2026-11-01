package com.psymate.seed;

import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatientPlan;
import com.psymate.seed.Plan.Practitioner;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Le patient DEMO (G, Q22) : « DEMO · Camille R. », suivie par la psychologue Claire sur 18 mois,
 * 20 séances. Le récit est écrit à la main, pas tiré d'un corpus : surmenage au travail, arrêt,
 * traitement par sertraline (50 puis 100 mg), reprise progressive, tensions de couple, puis un
 * deuil resté en suspens qui remonte et ouvre une réflexion sur le travail. Deux situations sont
 * encore actives à la fin (le deuil et la réflexion sur le travail).
 *
 * <p>Toutes les dates sont relatives à aujourd'hui. Les intensités des ressentis dessinent trois
 * motifs lisibles dans la vue d'ensemble : une anxiété forte avant l'arrêt, une détente progressive
 * sous traitement, un pic de chagrin au moment du deuil puis une accalmie.
 *
 * <p>Tout est fictif : aucun lien avec une personne réelle.
 */
final class DemoStory {

  private DemoStory() {}

  private static final String REASON_CLOSED = "Situation terminée";

  // jour (depuis le début du suivi), heure, durée, modalité des 20 séances
  private static final int[] DAY = {
    0, 14, 28, 45, 60, 75, 95, 112, 130, 160, 190, 220, 250, 285, 315, 350, 385, 420, 465, 515
  };
  private static final int[] HOUR = {
    10, 18, 18, 10, 18, 10, 17, 18, 10, 17, 18, 10, 17, 18, 10, 17, 10, 18, 17, 10
  };
  private static final int[] DURATION = {
    60, 50, 45, 50, 50, 50, 50, 45, 50, 50, 45, 50, 50, 60, 50, 50, 50, 45, 50, 60
  };
  private static final String[] MODALITY = {
    "in_person", "video", "in_person", "in_person", "video", "in_person", "in_person", "video",
    "in_person", "in_person", "video", "in_person", "in_person", "in_person", "video", "in_person",
    "in_person", "video", "in_person", "in_person"
  };

  private static final class Story {
    final PatientBuilder b = new PatientBuilder();
    final Instant t0;
    String session;
    Instant ts;
    int minutes;

    Story(Instant today) {
      // dix-huit mois avant aujourd'hui, la dernière séance tombe donc dans le dernier mois
      this.t0 = today.minus(540, ChronoUnit.DAYS);
    }

    /** Démarre la séance numéro {@code n} (1 à 20). */
    String start(int n) {
      LocalDate day = t0.atZone(ZoneOffset.UTC).toLocalDate().plusDays(DAY[n - 1]);
      if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
        day = day.plusDays(2);
      } else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
        day = day.plusDays(1);
      }
      ts = day.atTime(HOUR[n - 1], 0).toInstant(ZoneOffset.UTC);
      minutes = DURATION[n - 1];
      session = b.startSession(ts, MODALITY[n - 1]);
      return session;
    }

    void end(String summary) {
      b.closeSession(session, minutes, summary);
    }

    /** Instant dans la séance, {@code min} minutes après le début. */
    Instant in(int min) {
      return ts.plus(Math.min(min, minutes - 1), ChronoUnit.MINUTES);
    }

    /** Instant quelques jours avant la séance, à une heure donnée. */
    Instant before(int days, int hour) {
      LocalDate d = ts.atZone(ZoneOffset.UTC).toLocalDate().minusDays(days);
      return d.atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    LocalDate date() {
      return ts.atZone(ZoneOffset.UTC).toLocalDate();
    }

    String add(String kind, Body body, LinkPlan... links) {
      return b.entry(session, kind, body.build(), List.of(links));
    }

    void close(String ref, Instant from, Instant to) {
      b.patch(ref, Body.of().occurred(from, to, "exact", null).reason(REASON_CLOSED).build());
    }
  }

  private static LinkPlan out(String type, String ref) {
    return new LinkPlan(ref, type, "outgoing");
  }

  private static LinkPlan in(String type, String ref) {
    return new LinkPlan(ref, type, "incoming");
  }

  static PatientPlan build(Instant today) {
    Story s = new Story(today.truncatedTo(ChronoUnit.DAYS));

    // ================= Séance 1 : motif de consultation
    s.start(1);
    LocalDate firstDay = s.date();
    Instant colleagueFrom =
        firstDay.minusMonths(7).withDayOfMonth(10).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant colleagueTo =
        firstDay.minusMonths(5).withDayOfMonth(20).atStartOfDay(ZoneOffset.UTC).toInstant();
    String colleague =
        s.add(
            "event",
            Body.of()
                .title("Départ non remplacé d'une collègue")
                .body(
                    "Sa collègue de bureau a quitté l'agence ; son poste n'a jamais été pourvu et ses dossiers se sont ajoutés aux siens.")
                .period(colleagueFrom, colleagueTo, FrDates.periodLabel(colleagueFrom, colleagueTo))
                .tags(List.of("travail")));
    Instant workFrom =
        firstDay.minusMonths(3).withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    String work =
        s.add(
            "situation",
            Body.of()
                .title("Surcharge de travail à l'agence")
                .body(
                    "Chargée de projet depuis six ans, elle suit trois clients en parallèle : journées de dix heures, mails le soir et le week-end, sensation de ne jamais finir.")
                .occurred(workFrom, null, "month", null)
                .tags(List.of("travail")),
            out("caused_by", colleague));
    Instant sleepFrom = firstDay.minusDays(70).atStartOfDay(ZoneOffset.UTC).toInstant();
    String sleep =
        s.add(
            "situation",
            Body.of()
                .title("Insomnie et sommeil haché")
                .body(
                    "Endormissement long, réveils vers quatre heures du matin, environ cinq heures de sommeil par nuit.")
                .occurred(sleepFrom, null, "period", FrDates.sinceLabel(sleepFrom))
                .tags(List.of("sommeil")),
            out("co_occurs", work));
    String anxiety1 =
        s.add(
            "feeling",
            Body.of()
                .feeling("anxiety", 7)
                .body("Boule au ventre le dimanche soir en pensant au lundi.")
                .at(s.in(15))
                .tags(List.of("travail")),
            in("triggers", work));
    s.add(
        "feeling",
        Body.of()
            .feeling("emptiness", 6)
            .body("Plus d'énergie en fin de journée, rien ne donne envie.")
            .at(s.in(25)),
        in("triggers", sleep));
    s.add(
        "note",
        Body.of()
            .body(
                "Motif de consultation : épuisement professionnel et troubles du sommeil. Aucune idée noire, aucun traitement en cours, premier suivi psychologique. Demande claire : « retrouver de l'énergie et ne plus avoir peur du lundi ». Alliance de travail d'emblée bonne.")
            .at(s.in(40)),
        out("co_occurs", anxiety1));
    s.end(
        "Première séance : recueil du motif, de l'histoire récente et du contexte de travail. Convention de suivi toutes les deux semaines. Agenda du sommeil proposé pour la prochaine fois.");

    // ================= Séance 2
    s.start(2);
    String clientMeeting =
        s.add(
            "event",
            Body.of()
                .title("Réunion client houleuse")
                .body(
                    "Un client a remis en cause tout le planning devant l'équipe ; elle est restée silencieuse puis a tout repris le soir même.")
                .day(s.date().minusDays(5))
                .tags(List.of("travail")),
            out("part_of", work));
    s.add(
        "feeling",
        Body.of()
            .feeling("shame", 6)
            .body("Honte de ne pas avoir su répondre au client.")
            .at(s.before(4, 21))
            .tags(List.of("travail")),
        in("triggers", work),
        in("worsens", clientMeeting));
    s.add(
        "feeling",
        Body.of()
            .feeling("sadness", 6)
            .body("Larmes dans le métro en rentrant, sans raison apparente.")
            .at(s.in(20)));
    s.add(
        "note",
        Body.of()
            .body(
                "L'agenda du sommeil montre quatre nuits sur sept à moins de cinq heures. Elle travaille sur son ordinateur jusqu'à minuit. Premier objectif : couper les mails à 21 heures.")
            .at(s.in(35))
            .tags(List.of("sommeil")),
        out("part_of", sleep));
    s.end(
        "Lecture de l'agenda du sommeil. Objectif de la quinzaine : aucun mail professionnel après 21 heures.");

    // ================= Séance 3
    s.start(3);
    String lateMail =
        s.add(
            "event",
            Body.of()
                .title("Mail d'un client à 22 heures, réponse immédiate")
                .body("Elle a répondu dans la minute, puis n'a pas dormi avant trois heures.")
                .at(s.before(3, 22))
                .tags(List.of("travail")),
            out("part_of", work));
    s.add(
        "feeling",
        Body.of()
            .feeling("anger", 6)
            .body("Colère contre le client, puis contre elle-même d'avoir répondu.")
            .at(s.before(3, 22)),
        in("worsens", lateMail));
    s.add(
        "note",
        Body.of()
            .body(
                "Premier exercice de cohérence cardiaque en séance : cinq minutes, six respirations par minute. Elle note un relâchement des épaules. Exercice à refaire matin et soir.")
            .at(s.in(30)));
    s.add(
        "feeling",
        Body.of()
            .feeling("calm", 3)
            .body("Calme et respiration plus lente à la fin de l'exercice.")
            .at(s.in(38)),
        in("relieves", s.session));
    s.end(
        "Exercice de cohérence cardiaque. Repérage des mails du soir comme déclencheur principal des nuits courtes.");

    // ================= Séance 4
    s.start(4);
    String whiteNight =
        s.add(
            "event",
            Body.of()
                .title("Nuit blanche avant la présentation du client principal")
                .at(s.before(2, 23))
                .tags(List.of("sommeil")),
            out("part_of", sleep));
    s.add(
        "feeling",
        Body.of()
            .feeling("anxiety", 8)
            .body("Mains moites et cœur qui bat vite pendant toute la présentation.")
            .at(s.before(1, 9))
            .tags(List.of("travail")),
        in("triggers", work),
        out("caused_by", whiteNight));
    s.add(
        "feeling",
        Body.of()
            .feeling("shame", 5)
            .body("Honte de ne pas tenir comme les autres collègues, qui semblent y arriver.")
            .at(s.in(22)),
        in("triggers", work));
    s.add(
        "note",
        Body.of()
            .body(
                "Dit penser parfois à « tout quitter et partir loin », sans plan ni intention. Elle n'en a parlé à personne, pas même à son compagnon. Risque évalué : faible, à surveiller.")
            .at(s.in(40))
            .privateEntry());
    s.end(
        "Séance sur la honte et la comparaison aux collègues. Question du risque abordée directement, rien d'inquiétant à ce stade.");

    // ================= Séance 5
    s.start(5);
    String gpVisit =
        s.add(
            "event",
            Body.of()
                .title("Consultation chez le médecin traitant")
                .body(
                    "Le Dr Lambert constate une tension élevée et une fatigue marquée, et parle d'un arrêt de travail.")
                .day(s.date().minusDays(3))
                .tags(List.of("travail")));
    s.add(
        "feeling",
        Body.of()
            .feeling("guilt", 7)
            .body("Culpabilité à l'idée de laisser l'équipe sans elle.")
            .at(s.in(18)),
        in("triggers", work),
        out("caused_by", gpVisit));
    s.add(
        "note",
        Body.of()
            .body(
                "Hésite entre accepter l'arrêt proposé par le médecin et « tenir encore un mois ». Nous listons ce que l'arrêt permettrait et ce qu'il coûterait. Elle penche pour accepter.")
            .at(s.in(35))
            .tags(List.of("travail")),
        out("co_occurs", gpVisit));
    s.end(
        "Aide à la décision autour de l'arrêt de travail. Elle reverra son médecin cette semaine.");

    // ================= Séance 6 : arrêt de travail
    s.start(6);
    Instant leaveFrom = s.before(5, 9);
    String leave =
        s.add(
            "situation",
            Body.of()
                .title("Arrêt de travail")
                .body(
                    "Arrêt prescrit pour six semaines, renouvelable. Objectif : ne rien faire de professionnel, dormir, marcher.")
                .at(leaveFrom)
                .tags(List.of("travail")),
            out("co_occurs", work));
    String leaveEvent =
        s.add(
            "event",
            Body.of()
                .title("Premier jour d'arrêt de travail")
                .body("Réveil sans réveil-matin, première grasse matinée depuis des années.")
                .at(leaveFrom)
                .tags(List.of("travail")),
            out("part_of", leave));
    s.add(
        "feeling",
        Body.of()
            .feeling("serenity", 4)
            .body("Soulagement de ne plus avoir à se lever à six heures.")
            .at(s.in(12)),
        in("triggers", leaveEvent));
    s.add(
        "feeling",
        Body.of()
            .feeling("guilt", 6)
            .body("Culpabilité qui revient en fin de journée.")
            .at(s.in(20)),
        in("triggers", leave));
    s.add(
        "note",
        Body.of()
            .body(
                "L'arrêt est vécu à la fois comme un soulagement et un échec. Elle prévoit de longues marches et de ne pas ouvrir sa messagerie professionnelle.")
            .at(s.in(38)));
    s.end("Mise en place de l'arrêt : cadre, rythme de la journée, rituels de sommeil.");

    // ================= Séance 7 : traitement
    s.start(7);
    String sertraline50 =
        s.add(
            "medication",
            Body.of()
                .title("Sertraline 50 mg")
                .body(
                    "Traitement démarré par le médecin traitant. Premiers effets attendus dans deux à quatre semaines.")
                .at(s.in(10))
                .medication(
                    "sertraline",
                    "50 mg",
                    "1 comprimé le matin",
                    "Dr Lambert (médecin traitant)",
                    s.date().minusDays(4)),
            out("treats", sleep));
    s.add(
        "feeling",
        Body.of()
            .feeling("apprehension", 5)
            .body("Peur des effets secondaires et de « dépendre d'un médicament ».")
            .at(s.in(18)),
        in("worsens", sertraline50));
    String walks =
        s.add(
            "event",
            Body.of()
                .title("Longues promenades au bord du canal")
                .body("Une heure de marche chaque matin depuis le début de l'arrêt.")
                .period(leaveFrom, s.in(0), FrDates.periodLabel(leaveFrom, s.in(0)))
                .tags(List.of("sommeil")),
            out("part_of", leave));
    s.add(
        "feeling",
        Body.of()
            .feeling("emptiness", 4)
            .body("Journées longues et vides en début d'arrêt.")
            .at(s.before(3, 15)),
        in("triggers", leave));
    s.add(
        "note",
        Body.of()
            .body(
                "Explications sur le traitement et sur le délai d'action. Je rappelle que les décisions médicales restent celles de son médecin. Elle note ses ressentis chaque soir.")
            .at(s.in(35)),
        out("co_occurs", sertraline50));
    s.end(
        "Psychoéducation sur le traitement. Elle garde un carnet du soir pour objectiver l'évolution.");

    // ================= Séance 8
    s.start(8);
    s.add(
        "feeling",
        Body.of()
            .feeling("calm", 4)
            .body("Moment de calme pendant la marche du matin.")
            .at(s.before(2, 10)),
        in("relieves", walks));
    s.add(
        "feeling",
        Body.of()
            .feeling("anxiety", 5)
            .body("Angoisse quand le téléphone sonne : peur que ce soit l'agence.")
            .at(s.in(15)),
        in("triggers", work));
    String sisterWeekend =
        s.add(
            "event",
            Body.of()
                .title("Week-end chez sa sœur Élise")
                .body("Deux jours sans écran, parties de cartes avec les neveux.")
                .period(s.before(6, 9), s.before(4, 18), "week-end chez Élise")
                .tags(List.of("famille")));
    s.add(
        "note",
        Body.of()
            .body(
                "Sommeil à environ six heures, avec un seul réveil. Elle se dit « moins hachée ». Les nuits restent plus courtes quand elle rumine sur l'agence.")
            .at(s.in(30))
            .tags(List.of("sommeil")),
        out("part_of", sleep));
    s.end(
        "Évolution favorable du sommeil. Le week-end chez Élise est identifié comme source de ressources.");

    // ================= Séance 9 : le couple
    s.start(9);
    Instant coupleFrom = s.before(20, 20);
    String couple =
        s.add(
            "situation",
            Body.of()
                .title("Tensions avec Mathieu")
                .body(
                    "Mathieu a du mal à comprendre l'arrêt de travail et lui reproche de « rester à la maison ». Les tâches ménagères et le temps passé ensemble cristallisent les disputes.")
                .at(coupleFrom)
                .tags(List.of("couple")),
            in("triggers", leave));
    String quarrel =
        s.add(
            "event",
            Body.of()
                .title("Dispute au sujet des tâches ménagères")
                .body(
                    "Mathieu a dit qu'elle « avait tout le temps de s'en occuper ». Elle est sortie marcher une heure.")
                .day(s.date().minusDays(6))
                .tags(List.of("couple")),
            out("part_of", couple));
    s.add(
        "feeling",
        Body.of()
            .feeling("anger", 6)
            .body("Colère froide, envie de ne plus rien dire.")
            .at(s.before(6, 21))
            .tags(List.of("couple")),
        in("worsens", quarrel),
        in("triggers", couple));
    s.add(
        "feeling",
        Body.of()
            .feeling("guilt", 6)
            .body("Culpabilité d'être « un poids » pour lui.")
            .at(s.in(25)),
        in("triggers", couple));
    s.add(
        "event",
        Body.of()
            .title("Brouille avec Sophie, amie d'enfance")
            .body("Elles ne se parlent plus depuis une dispute à un anniversaire.")
            .year(s.date().minusYears(2).getYear()));
    s.end(
        "Les tensions de couple prennent de la place. Préparation d'une conversation avec Mathieu, sans reproche.");

    // ================= Séance 10 : préparation de la reprise
    s.start(10);
    s.add(
        "feeling",
        Body.of()
            .feeling("apprehension", 6)
            .body("Appréhension de retrouver l'open space et les regards.")
            .at(s.before(3, 20))
            .tags(List.of("travail")),
        in("triggers", work));
    s.add(
        "feeling",
        Body.of()
            .feeling("interest", 4)
            .body("Curiosité de voir ce que le temps partiel va changer.")
            .at(s.in(25)));
    s.add(
        "note",
        Body.of()
            .body(
                "Préparation de la reprise à mi-temps thérapeutique : phrases pour dire non, plan pour les premiers jours, interlocuteur identifié à la RH. Elle sait que le premier lundi sera le plus dur.")
            .at(s.in(38))
            .tags(List.of("travail")),
        out("part_of", work));
    s.end("Préparation de la reprise à mi-temps. Objectif : tenir la limite des 18 heures.");

    // ================= Séance 11 : reprise et posologie
    s.start(11);
    Instant resume = s.date().minusDays(12).atTime(9, 0).toInstant(ZoneOffset.UTC);
    s.close(leave, leaveFrom, resume);
    String resumeEvent =
        s.add(
            "event",
            Body.of()
                .title("Reprise à mi-temps thérapeutique")
                .body(
                    "Reprise à 50 % avec un aménagement : pas de mails après 18 heures et un seul client suivi. Accueil chaleureux de l'équipe ; elle est partie à 18 heures comme convenu.")
                .at(resume)
                .tags(List.of("travail")),
            out("part_of", work),
            out("follows", leaveEvent));
    s.b.patch(
        sertraline50, Body.of().medicationEnds(s.date()).reason("Changement de posologie").build());
    String sertraline100 =
        s.add(
            "medication",
            Body.of()
                .title("Sertraline 100 mg")
                .body(
                    "Posologie doublée par le médecin traitant après plusieurs mois : bonne tolérance, anxiété encore présente.")
                .at(s.in(8))
                .medication(
                    "sertraline",
                    "100 mg",
                    "1 comprimé le matin",
                    "Dr Lambert (médecin traitant)",
                    s.date()),
            out("treats", sleep));
    s.add(
        "feeling",
        Body.of()
            .feeling("anxiety", 6)
            .body("Cœur qui s'accélère dans l'ascenseur le matin de la reprise.")
            .at(s.before(12, 8))
            .tags(List.of("travail")),
        in("triggers", work),
        out("caused_by", resumeEvent));
    s.add(
        "feeling",
        Body.of()
            .feeling("joy", 5)
            .body("Réunion d'équipe plus légère que prévu, quelques rires.")
            .at(s.before(7, 15)),
        in("relieves", sertraline50));
    s.add(
        "note",
        Body.of()
            .body(
                "Premiers jours de reprise vécus mieux que redouté. Elle a tenu la limite des 18 heures trois jours sur cinq. Je l'invite à noter ce qui l'a aidée les deux autres jours.")
            .at(s.in(33))
            .tags(List.of("travail")));
    s.end(
        "Bilan des premiers jours de reprise. Traitement ajusté par le médecin, tolérance bonne.");

    // ================= Séance 12
    s.start(12);
    String friendsEvening =
        s.add(
            "event",
            Body.of()
                .title("Soirée avec des amies")
                .body(
                    "Première sortie entre amies depuis des mois ; elle est restée jusqu'à minuit.")
                .at(s.before(5, 20)));
    s.add(
        "feeling",
        Body.of()
            .feeling("joy", 6)
            .body("Rire franc, sensation de redevenir elle-même.")
            .at(s.before(5, 23)),
        in("triggers", friendsEvening));
    s.add(
        "feeling",
        Body.of()
            .feeling("calm", 3)
            .body("Journée sans angoisse, la première depuis longtemps.")
            .at(s.in(20)),
        in("relieves", sertraline100));
    s.add(
        "note",
        Body.of()
            .body(
                "Sommeil autour de sept heures. Elle coupe les écrans une heure avant de se coucher. Il reste un réveil vers quatre heures un jour sur deux.")
            .at(s.in(33))
            .tags(List.of("sommeil")),
        out("part_of", sleep));
    s.end("Amélioration nette de l'humeur et du sommeil. Maintien du rythme de séances.");

    // ================= Séance 13
    s.start(13);
    String holidayQuarrel =
        s.add(
            "event",
            Body.of()
                .title("Dispute avec Mathieu à propos des vacances")
                .body(
                    "Il voulait partir une semaine chez ses parents, elle aurait voulu rester au calme.")
                .day(s.date().minusDays(2))
                .tags(List.of("couple")),
            out("part_of", couple));
    s.add(
        "feeling",
        Body.of()
            .feeling("sadness", 6)
            .body("Tristesse après la dispute, impression de ne plus se comprendre.")
            .at(s.before(2, 22)),
        in("triggers", couple),
        out("caused_by", holidayQuarrel));
    s.add(
        "feeling",
        Body.of()
            .feeling("annoyance", 5)
            .body("Agacement devant les reproches répétés.")
            .at(s.in(22)),
        in("worsens", holidayQuarrel));
    s.add(
        "note",
        Body.of()
            .body(
                "Nous revenons sur la manière de formuler ses besoins sans reproche. Elle prépare une conversation avec Mathieu le week-end prochain, hors des moments de fatigue.")
            .at(s.in(36))
            .tags(List.of("couple")),
        out("part_of", couple));
    s.end(
        "Travail sur la communication dans le couple, mise en situation de la conversation à venir.");

    // ================= Séance 14 : le deuil remonte
    s.start(14);
    LocalDate grandmotherDeath = s.date().minusMonths(7);
    String death =
        s.add(
            "event",
            Body.of()
                .title("Décès de la grand-mère maternelle")
                .body(
                    "Mamie Jeanne est décédée après une courte maladie. Elle n'a pas pu se libérer pour les derniers jours et a assisté aux obsèques sans pleurer.")
                .month(grandmotherDeath)
                .tags(List.of("deuil")));
    String childhood =
        s.add(
            "event",
            Body.of()
                .title("Étés à la campagne chez sa grand-mère")
                .body(
                    "Souvenirs d'enfance heureux : le jardin, les confitures, les soirées sur le perron.")
                .lifePhase("enfance")
                .tags(List.of("deuil")));
    Instant griefFrom = s.in(5);
    String grief =
        s.add(
            "situation",
            Body.of()
                .title("Deuil de la grand-mère maternelle")
                .body(
                    "Le deuil, resté à distance pendant l'épuisement, remonte depuis qu'elle va mieux : tristesse par vagues, envie d'appeler Mamie Jeanne, pensées autour de la maison familiale.")
                .at(griefFrom)
                .tags(List.of("deuil")),
            out("caused_by", death),
            out("co_occurs", childhood));
    s.add(
        "feeling",
        Body.of()
            .feeling("grief", 8)
            .body("Gros chagrin en parlant des derniers jours de Mamie Jeanne.")
            .at(s.in(22))
            .tags(List.of("deuil")),
        in("triggers", grief));
    s.add(
        "feeling",
        Body.of()
            .feeling("guilt", 6)
            .body("Culpabilité de ne pas être venue la voir plus souvent à la fin.")
            .at(s.in(30)),
        in("triggers", grief),
        out("caused_by", death));
    s.add(
        "feeling",
        Body.of()
            .feeling("emptiness", 6)
            .body("Vide le soir, devant le téléphone qui ne sonne plus le dimanche.")
            .at(s.before(2, 21)),
        in("triggers", grief));
    s.add(
        "feeling",
        Body.of()
            .feeling("sadness", 7)
            .body("Pleurs en cachette dans la salle de bains pour que Mathieu n'entende pas.")
            .at(s.before(1, 22))
            .privateEntry(),
        in("triggers", grief));
    s.add(
        "note",
        Body.of()
            .body(
                "Elle n'avait jamais parlé de ce deuil, ni à son compagnon ni à sa sœur. Elle évoque la culpabilité de ne pas avoir été présente. Nous convenons que cette séance est la première pour laisser de la place à cette perte.")
            .at(s.in(42))
            .tags(List.of("deuil")),
        out("part_of", grief));
    s.end(
        "Le deuil de la grand-mère est ouvert. Le rythme des séances est maintenu toutes les deux ou trois semaines.");

    // ================= Séance 15
    s.start(15);
    String photos =
        s.add(
            "event",
            Body.of()
                .title("Tri des photos de famille avec sa mère")
                .body("Un après-midi à regarder les albums de Mamie Jeanne, entre rires et larmes.")
                .day(s.date().minusDays(4))
                .tags(List.of("famille", "deuil")),
            out("part_of", grief));
    s.add(
        "feeling",
        Body.of()
            .feeling("grief", 7)
            .body("Chagrin en tombant sur une photo de la maison à la campagne.")
            .at(s.before(4, 17)),
        in("worsens", photos));
    s.add(
        "feeling",
        Body.of()
            .feeling("serenity", 4)
            .body("Sérénité en racontant un souvenir d'été avec sa grand-mère.")
            .at(s.in(24)),
        in("relieves", s.session));
    s.add(
        "note",
        Body.of()
            .body(
                "Elle a parlé du deuil à sa mère, qui a pleuré aussi. Le lien avec sa sœur s'en trouve renforcé. Nous repérons que le chagrin vient surtout le dimanche, jour de l'appel hebdomadaire.")
            .at(s.in(38))
            .tags(List.of("deuil", "famille")),
        out("part_of", grief));
    s.end(
        "Travail sur le rituel d'adieu : écrire une lettre à Mamie Jeanne pour la prochaine séance.");

    // ================= Séance 16 : couple apaisé, surcharge résolue
    s.start(16);
    Instant coupleEnd = s.date().minusDays(9).atTime(20, 0).toInstant(ZoneOffset.UTC);
    s.close(couple, coupleFrom, coupleEnd);
    Instant workEnd = s.date().minusDays(3).atTime(9, 0).toInstant(ZoneOffset.UTC);
    s.b.patch(
        work,
        Body.of()
            .occurred(workFrom, workEnd, "period", FrDates.periodLabel(workFrom, workEnd))
            .reason(REASON_CLOSED)
            .build());
    String weekend =
        s.add(
            "event",
            Body.of()
                .title("Week-end à deux en Bretagne")
                .body(
                    "Deux jours en bord de mer avec Mathieu, sans téléphone ; ils ont parlé du deuil et de ce qu'elle n'osait pas dire.")
                .period(s.before(8, 9), s.before(6, 18), "week-end en Bretagne")
                .tags(List.of("couple")),
            out("relieves", couple));
    s.add(
        "feeling",
        Body.of()
            .feeling("trust", 6)
            .body("Sentiment d'être soutenue par Mathieu pendant la conversation sur Mamie Jeanne.")
            .at(s.before(6, 20))
            .tags(List.of("couple")),
        in("triggers", weekend));
    s.add(
        "feeling",
        Body.of()
            .feeling("joy", 6)
            .body("Plaisir simple de marcher sur la plage.")
            .at(s.before(7, 15)),
        in("triggers", weekend));
    s.add(
        "note",
        Body.of()
            .body(
                "Les tensions se sont apaisées depuis la discussion sur le partage des tâches : Mathieu fait désormais les courses du samedi. La charge de travail est devenue tenable après le départ en congé de la directrice de projet, remplacée par un renfort.")
            .at(s.in(34))
            .tags(List.of("couple", "travail")),
        out("co_occurs", weekend));
    s.end(
        "Bilan : couple apaisé et charge de travail retrouvée à un niveau tenable. Les situations correspondantes sont closes.");

    // ================= Séance 17 : rapport au travail
    s.start(17);
    Instant projectFrom = s.in(10);
    String project =
        s.add(
            "situation",
            Body.of()
                .title("Repenser son rapport au travail")
                .body(
                    "L'épisode d'épuisement pose la question de ce qu'elle veut vraiment : réduire son temps de travail, se former à l'ergonomie, peut-être changer de métier d'ici deux ans.")
                .at(projectFrom)
                .tags(List.of("travail")),
            out("follows", work));
    s.add(
        "feeling",
        Body.of()
            .feeling("anticipation", 6)
            .body("Enthousiasme en consultant le programme d'une formation en ergonomie.")
            .at(s.in(20)),
        in("triggers", project));
    String advisor =
        s.add(
            "event",
            Body.of()
                .title("Rendez-vous avec une conseillère en évolution professionnelle")
                .body("Bilan de compétences proposé ; première rencontre le mois prochain.")
                .day(s.date().minusDays(6))
                .tags(List.of("travail")),
            out("part_of", project));
    s.add(
        "note",
        Body.of()
            .body(
                "Elle formule pour la première fois ce qu'elle voudrait : un travail où elle compte pour des gens, sans la pression permanente. Nous distinguons ce qui relève du poste de ce qui relève de sa manière de s'engager.")
            .at(s.in(36))
            .tags(List.of("travail")),
        out("part_of", project),
        out("co_occurs", advisor));
    s.end(
        "Ouverture d'une réflexion sur la suite professionnelle. Pas de décision à prendre dans l'immédiat.");

    // ================= Séance 18 : sommeil retrouvé
    s.start(18);
    Instant sleepEnd = s.date().minusDays(10).atTime(7, 0).toInstant(ZoneOffset.UTC);
    s.b.patch(
        sleep,
        Body.of()
            .occurred(sleepFrom, sleepEnd, "period", FrDates.periodLabel(sleepFrom, sleepEnd))
            .reason(REASON_CLOSED)
            .build());
    String fullNight =
        s.add(
            "event",
            Body.of()
                .title("Première nuit complète sans réveil")
                .body("Huit heures d'affilée pour la première fois depuis plus d'un an.")
                .at(s.before(10, 7))
                .tags(List.of("sommeil")),
            out("follows", whiteNight));
    s.add(
        "feeling",
        Body.of()
            .feeling("serenity", 6)
            .body("Matin tranquille, café sur le balcon.")
            .at(s.before(10, 8))
            .tags(List.of("sommeil")),
        in("triggers", fullNight),
        in("relieves", sertraline100));
    s.add(
        "feeling",
        Body.of()
            .feeling("anxiety", 4)
            .body("Légère appréhension le dimanche soir, qui passe en une heure.")
            .at(s.before(3, 20)),
        out("follows", anxiety1));
    s.add(
        "note",
        Body.of()
            .body(
                "Le sommeil est revenu à une durée normale. Nous évoquons avec elle l'idée d'espacer progressivement les séances ; elle veut garder un rythme mensuel pour le deuil.")
            .at(s.in(30))
            .tags(List.of("sommeil")),
        out("part_of", grief));
    s.end("Sommeil rétabli, situation close. Passage à un rythme mensuel.");

    // ================= Séance 19 : anniversaire du décès
    s.start(19);
    String anniversary =
        s.add(
            "event",
            Body.of()
                .title("Anniversaire du décès de Mamie Jeanne")
                .body("Elle a déposé des fleurs avec sa mère et sa sœur, puis déjeuné chez Élise.")
                .day(s.date().minusDays(5))
                .tags(List.of("deuil", "famille")),
            out("part_of", grief),
            out("follows", death));
    s.add(
        "feeling",
        Body.of()
            .feeling("grief", 4)
            .body("Chagrin plus calme, mêlé de tendresse.")
            .at(s.before(5, 15)),
        in("triggers", anniversary),
        in("triggers", grief));
    s.add(
        "feeling",
        Body.of()
            .feeling("pensiveness", 5)
            .body("Beaucoup de pensées sur ce que sa grand-mère lui a transmis.")
            .at(s.in(22)),
        in("triggers", grief));
    s.add(
        "note",
        Body.of()
            .body(
                "Elle a lu à voix haute sa lettre à Mamie Jeanne devant sa mère et sa sœur. Le sentiment de culpabilité a diminué ; il reste de la nostalgie.")
            .at(s.in(35))
            .tags(List.of("deuil")),
        out("part_of", grief));
    s.end(
        "Rituel d'anniversaire réussi. La culpabilité s'est apaisée, le deuil se poursuit sans urgence.");

    // ================= Séance 20 : bilan
    s.start(20);
    s.add(
        "feeling",
        Body.of()
            .feeling("serenity", 6)
            .body("Sentiment d'avoir retrouvé un équilibre.")
            .at(s.in(12)),
        in("relieves", s.session));
    s.add(
        "feeling",
        Body.of()
            .feeling("anticipation", 6)
            .body("Impatience de commencer le bilan de compétences.")
            .at(s.in(18)),
        in("triggers", project));
    s.add(
        "note",
        Body.of()
            .body(
                "Bilan de dix-huit mois : l'épuisement est derrière elle, le sommeil et le couple sont stabilisés. Deux chantiers restent actifs : le deuil de sa grand-mère, qui se poursuit à son rythme, et la réflexion sur son travail. Sertraline maintenue à 100 mg par le médecin traitant.")
            .at(s.in(40))
            .tags(List.of("travail", "deuil")),
        out("co_occurs", project),
        out("co_occurs", grief));
    s.add(
        "note",
        Body.of()
            .body(
                "Mémo personnel : penser à lui proposer de reparler de ses limites au travail si une nouvelle charge arrive. Pas de signe de rechute.")
            .at(s.in(48))
            .privateEntry());
    s.end(
        "Bilan des dix-huit mois. Prochaine séance dans un mois, centrée sur le deuil et le projet professionnel.");

    return new PatientPlan(
        Scenario.DEMO_PREFIX + "Camille R.",
        "Camille",
        "Roussel",
        LocalDate.of(1989, 6, 12).toString(),
        "elle",
        List.of("couple", "deuil", "famille", "sommeil", "travail"),
        Practitioner.CLAIRE,
        s.b.steps());
  }
}
