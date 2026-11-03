package com.psymate.identity.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.identity.auth.AbstractLoginIT;
import com.psymate.identity.model.AuthToken;
import com.psymate.identity.repository.AuthTokenJpaRepository;
import com.psymate.identity.repository.AuthTokenNativeQueries.ConsumedToken;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jeton de changement d'e-mail (M10-04) au niveau de la table : la charge utile {"newEmail"} est
 * relue à la consommation, un jeton n'est consommé qu'une fois, jamais après son expiration (24 h),
 * ni sous un autre type.
 */
class EmailChangeTokenIT extends AbstractLoginIT {

  @Autowired private AuthTokenJpaRepository tokens;

  private UUID savedToken(String hash, String kind, Instant expiresAt, String newAddress)
      throws Exception {
    UUID owner = idOf(account());
    Instant created = expiresAt.minus(AuthService.CONFIRM_TTL);
    tokens.save(
        new AuthToken(
            UUID.randomUUID(),
            owner,
            kind,
            hash,
            Map.of("newEmail", newAddress),
            expiresAt,
            created));
    return owner;
  }

  @Test
  void consumingReturnsThePayloadOnceOnly() throws Exception {
    Instant now = Instant.now();
    String hash = AuthService.sha256(UUID.randomUUID().toString());
    String address = newEmail();
    UUID owner =
        savedToken(hash, AuthService.KIND_EMAIL_CHANGE, now.plus(Duration.ofHours(24)), address);

    Optional<ConsumedToken> first = tokens.consumeWithPayload("email_change", hash, now);
    Optional<ConsumedToken> second = tokens.consumeWithPayload("email_change", hash, now);

    assertThat(first).isPresent();
    assertThat(first.get().practitionerId()).isEqualTo(owner);
    assertThat(
            JsonMapper.builder()
                .build()
                .readTree(first.get().payload())
                .path("newEmail")
                .asString())
        .isEqualTo(address);
    assertThat(second).isEmpty();
  }

  @Test
  void anExpiredTokenIsNeverConsumed_andOneMinuteBeforeExpiryItIs() throws Exception {
    Instant now = Instant.now();
    Instant expiry = now.plus(AuthService.CONFIRM_TTL);
    String late = AuthService.sha256(UUID.randomUUID().toString());
    String early = AuthService.sha256(UUID.randomUUID().toString());
    savedToken(late, "email_change", expiry, newEmail());
    savedToken(early, "email_change", expiry, newEmail());

    assertThat(AuthService.CONFIRM_TTL).isEqualTo(Duration.ofHours(24));
    assertThat(tokens.consumeWithPayload("email_change", late, expiry.plus(Duration.ofMinutes(1))))
        .isEmpty();
    assertThat(
            tokens.consumeWithPayload("email_change", early, expiry.minus(Duration.ofMinutes(1))))
        .isPresent();
  }

  @Test
  void aTokenIsOnlyConsumedUnderItsOwnKind() throws Exception {
    Instant now = Instant.now();
    String hash = AuthService.sha256(UUID.randomUUID().toString());
    savedToken(hash, "email_change", now.plus(Duration.ofHours(24)), newEmail());

    assertThat(tokens.consumeWithPayload("password_reset", hash, now)).isEmpty();
    assertThat(tokens.consume("email_confirm", hash, now)).isEmpty();
    assertThat(tokens.consumeWithPayload("email_change", hash, now)).isPresent();
  }
}
