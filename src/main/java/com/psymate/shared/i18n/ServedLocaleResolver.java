package com.psymate.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Locale de Spring MVC (argument {@code Locale} des contrôleurs et des gestionnaires d'erreurs,
 * messages par défaut de Jakarta Validation) : celle de Spring Boot par défaut ({@link
 * AcceptHeaderLocaleResolver}, sans locale par défaut ni liste de locales), à une exception près.
 * Une requête en arabe reçoit {@link ServedLanguage#messageLocale()} : le français tant que {@code
 * messages_ar.properties} n'existe pas (G Q96), sinon {@code ar-u-nu-latn} (G Q101). Sans cela, les
 * messages par défaut de la validation viendraient du fichier arabe d'Hibernate Validator. Les
 * autres langues, dont le français et l'anglais, sont rendues telles que Spring Boot les rend.
 */
public class ServedLocaleResolver extends AcceptHeaderLocaleResolver {

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    Locale resolved = super.resolveLocale(request);
    return ServedLanguage.of(resolved) == ServedLanguage.AR
        ? ServedLanguage.AR.messageLocale()
        : resolved;
  }
}
