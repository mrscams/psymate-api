package com.psymate.identity.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.auth.AuthTestConfig.MutableClock;
import com.psymate.identity.auth.TotpGenerator;
import com.psymate.identity.auth.service.LoginAttemptLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Vecteurs de la RFC 6238 (annexe B, SHA-1, 6 chiffres), Base32 et verrou en mémoire. */
class TotpTest {

  private static final byte[] KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

  @Test
  void rfc6238Vectors() {
    assertThat(Totp.code(KEY, Totp.stepAt(59))).isEqualTo("287082");
    assertThat(Totp.code(KEY, Totp.stepAt(1111111109L))).isEqualTo("081804");
    assertThat(Totp.code(KEY, Totp.stepAt(1111111111L))).isEqualTo("050471");
    assertThat(Totp.code(KEY, Totp.stepAt(1234567890L))).isEqualTo("005924");
    assertThat(Totp.code(KEY, Totp.stepAt(2000000000L))).isEqualTo("279037");
    assertThat(Totp.code(KEY, Totp.stepAt(20000000000L))).isEqualTo("353130");
  }

  @Test
  void testGeneratorAgreesWithProduction() {
    assertThat(TotpGenerator.code(KEY, 1234567890L)).isEqualTo("005924");
  }

  @Test
  void matchAcceptsOneStepOfDriftOnly() {
    long now = 1_700_000_000L;
    long step = Totp.stepAt(now);
    assertThat(Totp.match(KEY, Totp.code(KEY, step), now)).isEqualTo(step);
    assertThat(Totp.match(KEY, Totp.code(KEY, step - 1), now)).isEqualTo(step - 1);
    assertThat(Totp.match(KEY, Totp.code(KEY, step + 1), now)).isEqualTo(step + 1);
    assertThat(Totp.match(KEY, Totp.code(KEY, step - 3), now)).isEqualTo(-1);
    assertThat(Totp.match(KEY, "12345", now)).isEqualTo(-1);
    assertThat(Totp.match(KEY, null, now)).isEqualTo(-1);
  }

  @Test
  void base32RoundTripAndKnownValue() {
    assertThat(Totp.base32("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    byte[] secret = Totp.newSecret();
    assertThat(Totp.fromBase32(Totp.base32(secret))).isEqualTo(secret);
    assertThat(TotpGenerator.base32Decode(Totp.base32(secret))).isEqualTo(secret);
  }

  @Test
  void otpauthUriCarriesIssuerAndParameters() {
    String uri = Totp.otpauthUri("PsyMate", "maya@example.org", KEY);
    assertThat(uri)
        .startsWith("otpauth://totp/PsyMate:maya%40example.org?secret=")
        .contains("issuer=PsyMate")
        .contains("digits=6")
        .contains("period=30");
  }

  @Test
  void limiterLocksOnFifthFailureForFifteenMinutes() {
    MutableClock clock = new MutableClock();
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
    for (int i = 1; i < 5; i++) {
      assertThat(limiter.recordFailure("k").locked(clock.instant())).isFalse();
    }
    assertThat(limiter.recordFailure("k").locked(clock.instant())).isTrue();
    assertThat(limiter.remainingLock("k"))
        .hasValueSatisfying(d -> assertThat(d.toSeconds()).isEqualTo(900));
    clock.advance(Duration.ofMinutes(15));
    assertThat(limiter.remainingLock("k")).isEmpty();
  }

  @Test
  void limiterFailuresOutsideTheWindowDoNotAccumulate() {
    MutableClock clock = new MutableClock();
    LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
    for (int i = 0; i < 4; i++) {
      limiter.recordFailure("a");
    }
    clock.advance(Duration.ofMinutes(16));
    assertThat(limiter.recordFailure("a").failures()).isEqualTo(1);
    assertThat(limiter.recordFailure("b").failures()).isEqualTo(1);
  }
}
