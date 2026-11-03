package com.psymate.identity.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** M10-06 : appareil déduit de l'User-Agent, jamais la chaîne brute. */
class UserAgentsTest {

  @Test
  void recognisesCommonBrowsersAndSystems() {
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/124.0.0.0 Safari/537.36"))
        .isEqualTo("Chrome · Windows");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"))
        .isEqualTo("Edge · Windows");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like"
                    + " Gecko) Version/17.4 Safari/605.1.15"))
        .isEqualTo("Safari · macOS");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (X11; Linux x86_64; rv:125.0) Gecko/20100101 Firefox/125.0"))
        .isEqualTo("Firefox · Linux");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15"
                    + " (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"))
        .isEqualTo("Safari · iOS");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/124.0.0.0 Mobile Safari/537.36"))
        .isEqualTo("Chrome · Android");
    assertThat(
            UserAgents.device(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/124.0.0.0 Safari/537.36 OPR/110.0.0.0"))
        .isEqualTo("Opera · Windows");
  }

  @Test
  void keepsWhatIsRecognisedWhenOnlyOnePartIs() {
    assertThat(UserAgents.device("Mozilla/5.0 (Windows NT 10.0)")).isEqualTo("Windows");
    assertThat(UserAgents.device("Firefox/125.0")).isEqualTo("Firefox");
  }

  @Test
  void returnsNullWhenNothingIsRecognised() {
    assertThat(UserAgents.device(null)).isNull();
    assertThat(UserAgents.device("")).isNull();
    assertThat(UserAgents.device("   ")).isNull();
    assertThat(UserAgents.device("curl/8.5.0")).isNull();
  }

  @Test
  void neverReturnsTheRawHeader() {
    String hostile = "Chrome/1 \" } <script>alert(1)</script> Windows " + "x".repeat(5_000);
    String device = UserAgents.device(hostile);
    assertThat(device).isEqualTo("Chrome · Windows");
    assertThat(device).doesNotContain("script").doesNotContain("\"");
  }
}
