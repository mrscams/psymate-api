package com.psymate.seed;

import java.util.List;

/**
 * Corpus français du générateur : noms fictifs, thèmes de vie, phrases de séance. Tout est inventé
 * et neutre quant au genre (pas d'accord à faire) ; quelques mots rares (licenciement,
 * ophtalmologue, déménagement) servent aux scénarios de recherche.
 */
final class Corpus {

  private Corpus() {}

  /** Mot fréquent du corpus (présent dans de très nombreuses entrées) pour la recherche. */
  static final String FREQUENT_WORD = "travail";

  /** Mots rares du corpus, présents dans quelques entrées seulement. */
  static final List<String> RARE_WORDS = List.of("licenciement", "ophtalmologue", "déménagement");

  /** Prénom fictif et pronom usuel. */
  record FirstName(String name, String pronoun) {}

  static final FirstName[] FIRST_NAMES = {
    new FirstName("Léa", "elle"),
    new FirstName("Hugo", "il"),
    new FirstName("Inès", "elle"),
    new FirstName("Maxime", "il"),
    new FirstName("Camille", "iel"),
    new FirstName("Noé", "il"),
    new FirstName("Manon", "elle"),
    new FirstName("Théo", "il"),
    new FirstName("Sarah", "elle"),
    new FirstName("Antoine", "il"),
    new FirstName("Julie", "elle"),
    new FirstName("Nicolas", "il"),
    new FirstName("Clara", "elle"),
    new FirstName("Romain", "il"),
    new FirstName("Émilie", "elle"),
    new FirstName("Baptiste", "il"),
    new FirstName("Zoé", "elle"),
    new FirstName("Lucas", "il"),
    new FirstName("Anaïs", "elle"),
    new FirstName("Mathieu", "il"),
    new FirstName("Louise", "elle"),
    new FirstName("Adrien", "il"),
    new FirstName("Chloé", "elle"),
    new FirstName("Kévin", "il"),
    new FirstName("Margaux", "elle"),
    new FirstName("Samuel", "il"),
    new FirstName("Océane", "elle"),
    new FirstName("Florian", "il"),
    new FirstName("Pauline", "elle"),
    new FirstName("Thomas", "il"),
    new FirstName("Alix", "iel"),
    new FirstName("Yanis", "il"),
    new FirstName("Elsa", "elle"),
    new FirstName("Victor", "il"),
    new FirstName("Marion", "elle"),
    new FirstName("Damien", "il"),
    new FirstName("Charlotte", "elle"),
    new FirstName("Sébastien", "il"),
    new FirstName("Justine", "elle"),
    new FirstName("Pierre", "il"),
    new FirstName("Lola", "elle"),
    new FirstName("Mehdi", "il"),
    new FirstName("Nina", "elle"),
    new FirstName("Gabriel", "il"),
    new FirstName("Salomé", "elle"),
    new FirstName("Alexis", "iel"),
    new FirstName("Mélanie", "elle"),
    new FirstName("Quentin", "il"),
    new FirstName("Agathe", "elle"),
    new FirstName("Benoît", "il"),
    new FirstName("Fanny", "elle"),
    new FirstName("Étienne", "il"),
    new FirstName("Lucie", "elle"),
    new FirstName("Julien", "il"),
    new FirstName("Mathilde", "elle"),
    new FirstName("Rayan", "il"),
    new FirstName("Apolline", "elle"),
    new FirstName("Valentin", "il"),
    new FirstName("Naïma", "elle"),
    new FirstName("Loïc", "il"),
    new FirstName("Eva", "elle"),
    new FirstName("Simon", "il"),
    new FirstName("Jade", "elle"),
    new FirstName("Arthur", "il"),
    new FirstName("Maëlle", "elle"),
    new FirstName("Cyril", "il"),
    new FirstName("Solène", "elle"),
    new FirstName("Karine", "elle"),
    new FirstName("Tristan", "il"),
    new FirstName("Dorothée", "elle"),
    new FirstName("Anthony", "il"),
    new FirstName("Morgane", "elle"),
    new FirstName("Yann", "il"),
    new FirstName("Sasha", "iel")
  };

