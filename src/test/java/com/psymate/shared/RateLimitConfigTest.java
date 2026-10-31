package com.psymate.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.shared.ratelimit.RateLimitProperties;
import com.psymate.shared.ratelimit.RateLimitProperties.Key;
import com.psymate.shared.ratelimit.RateLimitProperties.Rule;
import com.psymate.shared.ratelimit.RateLimiter;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Garde « aucune valeur de limite n'a changé » (G Q24) : lit le vrai application.yml et vérifie les
 * trois règles, puis que le limiteur les applique bien.
 */
class RateLimitConfigTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RateLimitProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(Config.class);

  @Test
  void applicationYmlKeepsTheDecidedLimits() {
    runner.run(
        ctx -> {
          RateLimitProperties props = ctx.getBean(RateLimitProperties.class);
          assertThat(props.rules())
              .containsOnlyKeys("public-directory", "public-contact", "session");

          Rule directory = props.rules().get("public-directory");
          assertThat(directory.pattern()).isEqualTo("/api/public/**");
          assertThat(directory.methods()).isEmpty();
          assertThat(directory.key()).isEqualTo(Key.IP);
          assertThat(directory.limit()).isEqualTo(60);
          assertThat(directory.period()).isEqualTo(Duration.ofMinutes(1));

          Rule contact = props.rules().get("public-contact");
          assertThat(contact.pattern()).isEqualTo("/api/public/directory/practitioners/*/contact");
          assertThat(contact.methods()).isEqualTo(Set.of("POST"));
          assertThat(contact.key()).isEqualTo(Key.IP);
          assertThat(contact.limit()).isEqualTo(5);
          assertThat(contact.period()).isEqualTo(Duration.ofHours(1));

          Rule session = props.rules().get("session");
          assertThat(session.pattern()).isEqualTo("/api/**");
          assertThat(session.key()).isEqualTo(Key.SESSION);
          assertThat(session.limit()).isEqualTo(600);
          assertThat(session.period()).isEqualTo(Duration.ofMinutes(1));
        });
  }

  @Test
  void limiterEnforcesTheConfiguredLimits() {
    runner.run(
        ctx -> {
          RateLimiter limiter = new RateLimiter(ctx.getBean(RateLimitProperties.class));
          assertThat(consumed(limiter, "public-directory", "198.51.100.1", 70)).isEqualTo(60);
          assertThat(consumed(limiter, "public-contact", "198.51.100.1", 10)).isEqualTo(5);
          assertThat(consumed(limiter, "session", "session-a", 700)).isEqualTo(600);
          // Un autre demandeur a son propre seau.
          assertThat(consumed(limiter, "public-contact", "198.51.100.2", 10)).isEqualTo(5);
        });
  }

  @Test
  void ipv6IsGroupedBySlash64AndTablesAreBounded() {
    runner.run(
        ctx -> {
          RateLimiter limiter = new RateLimiter(ctx.getBean(RateLimitProperties.class));
          assertThat(RateLimiter.ipKey("2001:db8:1:2:0:0:0:1"))
              .isEqualTo(RateLimiter.ipKey("2001:db8:1:2:ffff:0:0:9"));
          assertThat(RateLimiter.ipKey("2001:db8:1:3::1"))
              .isNotEqualTo(RateLimiter.ipKey("2001:db8:1:2::1"));
          assertThat(RateLimiter.ipKey("10.0.0.1")).isEqualTo("10.0.0.1");

          // Deux adresses du même /64 partagent le seau de la règle « ip ».
          for (int i = 0; i < 3; i++) {
            limiter.tryConsume("public-contact", "2001:db8:1:2:0:0:0:" + (i + 1));
          }
          assertThat(limiter.tracked("public-contact")).isEqualTo(1);

          // Au-delà de la borne, les nouveaux demandeurs partagent un seau commun.
          for (int i = 0; i < RateLimiter.MAX_TRACKED + 100; i++) {
            limiter.tryConsume("session", "s" + i);
          }
          assertThat(limiter.tracked("session")).isLessThanOrEqualTo(RateLimiter.MAX_TRACKED + 1);
        });
  }

  private static int consumed(RateLimiter limiter, String rule, String key, int attempts) {
    int ok = 0;
    for (int i = 0; i < attempts; i++) {
      ConsumptionProbe probe = limiter.tryConsume(rule, key);
      if (probe.isConsumed()) {
        ok++;
      }
    }
    return ok;
  }
}
