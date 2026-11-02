package com.psymate.identity.admin.service;

import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envoi d'e-mail au praticien après le commit de la transaction en cours, sur un petit pool de fils
 * démons : un échec d'envoi n'annule jamais la décision.
 */
@Component
public class AdminAsyncMail {

  private static final Logger LOG = LoggerFactory.getLogger(AdminAsyncMail.class);

  /** Quelques fils : un SMTP lent ne doit pas retarder tous les e-mails derrière lui. */
  private static final int POOL_SIZE = 4;

  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          POOL_SIZE,
          r -> {
            Thread t = new Thread(r, "admin-account-mail");
            t.setDaemon(true);
            return t;
          });

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }

  /** Appelle {@code send} avec la langue du praticien une fois la transaction validée. */
  void afterCommit(String localeTag, Consumer<Locale> send) {
    Locale locale = Locale.forLanguageTag(localeTag == null ? "fr" : localeTag);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            executor.execute(
                () -> {
                  try {
                    send.accept(locale);
                  } catch (RuntimeException e) {
                    LOG.error("account e-mail could not be sent", e);
                  }
                });
          }
        });
  }
}
