package com.psymate.seed;

import com.psymate.seed.Plan.LinkPlan;
import com.psymate.seed.Plan.PatientPlan;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dossier de démonstration en arabe (M16-06, G Q96 à Q102) : « SEED · Youssef A. », suivi par la
 * praticienne arabophone {@link Accounts#ARABIC} (préférence {@code locale = ar}). Trois séances
 * passées, closes avec un compte rendu en arabe ; huit entrées dont le titre et le texte sont en
 * arabe standard moderne (deux situations, un événement, trois ressentis, deux notes), plus une
 * note qui mêle arabe et français dans une même phrase ; sept liens conformes à E5.3.
 *
 * <p>Le texte parle de la personne suivie à la troisième personne, au masculin grammatical de «
 * المريض » (le patient, forme générique du glossaire, règle 4), qui est aussi celui de Youssef ;
 * les tournures nominales sont préférées quand elles existent. Il emploie volontairement des formes
 * que la recherche mot à mot distingue (article collé, ta marbuta, alif avec ou sans hamza,
 * singulier et pluriel : « النوم » et « نوم », « الاجتماع » et « الاجتماعات », « صعوبة » et «
 * صعوبات ») : M16-32 mesurera dessus ce que la recherche actuelle trouve et manque.
 *
 * <p>Générateur pur, sans réseau ni horloge cachée : les dates sont relatives au jour passé en
 * paramètre, toutes les séances sont passées. Le patient n'est rattaché à aucune valeur de {@link
 * Plan.Practitioner} ({@code practitioner = null}) : l'agenda, la prise de rendez-vous et les tests
 * de performance parcourent cette énumération, et la praticienne arabophone n'en a pas besoin.
 *
 * <p>Tout est fictif : aucun lien avec une personne réelle.
 */
final class ArabicStory {

  private ArabicStory() {}

  /** Nom d'affichage du patient : identifiant stable pour les e2e arabes du web. */
  static final String DISPLAY_NAME = Scenario.SEED_PREFIX + "Youssef A.";

  /** Préférence {@code locale} de la praticienne. */
  static final String LOCALE = "ar";

  /** Titre de l'entrée qui mêle arabe et français (une note). */
  static final String MIXED_TITLE = "Vocabulaire du patient : « تعب في الرأس »";

  // jours avant aujourd'hui, heure UTC, durée, modalité des trois séances
  private static final int[] DAYS_AGO = {42, 28, 14};
  private static final int[] HOUR = {9, 16, 9};
  private static final int[] DURATION = {50, 45, 50};
  private static final String[] MODALITY = {"in_person", "video", "in_person"};

  private static final class Story {
    final PatientBuilder b = new PatientBuilder();
    final LocalDate today;
    String session;
    Instant ts;
    int minutes;

    Story(Instant today) {
      this.today = today.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /** Démarre la séance numéro {@code n} (1 à 3), reportée au lundi si elle tombe un week-end. */
    void start(int n) {
      LocalDate day = today.minusDays(DAYS_AGO[n - 1]);
      if (day.getDayOfWeek() == DayOfWeek.SATURDAY) {
        day = day.plusDays(2);
      } else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
        day = day.plusDays(1);
      }
      ts = day.atTime(HOUR[n - 1], 0).toInstant(ZoneOffset.UTC);
      minutes = DURATION[n - 1];
      session = b.startSession(ts, MODALITY[n - 1]);
    }

    void end(String summary) {
      b.closeSession(session, minutes, summary);
    }

    Instant in(int min) {
      return ts.plus(Math.min(min, minutes - 1), ChronoUnit.MINUTES);
    }

    LocalDate date() {
      return ts.atZone(ZoneOffset.UTC).toLocalDate();
    }

