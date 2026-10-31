package com.psymate.identity.admin.bootstrap;

import com.psymate.identity.admin.bootstrap.AdminBootstrapService.Result;
import com.psymate.identity.admin.bootstrap.AdminBootstrapService.Status;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Commande {@code --create-admin=<e-mail>} (make admin, M7-08). Sans cette option, ne fait rien :
 * aucun effet au démarrage normal, en test, ni en profil par défaut.
 *
 * <p>Le mot de passe est écrit sur la sortie standard (pas dans un logger) et nulle part ailleurs.
 * Codes de sortie : 0 compte créé, 1 e-mail invalide ou compte déjà existant.
 */
@Component
class AdminBootstrapRunner implements ApplicationRunner {

  static final String OPTION = "create-admin";
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final String RULE = "=".repeat(64);

  private final AdminBootstrapService service;
  private final ProcessExit processExit;

  AdminBootstrapRunner(AdminBootstrapService service, ProcessExit processExit) {
    this.service = service;
    this.processExit = processExit;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!args.containsOption(OPTION)) {
      return;
    }
    List<String> values = args.getOptionValues(OPTION);
    String email = values == null || values.isEmpty() ? null : values.get(0).trim();
    if (email == null || !EMAIL.matcher(email).matches()) {
      System.err.println(
          "Adresse e-mail invalide pour --create-admin : " + (email == null ? "(vide)" : email));
      System.err.flush();
      processExit.exit(1);
      return;
    }

    Result result = service.create(email);
    if (result.status() == Status.ALREADY_EXISTS) {
      System.err.println(
          "Un compte existe déjà pour "
              + result.email()
              + " ; aucun doublon créé. Aucun mot de passe n'est renvoyé.");
      System.err.flush();
      processExit.exit(1);
      return;
    }

    System.out.println(RULE);
    System.out.println("Compte administrateur créé");
    System.out.println("E-mail       : " + result.email());
    System.out.println("Mot de passe : " + result.password());
    System.out.println();
    System.out.println("Ce mot de passe est affiché une seule fois et ne sera pas renvoyé.");
    System.out.println("Copiez-le maintenant. Ne le collez ni dans un fichier ni dans un message.");
    System.out.println("Au premier login, le changement de mot de passe est obligatoire,");
    System.out.println("puis l'activation de la double authentification (2FA).");
    System.out.println(RULE);
    System.out.flush();
    processExit.exit(0);
  }
}