  /** Noms de famille inventés (aucun lien voulu avec des personnes réelles). */
  static final String[] LAST_NAMES = {
    "Marchetti", "Delorme", "Vasseur", "Lefrançois", "Aubertin", "Bellamy", "Cordier", "Duhamel",
    "Estève", "Fontanel", "Gaudin", "Hérisson", "Imbert", "Jolivet", "Kerbrat", "Lamarque",
    "Mortier", "Noguès", "Olivier-Rey", "Pellerin", "Quesnel", "Rivoire", "Soulier", "Tessier",
    "Urbain", "Vernier", "Wasson", "Yvonnet", "Zimmer", "Archambaud", "Berthelot", "Chauvet",
    "Dumoulin", "Escoffier", "Fabregue", "Grandin", "Hamelin", "Ibanez", "Jourdain", "Klein",
    "Lacombe", "Maillard", "Navarro", "Oudin", "Peyrot", "Raynaud", "Sauvage", "Taillandier",
    "Ulrich", "Valette", "Weber", "Xaintrailles", "Yildiz", "Zanetti", "Allard", "Bouvier",
    "Castel", "Deschamps", "Ferrand", "Girard"
  };

  // ---- thèmes de vie

  /**
   * Un thème : situation longue (titre, corps), événements ponctuels et émotions associées.
   *
   * @param tag libellé du tag du patient (jamais un nom d'émotion : l'API le refuserait)
   * @param negative vrai si les situations de ce thème pèsent sur les ressentis
   */
  record Theme(
      String tag,
      boolean negative,
      String[] emotions,
      String[] situationTitles,
      String[] situationBodies,
      String[] eventTitles,
      String[] eventBodies) {}