    String add(String kind, Body body, LinkPlan... links) {
      return b.entry(session, kind, body.build(), List.of(links));
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

    // ================= Séance 1 : سبب الاستشارة
    s.start(1);
    String work =
        s.add(
            "situation",
            Body.of()
                .title("ضغط متواصل في العمل")
                .body(
                    "يعمل المريض محاسبًا في شركة نقل منذ ثماني سنوات. ازداد حجم العمل منذ إعادة"
                        + " تنظيم الفريق قبل ثلاثة أشهر، مع ساعات إضافية شبه يومية ورسائل إلكترونية"
                        + " في المساء.")
                .month(s.date().minusMonths(3)));
    String sleep =
        s.add(
            "situation",
            Body.of()
                .title("صعوبات في النوم")
                .body(
                    "استيقاظ متكرر في الثالثة فجرًا وصعوبة في العودة إلى النوم منذ شهرين تقريبًا،"
                        + " بمعدل خمس ساعات من النوم في الليلة.")
                .month(s.date().minusMonths(2)),
            out("co_occurs", work));
    String anxiety =
        s.add(
            "feeling",
            Body.of()
                .title("قلق مساء الأحد")
                .feeling("anxiety", 7)
                .body("قلق شديد مساء الأحد عند التفكير في أسبوع العمل، مع شدّ في المعدة.")
                .at(s.in(15)),
            in("triggers", work));
    s.add(
        "note",
        Body.of()
            .title("سبب الاستشارة")
            .body(
                "سبب الاستشارة: إرهاق مرتبط بالعمل وصعوبات في النوم. لا أفكار انتحارية ولا علاج"
                    + " دوائي حالي، وهذه أول متابعة نفسية. الطلب واضح: استعادة الطاقة والقدرة على"
                    + " الفصل بين العمل والحياة الخاصة.")
            .at(s.in(40)),
        out("co_occurs", anxiety));
    s.end(
        "جلسة أولى: جمع سبب الاستشارة والسياق المهني. الاتفاق على جلسة كل أسبوعين، واقتراح مفكرة"
            + " للنوم حتى الجلسة القادمة.");

    // ================= Séance 2 : اجتماع متوتر
    s.start(2);
    String meeting =
        s.add(
            "event",
            Body.of()
                .title("اجتماع متوتر مع المدير")
                .body(
                    "انتقد المدير التأخير في التقرير الشهري أمام الفريق، ولم يتمكّن المريض من الرد.")
                .day(s.date().minusDays(3)),
            out("part_of", work));
    s.add(
        "feeling",
        Body.of()
            .title("خزي بعد الاجتماع")
            .feeling("shame", 6)
            .body("شعور بالخزي بعد الاجتماع، مع أفكار متكرّرة حول ما كان يمكن قوله.")
            .at(s.in(10)),
        in("worsens", meeting));
    s.add(
        "note",
        Body.of()
            .title(MIXED_TITLE)
            .body(
                "Le patient parle de « تعب في الرأس » pour décrire sa fatigue du soir, et il ajoute"
                    + " que le dimanche « القلق يبدأ من الظهر » bien avant la reprise du travail.")
            .at(s.in(30)));
    s.end(
        "مراجعة مفكرة النوم: نوم متقطع في أيام العمل. تمرين على التعرّف على الأفكار التلقائية بعد"
            + " الاجتماعات.");

    // ================= Séance 3 : تقدّم تدريجي
    s.start(3);
    s.add(
        "feeling",
        Body.of()
            .title("هدوء نسبي")
            .feeling("calm", 4)
            .body(
                "هدوء نسبي في نهاية الأسبوع بعد تحديد موعد ثابت للنوم وإيقاف الرسائل بعد الساعة"
                    + " الثامنة مساءً.")
            .at(s.in(10)),
        out("follows", anxiety));
    s.add(
        "note",
        Body.of()
            .title("تقدّم تدريجي")
            .body(
                "تحسّن تدريجي في النوم بعد تطبيق روتين مسائي. يبقى القلق مرتفعًا قبل الاجتماعات."
                    + " هدف الجلسة القادمة: التحضير لحديث مع المدير حول توزيع المهام.")
            .at(s.in(40)),
        out("part_of", sleep));
    s.end(
        "جلسة ثالثة: تقدّم في النوم وتراجع طفيف في القلق. الاتفاق على تمرين لتحضير الحديث مع"
            + " المدير.");

    return new PatientPlan(
        DISPLAY_NAME,
        "Youssef",
        "Amrani",
        LocalDate.of(1988, 4, 12).toString(),
        "il",
        List.of(),
        null,
        s.b.steps(),
        List.of());
  }
}
