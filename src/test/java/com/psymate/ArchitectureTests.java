package com.psymate;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.psymate.shared.problem.AdviceOrder;
import com.psymate.shared.problem.GlobalExceptionHandler;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ArchitectureTests {

  private static final String ROOT = "com.psymate";
  private static final String FIXTURE_ROOT = "psymatefixture.badadmin";
  private static final String[] CLINICAL_MODULES = {
    "patients", "clinical", "linking", "overview", "search"
  };

  private static final String JPA_FIXTURE_ROOT = "psymatefixture.badjpa";

  private static final String ENTITY = "jakarta.persistence.Entity";
  private static final String TABLE = "jakarta.persistence.Table";
  private static final String COLUMN = "jakarta.persistence.Column";
  private static final String TRANSIENT = "jakarta.persistence.Transient";
  private static final String IMMUTABLE = "org.hibernate.annotations.Immutable";
  private static final String DYNAMIC_UPDATE = "org.hibernate.annotations.DynamicUpdate";
  private static final List<String> JPA_ASSOCIATIONS =
      List.of(
          "jakarta.persistence.ManyToOne",
          "jakarta.persistence.OneToMany",
          "jakarta.persistence.OneToOne",
          "jakarta.persistence.ManyToMany");

  /**
   * Tables sans UPDATE ni DELETE pour psymate_app (REVOKE UPDATE, DELETE) : security_event et
   * patient_version (V01 l. 304), verification_decision et document_access_log (V03 l. 74).
   * L'entite qui les mappe doit etre {@code @Immutable}. Liste figee ici, pas de lecture SQL au
   * runtime : une nouvelle table append-only s'ajoute ici dans la meme PR que sa migration.
   */
  static final Set<String> APPEND_ONLY_TABLES =
      Set.of("security_event", "patient_version", "verification_decision", "document_access_log");

  /**
   * entry_version (V05 l. 132 : REVOKE UPDATE, DELETE ; l. 139 : GRANT UPDATE (title, body,
   * payload), G Q31 / E7 O11). Hibernate emet par defaut un UPDATE de toutes les colonnes, refuse a
   * psymate_app : l'entite doit etre {@code @Immutable} ou {@code @DynamicUpdate}.
   */
  static final String ENTRY_VERSION_TABLE = "entry_version";

  /** Colonne ecrite par trigger (V04, V08) et colonne generee (V05) : jamais ecrites par JPA. */
  static final Set<String> DB_MANAGED_COLUMNS = Set.of("search_vector", "occurred_range");

  private static final JavaClasses PRODUCTION = new ClassFileImporter().importPackages(ROOT);

  /**
   * Classes de src/main seulement : les tests suivent leurs classes et n'ont pas les memes regles.
   */
  static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ROOT);

  /** Module name = first package segment after the root package. */
  private static String moduleOf(JavaClass c, String root) {
    String pkg = c.getPackageName();
    if (!pkg.startsWith(root + ".")) {
      return null;
    }
    String rest = pkg.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }

  // allowEmptyShould(true): with zero production classes the rules are vacuously true; each rule
  // is proven non-vacuous by the fixture tests below.
  static ArchRule noRepositoryAccessAcrossModules(String root) {
    return classes()
        .should(
            new ArchCondition<JavaClass>("access a repository of another module") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(origin, root);
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  String to = moduleOf(target, root);
                  if (from != null
                      && to != null
                      && !from.equals(to)
                      && target.getSimpleName().endsWith("Repository")) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  static ArchRule moduleMustNotDependOn(String root, String source, String... forbidden) {
    String[] targets = new String[forbidden.length];
    for (int i = 0; i < forbidden.length; i++) {
      targets[i] = root + "." + forbidden[i] + "..";
    }
    return noClasses()
        .that()
        .resideInAPackage(root + "." + source + "..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(targets)
        .allowEmptyShould(true);
  }

  // ---------------------------------------------------------------------------------------------
  // Gardes JPA (M8-01, G Q32, etude-jpa section 6)
  // ---------------------------------------------------------------------------------------------

  private static boolean isEntity(JavaClass c) {
    return c.isAnnotatedWith(ENTITY);
  }

  private static String toSnakeCase(String name) {
    return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
  }

  private static String unquote(String identifier) {
    return identifier.replaceAll("[`\"\\[\\]]", "").trim().toLowerCase(Locale.ROOT);
  }

  /** Nom de table : {@code @Table(name)}, sinon nom de classe en snake_case (naming Boot). */
  static String tableOf(JavaClass entity) {
    String explicit =
        entity
            .tryGetAnnotationOfType(TABLE)
            .flatMap(a -> a.get("name"))
            .map(Object::toString)
            .map(ArchitectureTests::unquote)
            .orElse("");
    return explicit.isEmpty() ? toSnakeCase(entity.getSimpleName()) : explicit;
  }

  /**
   * L'annotation est cherchee sur l'entite et sur ses superclasses elles-memes {@code @Entity}
   * (heritage JPA) ; une {@code @MappedSuperclass} ne compte pas : {@code @Immutable} et
   * {@code @DynamicUpdate} ne sont pas {@code @Inherited}, l'exiger sur l'entite est plus sur.
   */
  private static boolean hasAnnotationInHierarchy(JavaClass entity, String annotation) {
    if (entity.isAnnotatedWith(annotation)) {
      return true;
    }
    return entity.getAllRawSuperclasses().stream()
        .anyMatch(c -> isEntity(c) && c.isAnnotatedWith(annotation));
  }

  private static void collectTypes(JavaType type, List<JavaClass> out) {
    JavaClass erasure = type.toErasure();
    while (erasure.isArray()) {
      erasure = erasure.getComponentType();
    }
    out.add(erasure);
    if (type instanceof JavaParameterizedType parameterized) {
      for (JavaType argument : parameterized.getActualTypeArguments()) {
        collectTypes(argument, out);
      }
    }
  }

  /** Cible d'une association : {@code targetEntity} si present, sinon type (ou type parametre). */
  private static List<JavaClass> associationTargets(JavaAnnotation<?> association, JavaType type) {
    List<JavaClass> targets = new ArrayList<>();
    Object explicit = association.get("targetEntity").orElse(null);
    if (explicit instanceof JavaClass declared && !declared.getName().equals("void")) {
      targets.add(declared);
    } else {
      collectTypes(type, targets);
    }
    return targets;
  }

  /**
   * Une entite ne porte aucune association JPA vers une entite d'un autre module : les cles
   * etrangeres transverses restent des {@code UUID} nus. Modulith ne voit pas ce couplage (le type
   * cible vit dans un autre package, donc il "passe"), le couplage est pourtant bien reel.
   */
  static ArchRule noJpaAssociationAcrossModules(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>("declare a JPA association towards another module") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                String from = moduleOf(entity, root);
                if (from == null) {
                  return;
                }
                for (JavaField field : entity.getAllFields()) {
                  checkMember(entity, field, field.getType(), from, events);
                }
                for (JavaMethod method : entity.getAllMethods()) {
                  checkMember(entity, method, method.getReturnType(), from, events);
                }
              }

              private void checkMember(
                  JavaClass entity,
                  JavaMember member,
                  JavaType type,
                  String from,
                  ConditionEvents events) {
                for (String association : JPA_ASSOCIATIONS) {
                  if (!member.isAnnotatedWith(association)) {
                    continue;
                  }
                  for (JavaClass target :
                      associationTargets(member.getAnnotationOfType(association), type)) {
                    String to = moduleOf(target, root);
                    if (to != null && !to.equals(from)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              member,
                              entity.getName()
                                  + "."
                                  + member.getName()
                                  + " declares @"
                                  + association.substring(association.lastIndexOf('.') + 1)
                                  + " towards "
                                  + target.getName()
                                  + " (module "
                                  + to
                                  + ", entity module "
                                  + from
                                  + ")"));
                    }
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  private static boolean isEndpoint(JavaClass c) {
    return c.isAnnotatedWith(RestController.class)
        || c.isMetaAnnotatedWith(RestController.class)
        || c.isAnnotatedWith(RestControllerAdvice.class)
        || c.isMetaAnnotatedWith(RestControllerAdvice.class);
  }

  private static boolean isUnder(JavaClass c, String root) {
    return c.getPackageName().equals(root) || c.getPackageName().startsWith(root + ".");
  }

  /**
   * Types que Jackson lit ou ecrit pour un point d'entree HTTP : types de retour et parametres des
   * methodes publiques (arguments generiques compris), puis, de proche en proche, les types des
   * champs de ces types. Seules les classes sous {@code root} sont parcourues ; une entite arrete
   * le parcours (elle est signalee par la dependance qui y mene).
   */
  static Set<JavaClass> publicSignatureClosure(JavaClass endpoint, String root) {
    Deque<JavaClass> todo = new ArrayDeque<>();
    for (JavaMethod method : endpoint.getMethods()) {
      if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
        continue;
      }
      List<JavaClass> types = new ArrayList<>();
      collectTypes(method.getReturnType(), types);
      for (JavaType parameter : method.getParameterTypes()) {
        collectTypes(parameter, types);
      }
      todo.addAll(types);
    }
    Set<JavaClass> seen = new LinkedHashSet<>();
    while (!todo.isEmpty()) {
      JavaClass c = todo.pop();
      if (c.equals(endpoint) || !isUnder(c, root) || isEntity(c) || !seen.add(c)) {
        continue;
      }
      for (JavaField field : c.getFields()) {
        if (!field.getModifiers().contains(JavaModifier.STATIC)) {
          List<JavaClass> types = new ArrayList<>();
          collectTypes(field.getType(), types);
          todo.addAll(types);
        }
      }
    }
    return seen;
  }

  /**
   * Aucune entite exposee (ARCH-04, G Q32) : les classes {@code @RestController} et
   * {@code @RestControllerAdvice} ou qu'elles soient rangees, les types de leurs signatures
   * publiques (et, de proche en proche, les types de leurs champs), les types d'un package {@code
   * api} (facades et evenements compris) ne dependent d'aucune {@code @Entity}, et aucune entite ne
   * reside dans {@code api}. Les classes {@code *Dto} et celles d'un package {@code dto} sont
   * verifiees en plus, meme hors signature. Jackson ne doit jamais voir une entite ni un proxy
   * Hibernate, et un autre module ne doit jamais en recevoir une.
   */
  static ArchRule noEntityExposedToWebOrDto(String root) {
    DescribedPredicate<JavaClass> api = JavaClass.Predicates.resideInAPackage(root + "..api..");
    // Les *Mapper et *Assembler vivent dans dto/ et sont les seuls a toucher les entites (G Q33,
    // revue §4.1 regle 3) ; s'ils sont dans une signature publique, la fermeture les verifie.
    DescribedPredicate<JavaClass> mapper =
        DescribedPredicate.describe(
            "a mapper or an assembler",
            c -> {
              String name = topLevel(c).getSimpleName();
              return name.endsWith("Mapper")
                  || name.endsWith("MapperImpl")
                  || name.endsWith("Assembler");
            });
    DescribedPredicate<JavaClass> dto =
        JavaClass.Predicates.simpleNameEndingWith("Dto")
            .or(JavaClass.Predicates.resideInAPackage(root + "..dto.."))
            .and(DescribedPredicate.not(mapper));
    DescribedPredicate<JavaClass> endpoint =
        DescribedPredicate.describe(
            "annotated with @RestController or @RestControllerAdvice",
            ArchitectureTests::isEndpoint);
    DescribedPredicate<JavaClass> checkedOnItsOwn = endpoint.or(api).or(dto);
    return classes()
        .that(checkedOnItsOwn)
        .should(
            new ArchCondition<JavaClass>(
                "expose a JPA entity (controller and its public signature, api, dto)"
                    + " or be an entity (api)") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                if (api.test(origin) && isEntity(origin)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          origin, origin.getName() + " is an @Entity in an api package"));
                }
                reportEntityDependencies(origin, "", events);
                if (!isEndpoint(origin)) {
                  return;
                }
                for (JavaClass exposed : publicSignatureClosure(origin, root)) {
                  if (!checkedOnItsOwn.test(exposed)) {
                    reportEntityDependencies(
                        exposed,
                        " (reachable from the public signature of " + origin.getName() + ")",
                        events);
                  }
                }
              }

              private void reportEntityDependencies(
                  JavaClass c, String context, ConditionEvents events) {
                for (Dependency d : c.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  if (!target.equals(c) && isEntity(target)) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription() + context));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  // ---------------------------------------------------------------------------------------------
  // Gardes independantes de la structure (M9-02, G Q32 a Q38)
  // ---------------------------------------------------------------------------------------------

  /** Modules et sous-domaines JPA (G Q32) : le SQL natif y passe par des fragments (G Q38). */
  static final List<String> JPA_PACKAGES =
      List.of("patients", "vocabulary", "directory", "identity", "clinical.export");

  /** Modules et sous-domaines {@code JdbcClient} (G Q32) : aucune API JPA. */
  static final List<String> JDBC_PACKAGES = List.of("search", "overview", "linking", "clinical");

  /** Sous-domaines JPA a l'interieur d'un module {@code JdbcClient}. */
  static final List<String> JPA_SUBDOMAINS_OF_JDBC_MODULES = List.of("clinical.export");

  /**
   * Classes JdbcClient voulues dans un module JPA (G Q38) : la recherche publique de l'annuaire.
   * Format {@code <package relatif>.<classe de premier niveau>} : la classe peut changer de dossier
   * dans son module sans casser l'entree.
   */
  static final Set<String> JDBC_IN_JPA_ALLOWED = Set.of("directory.PublicDirectoryRepository");

  /**
   * Exceptions temporaires a la garde de technologie. Cette liste ne peut que decroitre : chaque
   * entree porte la tache qui la supprime, et {@link #technologyExceptionsAreStillNeeded()} echoue
   * des qu'une entree ne sert plus.
   */
  static final Set<String> JDBC_IN_JPA_EXCEPTIONS = Set.of();

  private static final String JDBC_CORE = "org.springframework.jdbc.core";

  /**
   * API JPA. {@code org.hibernate} sans Hibernate Validator ({@code org.hibernate.validator}), qui
   * sert a la validation des DTO dans tous les modules.
   */
  private static final List<String> JPA_API_PACKAGES =
      List.of("jakarta.persistence", "org.springframework.data.jpa", "org.hibernate");

  private static final String HIBERNATE_VALIDATOR = "org.hibernate.validator";

  /** Package relatif a la racine ({@code clinical.export}), ou null hors racine. */
  private static String relativePackage(JavaClass c, String root) {
    String pkg = c.getPackageName();
    return pkg.startsWith(root + ".") ? pkg.substring(root.length() + 1) : null;
  }

  private static boolean inAny(String relative, Collection<String> prefixes) {
    return relative != null
        && prefixes.stream().anyMatch(p -> relative.equals(p) || relative.startsWith(p + "."));
  }

  private static boolean inPackage(JavaClass c, String pkg) {
    return c.getPackageName().equals(pkg) || c.getPackageName().startsWith(pkg + ".");
  }

  private static boolean isJdbc(JavaClass c) {
    return inPackage(c, JDBC_CORE);
  }

  private static boolean isJpaApi(JavaClass c) {
    return JPA_API_PACKAGES.stream().anyMatch(p -> inPackage(c, p))
        && !inPackage(c, HIBERNATE_VALIDATOR);
  }

  private static JavaClass topLevel(JavaClass c) {
    JavaClass top = c;
    while (top.getEnclosingClass().isPresent()) {
      top = top.getEnclosingClass().get();
    }
    return top;
  }

  /** Vrai si la classe (ou sa classe englobante) correspond a une entree {@code pkg.Classe}. */
  static boolean matchesClassEntry(JavaClass c, String root, Collection<String> entries) {
    String relative = relativePackage(c, root);
    String simpleName = topLevel(c).getSimpleName();
    for (String entry : entries) {
      int dot = entry.lastIndexOf('.');
      if (entry.substring(dot + 1).equals(simpleName)
          && inAny(relative, List.of(entry.substring(0, dot)))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Technologie d'acces par module (G Q32, Q38) : pas de {@code JdbcClient} ni de {@code
   * JdbcTemplate} dans un module ou sous-domaine JPA, hors {@code allowedJdbcClasses} ; pas d'API
   * JPA ({@code jakarta.persistence}, Spring Data JPA, Hibernate) dans un module {@code JdbcClient}
   * hors de ses sous-domaines JPA.
   */
  static ArchRule dataAccessTechnologyMatchesModule(
      String root, Collection<String> allowedJdbcClasses) {
    return classes()
        .should(
            new ArchCondition<JavaClass>("use the data access technology of its module") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                String relative = relativePackage(origin, root);
                boolean jpa = inAny(relative, JPA_PACKAGES);
                boolean jdbc =
                    inAny(relative, JDBC_PACKAGES)
                        && !inAny(relative, JPA_SUBDOMAINS_OF_JDBC_MODULES);
                if (jpa && matchesClassEntry(origin, root, allowedJdbcClasses)) {
                  return;
                }
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  if (jpa && isJdbc(target)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            d, d.getDescription() + " (JdbcClient in a JPA module, G Q38)"));
                  } else if (jdbc && isJpaApi(target)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            d, d.getDescription() + " (JPA in a JdbcClient module, G Q32)"));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * {@code shared} est de l'infrastructure technique (A5) : il ne depend d'aucun module et
   * n'utilise ni {@code JdbcClient} ni {@code EntityManager} (ARCH-09), sans exception depuis
   * M9-05.
   */
  static ArchRule sharedIsTechnicalOnly(String root) {
    return classes()
        .that()
        .resideInAPackage(root + ".shared..")
        .should(
            new ArchCondition<JavaClass>(
                "depend on a module, on JdbcClient or on jakarta.persistence") {
              @Override
              public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                  JavaClass target = d.getTargetClass();
                  String module = moduleOf(target, root);
                  boolean otherModule = module != null && !module.equals("shared");
                  boolean sql = isJdbc(target) || inPackage(target, "jakarta.persistence");
                  if (otherModule || sql) {
                    events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                  }
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Tables append-only (REVOKE UPDATE, DELETE) : {@code @Immutable} obligatoire ; entry_version :
   * {@code @Immutable} ou {@code @DynamicUpdate} (GRANT UPDATE (title, body, payload) seulement).
   * Le nom de table suit {@code @Table(name)}, sinon le nom de classe en snake_case. La regle ne
   * depend pas de la racine ; le parametre garde la symetrie avec les autres regles.
   */
  static ArchRule appendOnlyEntitiesAreImmutable(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>("be @Immutable (or @DynamicUpdate for entry_version)") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                if (moduleOf(entity, root) == null) {
                  return;
                }
                String table = tableOf(entity);
                boolean immutable = hasAnnotationInHierarchy(entity, IMMUTABLE);
                if (APPEND_ONLY_TABLES.contains(table) && !immutable) {
                  events.add(
                      SimpleConditionEvent.violated(
                          entity,
                          entity.getName()
                              + " maps append-only table "
                              + table
                              + " and must be @Immutable"));
                }
                if (table.equals(ENTRY_VERSION_TABLE)
                    && !immutable
                    && !hasAnnotationInHierarchy(entity, DYNAMIC_UPDATE)) {
                  events.add(
                      SimpleConditionEvent.violated(
                          entity,
                          entity.getName()
                              + " maps entry_version and must be @Immutable or @DynamicUpdate"));
                }
              }
            })
        .allowEmptyShould(true);
  }

  /**
   * Colonnes ecrites par trigger (search_vector) ou generees (occurred_range) : le champ doit etre
   * {@code @Column(insertable = false, updatable = false)}, sinon l'INSERT ou l'UPDATE JPA echoue.
   */
  static ArchRule triggerAndGeneratedColumnsAreReadOnly(String root) {
    return classes()
        .that(annotatedWith(ENTITY))
        .should(
            new ArchCondition<JavaClass>(
                "map search_vector / occurred_range with insertable = false, updatable = false") {
              @Override
              public void check(JavaClass entity, ConditionEvents events) {
                if (moduleOf(entity, root) == null) {
                  return;
                }
                for (JavaField field : entity.getAllFields()) {
                  if (!field.getModifiers().contains(JavaModifier.STATIC)
                      && !field.isAnnotatedWith(TRANSIENT)) {
                    checkMember(entity, field, field.getName(), events);
                  }
                }
                for (JavaMethod method : entity.getAllMethods()) {
                  if (method.isAnnotatedWith(COLUMN)) {
                    checkMember(entity, method, propertyName(method.getName()), events);
                  }
                }
              }

              private String propertyName(String methodName) {
                String bare = methodName;
                if (bare.startsWith("get") && bare.length() > 3) {
                  bare = bare.substring(3);
                } else if (bare.startsWith("is") && bare.length() > 2) {
                  bare = bare.substring(2);
                }
                return Character.toLowerCase(bare.charAt(0)) + bare.substring(1);
              }

              private void checkMember(
                  JavaClass entity, JavaMember member, String property, ConditionEvents events) {
                JavaAnnotation<?> column =
                    member.isAnnotatedWith(COLUMN) ? member.getAnnotationOfType(COLUMN) : null;
                String explicit =
                    column == null
                        ? ""
                        : column
                            .get("name")
                            .map(Object::toString)
                            .map(ArchitectureTests::unquote)
                            .orElse("");
                String name = explicit.isEmpty() ? toSnakeCase(property) : explicit;
                if (!DB_MANAGED_COLUMNS.contains(name)) {
                  return;
                }
                boolean insertable = column != null && isFalse(column, "insertable");
                boolean updatable = column != null && isFalse(column, "updatable");
                if (!insertable || !updatable) {
                  events.add(
                      SimpleConditionEvent.violated(
                          member,
                          entity.getName()
                              + "."
                              + member.getName()
                              + " maps database-managed column "
                              + name
                              + " and needs @Column(insertable = false, updatable = false)"));
                }
              }

              private boolean isFalse(JavaAnnotation<?> column, String property) {
                return column.get(property).map(v -> Boolean.FALSE.equals(v)).orElse(false);
              }
            })
        .allowEmptyShould(true);
  }

  @Test
  void noRepositoryAccessAcrossModulesInProduction() {
    noRepositoryAccessAcrossModules(ROOT).check(PRODUCTION);
  }

  @Test
  void directoryDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "directory", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void identityAdminDoesNotTouchClinicalModules() {
    moduleMustNotDependOn(ROOT, "identity.admin", CLINICAL_MODULES).check(PRODUCTION);
  }

  @Test
  void repositoryRuleDetectsCrossModuleAccess() {
    JavaClasses fixtures = new ClassFileImporter().importPackages("psymatefixture.badarch");
    ArchRule rule = noRepositoryAccessAcrossModules("psymatefixture.badarch");
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AService")
        .hasMessageContaining("BRepository")
        .hasMessageNotContaining("ARepository");
  }

  @Test
  void directoryRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "directory", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DirBad");
  }

  @Test
  void identityAdminRuleDetectsForbiddenImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule = moduleMustNotDependOn(FIXTURE_ROOT, "identity.admin", CLINICAL_MODULES);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("AdminBad");
  }

  @Test
  void reservedPackagesContainNoClassBesidesPackageInfo() {
    for (String reserved : List.of("ai", "companion")) {
      JavaClasses classes = new ClassFileImporter().importPackages(ROOT + "." + reserved);
      assertThat(classes).as(reserved).isNotEmpty();
      assertThat(classes).allMatch(c -> c.getSimpleName().equals("package-info"));
    }
  }

  /**
   * Tout advice de module doit passer strictement avant le filet global de shared (M7-02) : à
   * précédence égale, l'ordre du scan déciderait et le global attraperait tout.
   */
  @Test
  void moduleAdvicesAreOrderedBeforeTheGlobalHandler() {
    JavaClasses advices =
        new ClassFileImporter()
            .importPackages(ROOT)
            .that(annotatedWith(RestControllerAdvice.class));
    assertThat(advices).as("advices").hasSizeGreaterThan(5);
    for (JavaClass advice : advices) {
      if (advice.getName().equals(GlobalExceptionHandler.class.getName())) {
        assertThat(advice.getAnnotationOfType(Order.class).value())
            .as(advice.getName())
            .isEqualTo(Ordered.LOWEST_PRECEDENCE);
        continue;
      }
      assertThat(advice.isAnnotatedWith(Order.class))
          .as(advice.getName() + " porte @Order")
          .isTrue();
      assertThat(advice.getAnnotationOfType(Order.class).value())
          .as(advice.getName())
          .isEqualTo(AdviceOrder.MODULE)
          .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }
  }

  @Test
  void noJpaAssociationAcrossModulesInProduction() {
    noJpaAssociationAcrossModules(ROOT).check(PRODUCTION);
  }

  @Test
  void noEntityExposedToWebOrDtoInProduction() {
    noEntityExposedToWebOrDto(ROOT).check(PRODUCTION);
  }

  @Test
  void appendOnlyEntitiesAreImmutableInProduction() {
    appendOnlyEntitiesAreImmutable(ROOT).check(PRODUCTION);
  }

  @Test
  void triggerAndGeneratedColumnsAreReadOnlyInProduction() {
    triggerAndGeneratedColumnsAreReadOnly(ROOT).check(PRODUCTION);
  }

  @Test
  void jpaAssociationRuleDetectsCrossModuleAssociation() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = noJpaAssociationAcrossModules(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("CrossModuleOrder")
        .hasMessageContaining("CrossModuleCollection")
        .hasMessageContaining("CrossModuleTargetEntity")
        .hasMessageContaining("CrossModuleManyToMany")
        .hasMessageContaining("BEntity")
        .hasMessageNotContaining("SameModuleLine");
  }

  @Test
  void entityExposureRuleDetectsEntityInWebDtoAndApi() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = noEntityExposedToWebOrDto(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("LeakyDto")
        .hasMessageContaining("LeakyController")
        .hasMessageContaining("LeakyApiEntity")
        .hasMessageContaining("LeakyFacade")
        // Hors package web : l'annotation suffit (ARCH-04).
        .hasMessageContaining("LeakyAdvice")
        // Record imbrique d'un conteneur *Dtos, parametre d'un controleur.
        .hasMessageContaining("LeakyDtos$CreateThing")
        // Type atteint par un champ d'un type de retour, sans suffixe Dto.
        .hasMessageContaining("LeakyLine")
        // Classe de dto/ sans suffixe Dto.
        .hasMessageContaining("LeakyRequest")
        .hasMessageNotContaining("CThingMapper")
        .hasMessageNotContaining("GoodDto")
        .hasMessageNotContaining("GoodFacade")
        .hasMessageNotContaining("GoodController")
        .hasMessageNotContaining("CleanView")
        .hasMessageNotContaining("UnexposedHelper");
  }

  // ---------------------------------------------------------------------------------------------
  // M9-02
  // ---------------------------------------------------------------------------------------------

  private static final String TECH_FIXTURE_ROOT = "psymatefixture.badtech";
  private static final String SHARED_FIXTURE_ROOT = "psymatefixture.badshared";

  private static Set<String> union(Set<String> a, Set<String> b) {
    Set<String> all = new HashSet<>(a);
    all.addAll(b);
    return all;
  }

  @Test
  void dataAccessTechnologyMatchesModuleInProduction() {
    dataAccessTechnologyMatchesModule(ROOT, union(JDBC_IN_JPA_ALLOWED, JDBC_IN_JPA_EXCEPTIONS))
        .check(MAIN);
  }

  /** Une exception qui ne sert plus doit etre retiree : la liste ne fait que decroitre. */
  @Test
  void technologyExceptionsAreStillNeeded() {
    for (String entry : union(JDBC_IN_JPA_ALLOWED, JDBC_IN_JPA_EXCEPTIONS)) {
      boolean used =
          MAIN.stream()
              .filter(c -> matchesClassEntry(c, ROOT, List.of(entry)))
              .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
              .anyMatch(d -> isJdbc(d.getTargetClass()));
      assertThat(used).as("exception inutile, a retirer : " + entry).isTrue();
    }
  }

  @Test
  void technologyRuleDetectsJdbcInJpaModuleAndJpaInJdbcModule() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(TECH_FIXTURE_ROOT);
    ArchRule rule =
        dataAccessTechnologyMatchesModule(
            TECH_FIXTURE_ROOT,
            Set.of("directory.PublicDirectoryRepository", "clinical.export.ExportRepository"));
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PatientJdbcRepository")
        .hasMessageContaining("SearchEntityManagerUser")
        .hasMessageContaining("ClinicalJpaThing")
        .hasMessageNotContaining("PublicDirectoryRepository")
        .hasMessageNotContaining("ExportRepository")
        .hasMessageNotContaining("ExportJobStore")
        .hasMessageNotContaining("TimelineRepository")
        .hasMessageNotContaining("SearchValidatedRequest")
        .hasMessageNotContaining("PatientNativeQueriesImpl");
  }

  @Test
  void bootstrapDoesNotDependOnAuth() {
    moduleMustNotDependOn(ROOT, "identity.admin.bootstrap", "identity.auth").check(PRODUCTION);
  }

  @Test
  void bootstrapRuleDetectsAuthImport() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(FIXTURE_ROOT);
    ArchRule rule =
        moduleMustNotDependOn(FIXTURE_ROOT, "identity.admin.bootstrap", "identity.auth");
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BootstrapBad")
        .hasMessageNotContaining("BootstrapGood");
  }

  @Test
  void sharedIsTechnicalOnlyInProduction() {
    sharedIsTechnicalOnly(ROOT).check(MAIN);
  }

  @Test
  void sharedRuleDetectsModuleImportAndSql() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(SHARED_FIXTURE_ROOT);
    ArchRule rule = sharedIsTechnicalOnly(SHARED_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BadProblem")
        .hasMessageContaining("BadLimiter")
        .hasMessageContaining("BadEntityManagerUser")
        .hasMessageContaining("AuditLike")
        .hasMessageNotContaining("GoodShared");
  }

  @Test
  void appendOnlyRuleDetectsMutableEntities() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = appendOnlyEntitiesAreImmutable(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BadSecurityEvent")
        .hasMessageContaining("BadEntryVersionPlain")
        .hasMessageContaining("DocumentAccessLog")
        .hasMessageNotContaining("GoodPatientVersion")
        .hasMessageNotContaining("GoodEntryVersion");
  }

  @Test
  void databaseManagedColumnsRuleDetectsWritableColumns() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(JPA_FIXTURE_ROOT);
    ArchRule rule = triggerAndGeneratedColumnsAreReadOnly(JPA_FIXTURE_ROOT);
    assertThatThrownBy(() -> rule.check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("BadSearchVector")
        .hasMessageContaining("BadSearchVectorPartial")
        .hasMessageContaining("BadOccurredRange")
        .hasMessageNotContaining("GoodGeneratedColumns");
  }

  // ---------------------------------------------------------------------------------------------
  // M9-20
  // ---------------------------------------------------------------------------------------------

  private static final String MAPPING_FIXTURE_ROOT = "psymatefixture.badmapping";

  /**
   * Conventions de mapping (G Q46, revue ARCH-20, A-20) : une classe {@code *Mapper} est un
   * {@code @Mapper} MapStruct ; un mapping ecrit a la main s'appelle {@code *Assembler}.
   * RoleFolderTests range les deux dans {@code dto/}.
   */
  static ArchRule mappersAreMapStruct(String root) {
    return classes()
        .that()
        .resideInAPackage(root + "..")
        .and()
        .haveSimpleNameEndingWith("Mapper")
        .should()
        .beAnnotatedWith("org.mapstruct.Mapper")
        .because("un mapping ecrit a la main s'appelle *Assembler (G Q46)");
  }

  /** L'ancien nom des mappings manuels ({@code *Presenter}, ARCH-20) ne revient pas. */
  static ArchRule noPresenters(String root) {
    return noClasses()
        .that()
        .resideInAPackage(root + "..")
        .should()
        .haveSimpleNameEndingWith("Presenter")
        .because("un mapping ecrit a la main s'appelle *Assembler (G Q46)");
  }

  @Test
  void mappingConventionsHoldInProduction() {
    mappersAreMapStruct(ROOT).check(MAIN);
    noPresenters(ROOT).check(MAIN);
  }

  @Test
  void mappingRulesDetectAHandWrittenMapperAndAPresenter() {
    JavaClasses fixtures = new ClassFileImporter().importPackages(MAPPING_FIXTURE_ROOT);
    assertThatThrownBy(() -> mappersAreMapStruct(MAPPING_FIXTURE_ROOT).check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ManualMapper")
        .hasMessageNotContaining("GoodMapper")
        .hasMessageNotContaining("ThingAssembler");
    assertThatThrownBy(() -> noPresenters(MAPPING_FIXTURE_ROOT).check(fixtures))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("ThingPresenter")
        .hasMessageNotContaining("ThingAssembler");
  }
}
