package com.psymate.shared.i18n;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Remplace la {@link LocaleResolver} de Spring Boot (même nom de bean, que Boot ne crée alors pas)
 * par {@link ServedLocaleResolver}, qui ne change que la locale d'une requête en arabe.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class I18nConfiguration {

  @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
  LocaleResolver localeResolver() {
    return new ServedLocaleResolver();
  }
}