  static final Theme[] THEMES = {
    new Theme(
        "travail",
        true,
        new String[] {"anxiety", "annoyance", "shame", "apprehension", "sadness", "anger"},
        new String[] {
          "Surcharge de travail",
          "Conflit avec la hiérarchie",
          "Menace de licenciement économique",
          "Changement de poste imposé",
          "Reprise du travail après un arrêt",
          "Tensions dans l'équipe"
        },
        new String[] {
          "Journées trop longues, dossiers qui s'accumulent et difficulté à déconnecter le soir.",
          "Relation tendue avec le responsable, sentiment de ne pas être entendu en réunion.",
          "Peur de ne pas tenir le rythme, rumination sur le travail jusque dans la nuit.",
          "Le contexte dans l'entreprise est incertain et nourrit une inquiétude diffuse."
        },
        new String[] {
          "Réunion tendue avec le responsable",
          "Évaluation annuelle décevante",
          "Annonce d'un plan de licenciement dans l'entreprise",
          "Entretien avec le médecin du travail",
          "Dossier rendu en retard",
          "Retour de congés difficile",
          "Entretien d'embauche dans une autre structure"
        },
        new String[] {
          "L'échange a duré une heure et laissé une sensation d'injustice.",
          "Une semaine chargée, avec deux soirées passées au bureau.",
          "Le résultat est en deçà des attentes, mais la discussion a été honnête."
        }),
    new Theme(
        "couple",
        true,
        new String[] {"sadness", "anger", "guilt", "apprehension", "emptiness", "annoyance"},
        new String[] {
          "Tensions dans le couple",
          "Éloignement progressif avec le conjoint",
          "Séparation envisagée",
          "Projet d'enfant qui divise"
        },
        new String[] {
          "Les disputes reviennent sur les mêmes sujets : tâches ménagères, temps passé ensemble.",
          "Sentiment de vivre côte à côte, avec peu de moments partagés.",
          "Hésitation entre faire un effort et partir, avec beaucoup de culpabilité."
        },
        new String[] {
          "Dispute au sujet des tâches ménagères",
          "Soirée en tête-à-tête réussie",
          "Discussion sur le projet d'enfant",
          "Départ chez un ami pour quelques jours",
          "Week-end en amoureux annulé",
          "Consultation de couple envisagée"
        },
        new String[] {
          "La discussion est montée dans les tours puis s'est apaisée au bout d'une heure.",
          "Un moment agréable, le premier depuis longtemps.",
          "Chacun a pu dire ce qui le blessait, sans élever la voix."
        }),
    new Theme(
        "famille",
        true,
        new String[] {"annoyance", "sadness", "guilt", "anger", "apprehension"},
        new String[] {
          "Relation difficile avec la mère",
          "Conflit de fratrie autour d'un héritage",
          "Pression de la famille sur les choix de vie",
          "Parent âgé dépendant"
        },
        new String[] {
          "Appels fréquents et reproches répétés, avec un fort sentiment de devoir.",
          "La fratrie est divisée et chaque conversation tourne à la dispute.",
          "Difficulté à poser des limites sans se sentir coupable."
        },
        new String[] {
          "Repas de famille houleux",
          "Appel de la sœur à propos de la succession",
          "Visite chez les parents",
          "Anniversaire de la mère",
          "Réunion de famille chez l'oncle"
        },
        new String[] {
          "Des remarques blessantes, et peu de soutien des autres.",
          "Moment plus doux que prévu, avec un vrai échange.",
          "Retour à la maison épuisé, besoin de silence."
        }),
    new Theme(
        "deuil",
        true,
        new String[] {"grief", "sadness", "emptiness", "guilt", "pensiveness"},
        new String[] {
          "Deuil du père",
          "Deuil d'un grand-parent",
          "Perte d'un ami proche",
          "Deuil de l'animal de compagnie"
        },
        new String[] {
          "La perte est encore très présente, avec des vagues de tristesse imprévisibles.",
          "Difficulté à parler de la personne disparue sans que les larmes montent.",
          "Sensation de vide aux dates qui comptaient, et culpabilité de ne pas avoir été plus présent."
        },
        new String[] {
          "Obsèques",
          "Décès du père",
          "Tri des affaires de la maison familiale",
          "Visite au cimetière",
          "Anniversaire du décès",
          "Ouverture de la succession chez le notaire"
        },
        new String[] {
          "Moment éprouvant mais entouré par la famille.",
          "Beaucoup de souvenirs remontés en rangeant les photos.",
          "Une journée silencieuse, passée à regarder de vieux albums."
        }),
    new Theme(
        "santé",
        true,
        new String[] {"apprehension", "anxiety", "fear", "pensiveness", "vigilance"},
        new String[] {
          "Douleurs chroniques au dos",
          "Examens médicaux en cours",
          "Troubles de la vue : suivi ophtalmologique",
          "Fatigue persistante sans cause identifiée"
        },
        new String[] {
          "Les douleurs réveillent la nuit et limitent les sorties.",
          "L'attente des résultats alimente des scénarios catastrophes.",
          "Inquiétude à propos d'une baisse de la vue, avec rendez-vous multiples."
        },
        new String[] {
          "Rendez-vous chez l'ophtalmologue",
          "Résultats d'analyses sanguines",
          "Consultation chez le médecin traitant",
          "Scanner de contrôle",
          "Séance de kinésithérapie",
          "Consultation chez le rhumatologue"
        },
        new String[] {
          "Le praticien se veut rassurant mais demande des examens complémentaires.",
          "Une bonne nouvelle : rien d'inquiétant pour l'instant.",
          "Le rendez-vous a été reporté de trois semaines."
        }),
    new Theme(
        "études",
        true,
        new String[] {"anxiety", "shame", "fear", "apprehension", "vigilance"},
        new String[] {
          "Préparation d'un concours",
          "Mémoire de master en retard",
          "Reprise d'études après des années",
          "Redoublement possible"
        },
        new String[] {
          "Révisions irrégulières et peur de l'échec qui paralyse le démarrage.",
          "Le mémoire n'avance pas, avec une sensation d'être dépassé.",
          "Retrouver le rythme des cours est plus difficile que prévu."
        },
        new String[] {
          "Épreuve écrite du concours",
          "Rendu du mémoire",
          "Résultats d'examens",
          "Rendez-vous avec le directeur de recherche",
          "Oral de rattrapage"
        },
        new String[] {
          "Une épreuve plus longue que prévu, sortie vidée.",
          "Les résultats sont corrects, avec un vrai soulagement.",
          "Le retour du jury a été constructif."
        }),
    new Theme(
        "argent",
        true,
        new String[] {"apprehension", "anxiety", "shame", "anger", "fear"},
        new String[] {
          "Difficultés financières",
          "Dette à rembourser",
          "Impayés de loyer",
          "Perte de revenus après un arrêt"
        },
        new String[] {
          "Les fins de mois sont tendues et le sujet est caché à l'entourage.",
          "Anxiété à l'ouverture du courrier et évitement des comptes.",
          "Honte de demander de l'aide, alors que la situation s'aggrave."
        },
        new String[] {
          "Courrier de la banque",
          "Découvert bancaire dépassé",
          "Rendez-vous avec le conseiller bancaire",
          "Demande d'aide auprès de l'assistante sociale",
          "Échéancier accepté par le créancier"
        },
        new String[] {
          "Un courrier recommandé qui a ravivé l'inquiétude.",
          "Une démarche menée jusqu'au bout, malgré l'appréhension.",
          "Le plan de remboursement soulage un peu."
        }),
    new Theme(
        "sommeil",
        true,
        new String[] {"anxiety", "emptiness", "annoyance", "boredom", "apprehension"},
        new String[] {
          "Insomnie d'endormissement",
          "Réveils nocturnes répétés",
          "Sommeil fragmenté depuis plusieurs semaines",
          "Cauchemars récurrents"
        },
        new String[] {
          "Plus d'une heure pour s'endormir, avec des pensées qui tournent en boucle.",
          "Réveil vers quatre heures du matin presque chaque nuit.",
          "La fatigue de la journée rend les émotions plus difficiles à contenir."
        },
        new String[] {
          "Nuit blanche avant une échéance",
          "Consultation au centre du sommeil",
          "Début d'une routine de coucher régulière",
          "Première nuit complète depuis longtemps",
          "Nuit agitée après une dispute"
        },
        new String[] {
          "Écrans coupés une heure plus tôt : un peu mieux.",
          "Lever à six heures avec une grande fatigue.",
          "La respiration abdominale aide à s'endormir."
        }),
    new Theme(
        "logement",
        true,
        new String[] {"apprehension", "anticipation", "sadness", "annoyance", "interest"},
        new String[] {
          "Déménagement en cours",
          "Recherche d'un logement",
          "Travaux dans l'appartement",
          "Conflit avec le voisinage"
        },
        new String[] {
          "Cartons partout, fatigue de l'organisation et appréhension du nouveau quartier.",
          "Les visites s'enchaînent sans réponse favorable des propriétaires.",
          "Le bruit des travaux empêche de se poser."
        },
        new String[] {
          "Visite d'un appartement",
          "Signature du bail",
          "Déménagement",
          "Premier soir dans le nouveau logement",
          "Réunion avec le syndic"
        },
        new String[] {
          "Le logement est petit mais lumineux.",
          "Une journée de cartons et de fatigue, aidée par des amis.",
          "Le dossier a enfin été accepté."
        }),
    new Theme(
        "amitiés",
        false,
        new String[] {"trust", "joy", "interest", "serenity", "acceptance"},
        new String[] {
          "Reprise des liens amicaux",
          "Nouvelle activité de groupe",
          "Réseau de soutien qui se reconstruit"
        },
        new String[] {
          "Retour progressif vers des sorties, avec du plaisir à retrouver les autres.",
          "Participation régulière à une chorale, qui structure la semaine.",
          "Trois personnes sur qui compter, et la sensation d'en parler plus librement."
        },
        new String[] {
          "Sortie au cinéma avec des amis",
          "Week-end chez une amie",
          "Inscription au club de course à pied",
          "Répétition de la chorale",
          "Dîner entre voisins"
        },
        new String[] {
          "Une soirée simple et chaleureuse.",
          "Surpris du plaisir ressenti, malgré l'hésitation avant d'y aller.",
          "Un échange sincère qui a fait du bien."
        }),
    new Theme(
        "projets",
        false,
        new String[] {"anticipation", "interest", "joy", "serenity", "trust"},
        new String[] {
          "Projet de reconversion professionnelle",
          "Projet de voyage",
          "Reprise d'une activité manuelle"
        },
        new String[] {
          "Envie de changer de voie, avec des démarches concrètes qui commencent.",
          "Préparation d'un voyage, source d'enthousiasme et de planification.",
          "La poterie du jeudi soir offre un moment à soi."
        },
        new String[] {
          "Inscription à une formation",
          "Premier cours de poterie",
          "Rendez-vous avec un conseiller en évolution professionnelle",
          "Réservation des billets",
          "Visite d'un atelier"
        },
        new String[] {
          "Un premier pas concret, vécu avec fierté.",
          "L'idée prend forme, avec un peu de vertige.",
          "Moment de concentration agréable, sans pensées parasites."
        })
  };

