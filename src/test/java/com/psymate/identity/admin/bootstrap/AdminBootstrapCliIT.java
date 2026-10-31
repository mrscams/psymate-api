package com.psymate.identity.admin.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.App;
import com.psymate.identity.admin.bootstrap.BootstrapTestConfig.ExitRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.WebApplicationContext;

/**
 * Profil cli (make admin) : l'application est démarrée comme par {@code spring-boot:run}, sans
 * aucune surcharge du type d'application par le test. Le document {@code on-profile: cli} de
 * application.yml doit à lui seul supprimer le serveur web ({@code web-application-type: none}),
 * Flyway migre la base, puis le runner crée le compte depuis l'argument {@code --create-admin} et
 * demande la sortie du processus avec le code 0. Seul {@link ProcessExit} est remplacé, pour ne pas
 * tuer la JVM de test.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapCliIT {

  private static final String EMAIL = "cli-admin@example.org";

  @Test
  void cliProfileStartsWithoutWebServer_migratesAndCreatesTheAdmin(CapturedOutput output) {
    try (ConfigurableApplicationContext context =
        new SpringApplicationBuilder(App.class, BootstrapTestConfig.class)
            .profiles("test", "cli")
            .run("--create-admin=" + EMAIL)) {
      assertThat(context).isNotInstanceOf(WebApplicationContext.class);
      assertThat(context.getBeansOfType(SecurityFilterChain.class)).isEmpty();
      assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
          .isEqualTo("none");

      ExitRecorder exits = context.getBean(ExitRecorder.class);
      assertThat(exits.codes).containsExactly(0);

      JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
      assertThat(
              jdbc.queryForObject(
                  "select role from practitioner where email_normalized = ?", String.class, EMAIL))
          .isEqualTo("admin");
      assertThat(output.getOut()).contains("Compte administrateur créé").contains(EMAIL);
    }
  }
}
