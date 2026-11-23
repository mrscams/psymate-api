package com.psymate.patients.service;

import com.psymate.shared.i18n.ServedLanguage;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemMessages;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Messages localisés du Profil 360 (clés {@code patients.profile.error.*} de messages_fr.properties
 * et messages_en.properties, MessageFormat toujours actif). Le français est la langue par défaut ;
 * l'anglais n'est servi que si {@code Accept-Language} le place en premier, comme pour les erreurs
 * transverses. Pour une erreur de champ, clé la plus précise d'abord : {@code
 * patients.profile.error.CODE.champ}, puis {@code patients.profile.error.CODE}, puis le code.
 */
@Component
public class PatientProfileMessages {

  private static final String PREFIX = "patients.profile.error.";

  private final MessageSource source;

  PatientProfileMessages() {
    ResourceBundleMessageSource s = new ResourceBundleMessageSource();
    s.setBasename("messages");
    s.setDefaultEncoding("UTF-8");
    s.setFallbackToSystemLocale(false);
    s.setAlwaysUseMessageFormat(true);
    this.source = s;
  }

  /** Locale de la requête HTTP en cours (français hors requête). */
  public Locale locale() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return ProblemMessages.localeOf(attrs.getRequest());
    }
    return ServedLanguage.FR.locale();
  }

  /** Message d'un code métier ({@code patients.profile.error.CODE}). */
  public String domain(String code, Object... args) {
    return source.getMessage(PREFIX + code, args, code, ServedLanguage.messageLocaleOf(locale()));
  }

  /** Erreur de champ complétée de son message localisé. */
  public FieldProblem field(FieldProblem problem) {
    Locale locale = ServedLanguage.messageLocaleOf(locale());
    String generic = PREFIX + problem.code();
    // Pas de message par défaut imbriqué : il serait reformaté par MessageFormat (apostrophes).
    String message = source.getMessage(generic + "." + problem.field(), null, null, locale);
    if (message == null) {
      message = source.getMessage(generic, null, problem.code(), locale);
    }
    return new FieldProblem(problem.field(), problem.code(), message);
  }
}
