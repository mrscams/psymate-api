package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class AppContextTest {

  @Test
  void contextLoadsWithExpectedProperties(
      ApplicationContext context,
      @Value("${app.name}") String appName,
      @Value("${app.mail.from}") String mailFrom,
      @Value("${versioning.grace-period}") String gracePeriod) {
    assertThat(context).isNotNull();
    assertThat(appName).isEqualTo("PsyMate");
    assertThat(mailFrom).isEqualTo("test@gmail.com");
    assertThat(gracePeriod).isEqualTo("75m");
  }
}
