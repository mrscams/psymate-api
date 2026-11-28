package com.psymate.billing.service;

import com.psymate.billing.dto.BillingIbanDeleteRequest;
import com.psymate.billing.dto.BillingIbanRequest;
import com.psymate.billing.model.BillingException;
import com.psymate.billing.model.BillingSettings;
import com.psymate.billing.model.Iban;
import com.psymate.billing.repository.BillingSettingsRepository;
import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.api.EncryptedStorageFacade;
import com.psymate.identity.api.PractitionerFacade;
import com.psymate.shared.problem.FieldProblem;
import com.psymate.shared.problem.ProblemException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * IBAN du praticien (M15-06, L5.1, G Q95). L'IBAN n'est jamais en base, ni dans une réponse, ni
 * dans un journal, ni dans un message d'exception : il est rangé chiffré par {@link
 * EncryptedStorageFacade} sous la clé {@code billing/iban/{practitionerId}} ; seuls ses quatre
 * derniers caractères vont dans {@code billing_settings.iban_last4}. L'écrire ou l'effacer exige le
 * mot de passe actuel du compte (403 PASSWORD_INVALID). Ordre des contrôles : forme du corps
 * (Jakarta), plafond d'essais (429 TOO_MANY_ATTEMPTS), format et clé de l'IBAN (422 IBAN_INVALID),
 * puis mot de passe (403) : le contrôle du mot de passe, coûteux (Argon2), n'est fait que pour une
 * demande recevable.
 *
 * <p>Plafond d'essais : {@code passwordMatches} ne compte rien (M15-03), la limite revient ici.
 * Cinq mots de passe faux en quinze minutes (fenêtre glissante, par praticien, PUT et DELETE
 * confondus) bloquent les demandes suivantes en 429, sans vérifier le mot de passe, jusqu'à la
 * sortie du plus ancien échec de la fenêtre ; un mot de passe juste remet le compteur à zéro.
 *
 * <p>Écriture : la ligne {@code billing_settings} est écrite et envoyée à la base d'abord, puis le
 * fichier chiffré, dans la transaction ; si l'écriture du fichier échoue, la transaction est
 * annulée et {@code iban_last4} reste l'ancien. Si c'est la validation qui échoue après coup, le
 * fichier porte déjà le nouvel IBAN et {@code iban_last4} l'ancien : le praticien reçoit une erreur
 * et recommence, et aucun IBAN n'est perdu (écrire le fichier après la validation exposerait au cas
 * inverse : {@code iban_last4} nouveau, fichier ancien, réponse 204). Effacement : le fichier n'est
 * supprimé qu'après la validation de la transaction, pour ne pas perdre l'IBAN si elle échoue. Les
 * écritures d'un même praticien passent par son verrou consultatif.
 */
@Service
public class BillingIbanService {

  /** Préfixe des clés de stockage des IBAN. */
  static final String KEY_PREFIX = "billing/iban/";

  /** Longueur maximale d'un mot de passe soumis (protège le hachage d'une entrée démesurée). */
  static final int PASSWORD_MAX = 1024;

  /** Échecs du mot de passe admis dans la fenêtre. */
  static final int MAX_PASSWORD_FAILURES = 5;

  /** Fenêtre glissante des échecs du mot de passe. */
  static final Duration PASSWORD_WINDOW = Duration.ofMinutes(15);

  private final BillingSettingsRepository settings;
  private final PractitionerFacade practitioners;
  private final EncryptedStorageFacade storage;
  private final Clock clock;
  private final BillingWindowCounter passwordFailures;

  public BillingIbanService(
      BillingSettingsRepository settings,
      PractitionerFacade practitioners,
      EncryptedStorageFacade storage,
      Clock clock) {
    this.settings = settings;
    this.practitioners = practitioners;
    this.storage = storage;
    this.clock = clock;
    this.passwordFailures = new BillingWindowCounter(MAX_PASSWORD_FAILURES, PASSWORD_WINDOW, clock);
  }

  /** Clé de stockage de l'IBAN d'un praticien. */
  public static String storageKey(UUID practitionerId) {
    return KEY_PREFIX + practitionerId;
  }

  /** Enregistre (ou remplace) l'IBAN. */
  @Transactional
  public void replace(BillingIbanRequest request) {
    CurrentPractitioner me = me();
    requireAttemptsLeft(me);
    requirePasswordLength(request.currentPassword());
    String iban = Iban.normalize(request.iban());
    if (!Iban.isValid(iban)) {
      throw BillingException.invalidFields(List.of(new FieldProblem("iban", "IBAN_INVALID")));
    }
    requirePassword(me, request.currentPassword());
    settings.lockSettings(me.id());
    OffsetDateTime now = now();
    BillingSettings current =
        settings
            .findById(me.id())
            .orElseGet(() -> BillingSettings.defaults(me.id(), me.tenantId(), now));
    current.changeIbanLast4(Iban.last4(iban), now);
    settings.saveAndFlush(current);
    storage.put(storageKey(me.id()), iban.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Efface l'IBAN : le fichier rangé est supprimé et {@code iban_last4} passe à null. Idempotent ;
   * sans ligne de réglages, rien n'est créé.
   */
  @Transactional
  public void delete(BillingIbanDeleteRequest request) {
    CurrentPractitioner me = me();
    requireAttemptsLeft(me);
    requirePasswordLength(request.currentPassword());
    requirePassword(me, request.currentPassword());
    settings.lockSettings(me.id());
    settings
        .findById(me.id())
        .ifPresent(
            current -> {
              if (current.getIbanLast4() != null) {
                current.changeIbanLast4(null, now());
                settings.saveAndFlush(current);
              }
            });
    String key = storageKey(me.id());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            storage.delete(key);
          }
        });
  }

  private static void requirePasswordLength(String password) {
    if (password != null && password.length() > PASSWORD_MAX) {
      throw BillingException.invalidFields(
          List.of(new FieldProblem("currentPassword", "OUT_OF_RANGE")));
    }
  }

  /** 429 tant que le praticien a épuisé ses essais, sans vérifier le mot de passe. */
  private void requireAttemptsLeft(CurrentPractitioner me) {
    passwordFailures
        .blockedFor(me.id())
        .ifPresent(
            wait -> {
              throw BillingException.tooManyAttempts(
                  wait.toSeconds() + (wait.toNanosPart() > 0 ? 1 : 0));
            });
  }

  private void requirePassword(CurrentPractitioner me, String password) {
    if (!practitioners.passwordMatches(me.id(), password)) {
      passwordFailures.record(me.id());
      throw BillingException.passwordInvalid();
    }
    passwordFailures.reset(me.id());
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static CurrentPractitioner me() {
    return CurrentPractitioner.get()
        .orElseThrow(() -> new ProblemException(401, "UNAUTHENTICATED", "Session requise."));
  }
}
