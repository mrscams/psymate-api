package com.psymate.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Locale de Spring MVC : celle de Spring Boot, sauf pour l'arabe (G Q96). */
class ServedLocaleResolverTest {

  private final ServedLocaleResolver resolver = new ServedLocaleResolver();
  private final AcceptHeaderLocaleResolver boot = new AcceptHeaderLocaleResolver();

  private static MockHttpServletRequest request(String header) {
    MockHttpServletRequest r = new MockHttpServletRequest();
    if (header != null) {
      r.addHeader("Accept-Language", header);
    }
    return r;
  }

  @Test
  void otherLanguagesAreResolvedAsSpringBootDoes() {
    for (String header :
        new String[] {null, "", "fr", "fr-FR", "en", "en-GB,fr;q=0.5", "de", "es-ES", "*"}) {
      assertThat(resolver.resolveLocale(request(header)))
          .as(String.valueOf(header))
          .isEqualTo(boot.resolveLocale(request(header)));
    }
  }

  @Test
  void arabicGetsTheArabicMessageLocale() {
    for (String header : new String[] {"ar", "ar-MA,ar;q=0.9", "ar-SA,en;q=0.5"}) {
      Locale resolved = resolver.resolveLocale(request(header));
      assertThat(resolved).as(header).isEqualTo(ServedLanguage.AR.messageLocale());
    }
  }
}