  /** Événements liés à l'enfance (précision « phase de vie »). */
  static final String[] CHILDHOOD_EVENTS = {
    "Déménagements fréquents pendant l'enfance",
    "Séparation des parents pendant l'enfance",
    "Changement d'école en cours d'année",
    "Hospitalisation pendant l'enfance",
    "Années de pensionnat"
  };

  // ---- phrases de séance

  /** Notes de séance : une à trois phrases tirées de ce corpus (voix neutre). */
  static final String[] NOTES = {
    "Séance centrée sur la fatigue de fin de semaine, avec des journées longues et peu de pauses.",
    "Semaine plus calme, la marche du matin a été reprise trois fois.",
    "Difficulté à s'endormir avant minuit, les pensées tournent autour du travail.",
    "Évoque une discussion franche avec un proche, qui a soulagé sans tout résoudre.",
    "Les ruminations du soir reviennent, malgré l'exercice de respiration proposé.",
    "Premier rendez-vous chez l'ophtalmologue, sans mauvaise nouvelle pour l'instant.",
    "Parle du déménagement à venir : liste de tâches, appréhension du changement de quartier.",
    "Se dit soulagé après la réunion, tout en gardant une méfiance vis-à-vis de la direction.",
    "A repris contact avec une amie perdue de vue, et en ressort plus léger.",
    "Colère contenue pendant la semaine, difficile à exprimer sans se sentir injuste.",
    "Revient sur l'enfance et la place du silence à la maison.",
    "Humeur plus basse depuis le week-end, sans cause précise identifiée.",
    "L'exercice d'écriture du soir est tenu, avec trois lignes par jour environ.",
    "Peur du jugement des collègues, qui limite les prises de parole en réunion.",
    "Évoque l'annonce d'un licenciement collectif et la peur de faire partie de la liste.",
    "Souhaite reprendre une activité physique douce, sans objectif de performance.",
    "Rapporte un moment de joie inattendu pendant une promenade.",
    "Parle de la culpabilité ressentie vis-à-vis de la famille, à propos d'un refus.",
    "Semaine marquée par plusieurs nuits courtes et une irritabilité accrue.",
    "Nomme plus facilement ses émotions que lors des premières séances.",
    "Raconte une dispute et la manière dont elle a été désamorcée au bout d'une heure.",
    "Difficulté à demander de l'aide, par peur de déranger.",
    "Un rêve récurrent est évoqué, associé à un sentiment d'être poursuivi.",
    "Fait le point sur les objectifs : trois sont tenus, deux restent flous.",
    "Évite certaines conversations à la maison pour ne pas créer de tension.",
    "Bonne semaine globalement, avec un pic d'angoisse mardi soir.",
    "Parle d'un souvenir précis, resté vif, qui revient à l'approche de l'automne.",
    "Questionne le sens du travail actuel et l'envie de changer de voie.",
    "Se sent mieux compris depuis la discussion avec le médecin traitant.",
    "L'attente des résultats d'examens occupe beaucoup de place dans les pensées.",
    "Essaie de limiter les écrans le soir, avec un effet modeste sur le sommeil.",
    "Évoque la solitude du dimanche après-midi, moment le plus difficile.",
    "Réussit à poser une limite à un proche, et en est étonné.",
    "Pense moins à l'échec depuis qu'un plan de révision est écrit.",
    "Décrit une sensation de poids dans la poitrine à l'approche des échéances.",
    "Parle d'un conflit avec un voisin, qui prend beaucoup d'énergie.",
    "Vient avec une liste de sujets et en aborde trois dans la séance.",
    "Rappelle l'importance du rythme des repas pour tenir la journée.",
    "Revient sur le décès d'un proche et sur ce qui n'a pas été dit.",
    "Se réjouit de petites victoires de la semaine, comme avoir répondu au courrier en attente.",
    "Évoque une envie de voyager pour couper avec le quotidien.",
    "Doute de sa capacité à tenir sur la durée, tout en poursuivant les exercices.",
    "Les relations avec la fratrie reviennent, notamment autour de l'héritage.",
    "Parle de honte à propos d'une situation d'argent et de la difficulté à en parler.",
    "Se sent plus stable depuis la mise en place d'un rituel du matin.",
    "La séance porte sur l'estime de soi et la comparaison permanente avec les autres.",
    "Remarque que les crises d'angoisse sont moins longues, sans être moins intenses.",
    "Raconte la visite d'un logement et la crainte de ne pas être accepté par le propriétaire."
  };

  /** Phrases courtes pour le compte rendu de clôture. */
  static final String[] SUMMARIES = {
    "Séance de suivi, ambiance détendue.",
    "Travail sur les pensées automatiques, un exercice est donné pour la semaine.",
    "Point d'étape sur les objectifs, ajustement du rythme des séances.",
    "Séance difficile, avec un temps d'émotion important en fin d'heure.",
    "Bonne alliance de travail, le patient se montre actif.",
    "Prochaine séance prévue dans deux semaines.",
    "Relecture du carnet de la semaine et repérage des moments clés.",
    "Exercice de respiration pratiqué en séance.",
    "Discussion autour des situations déclenchantes de la semaine.",
    "Séance plus courte que prévu, retard au début.",
    "Évolution favorable sur le sommeil, à surveiller.",
    "Plan de sécurité revu, aucun signe d'urgence.",
    "Séance centrée sur les relations proches.",
    "Retour sur les événements marquants depuis la dernière séance.",
    "Un point de vigilance est noté pour la prochaine rencontre."
  };

  /** Contexte d'un ressenti (corps facultatif) pour une émotion pesante. */
  static final String[] FEELING_NEGATIVE = {
    "Montée d'angoisse en fin d'après-midi, serrement dans la poitrine.",
    "Pensées qui tournent en boucle depuis le matin.",
    "Sensation de poids, difficulté à se concentrer.",
    "Irritabilité avec les proches, regret une fois la tension retombée.",
    "Envie de s'isoler, sans énergie pour répondre aux messages.",
    "Gorge serrée en repensant à la discussion de la veille.",
    "Réveil avec une boule au ventre.",
    "Impression de ne pas être à la hauteur.",
    "Tension dans les épaules toute la soirée.",
    "Larmes à la fin du repas, sans savoir pourquoi sur le moment.",
    "Peur que la situation ne s'améliore pas.",
    "Colère rentrée, qui ressort sur des détails."
  };

  /** Contexte d'un ressenti plus apaisé. */
  static final String[] FEELING_POSITIVE = {
    "Moment de calme en fin de journée, avec une respiration plus ample.",
    "Plaisir simple à marcher dehors ce matin.",
    "Sentiment d'être soutenu après un échange avec une amie.",
    "Satisfaction d'avoir tenu l'objectif de la semaine.",
    "Curiosité retrouvée pour une activité abandonnée.",
    "Légèreté après avoir réglé une démarche en attente.",
    "Sérénité pendant un repas partagé.",
    "Un rire franc, le premier depuis plusieurs jours."
  };

  /** Émotions pour un ressenti sans situation active. */
  static final String[] CALM_EMOTIONS = {"calm", "serenity", "joy", "interest"};

  // ---- médicaments

  /**
   * Un traitement : suite de dosages (changements de posologie), fréquence, prescripteur.
   *
   * @param adjunct vrai pour un traitement ponctuel (hydroxyzine, alprazolam) ou d'appoint
   */
  record Course(
      String molecule, String[] dosages, String frequency, String prescriber, boolean adjunct) {}

  static final Course[] COURSES = {
    new Course(
        "sertraline",
        new String[] {"25 mg", "50 mg", "100 mg"},
        "1 comprimé le matin",
        "Dr Haddad",
        false),
    new Course(
        "escitalopram",
        new String[] {"5 mg", "10 mg", "20 mg"},
        "1 comprimé le matin",
        "Dr Haddad",
        false),
    new Course(
        "venlafaxine",
        new String[] {"37,5 mg", "75 mg", "150 mg"},
        "1 gélule LP le matin",
        "Dr Haddad",
        false),
    new Course(
        "quétiapine", new String[] {"25 mg", "50 mg"}, "1 comprimé le soir", "Dr Haddad", false),
    new Course(
        "lamotrigine",
        new String[] {"25 mg", "50 mg", "100 mg"},
        "1 comprimé par jour",
        "Dr Haddad",
        false),
    new Course(
        "alprazolam",
        new String[] {"0,25 mg"},
        "si besoin, 1 comprimé maximum par jour",
        "Dr Haddad",
        true),
    new Course(
        "hydroxyzine", new String[] {"25 mg"}, "1 comprimé le soir si besoin", "Dr Lambert", true),
    new Course("mélatonine", new String[] {"2 mg"}, "1 comprimé au coucher", "Dr Lambert", true)
  };
}
