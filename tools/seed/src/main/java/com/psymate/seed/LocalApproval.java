package com.psymate.seed;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Approbation des comptes du seed, <b>seule écriture SQL du seed</b> (M14-08).
 *
 * <p>Une fiche n'est publiée, et la prise de rendez-vous offerte, que pour un praticien approuvé (G
 * Q71, Q75). Dans l'application, l'approbation est la décision d'un administrateur ({@code POST
 * /api/admin/verifications/{id}/decision}). La passer par l'API serait possible mais
 * disproportionné pour un seed : il faudrait lancer une seconde application ({@code make admin}) et
 * lire son mot de passe à usage unique, activer la double authentification TOTP, déposer un
 * justificatif pour chaque praticien, et laisser en base un compte administrateur durable dont
 * personne n'a besoin. Les IT approuvent déjà leurs praticiens en SQL ({@code
 * PublicBookingFixtures}, par exemple). Le seed pose donc ce seul état en SQL, dans le Postgres de
 * {@code make up} : {@code verification_status = 'approved'} et {@code verified_at} pour les
 * comptes du seed (adresses {@code *.seed@example.test}) encore en attente de vérification. Tout le
 * reste passe par l'API.
 *
 * <p>Exécution : {@code docker exec -i <conteneur> psql} ; conteneur donné par {@code
 * --pg-container}, sinon celui du service {@code postgres} de {@code docker compose} (fichier
 * {@code docker-compose.yml} cherché depuis le dossier courant en remontant).
 */
public final class LocalApproval {

  /** Seules les adresses des comptes du seed sont acceptées (aucune injection possible). */
  private static final Pattern SEED_EMAIL = Pattern.compile("^[a-z]+\\.seed@example\\.test$");

  private final String container;

  /**
   * @param container nom ou identifiant du conteneur Postgres ; null pour celui de docker compose
   */
  public LocalApproval(String container) {
    if (container != null && !validContainer(container)) {
      throw new IllegalArgumentException("Nom de conteneur invalide : " + container);
    }
    this.container = container;
  }

  /** Nom ou identifiant de conteneur Docker ; jamais une option (rien ne commence par « - »). */
  private static final Pattern CONTAINER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]*$");

  /** {@code --pg-container} acceptable : motif de Docker, sans tiret initial. */
  public static boolean validContainer(String name) {
    return name != null && CONTAINER.matcher(name).matches();
  }

  /** Requête d'approbation : ne touche que des comptes praticiens en attente de vérification. */
  static String sql(List<String> emails) {
    List<String> quoted = new ArrayList<>();
    for (String email : emails) {
      if (!SEED_EMAIL.matcher(email).matches()) {
        throw new IllegalArgumentException("Adresse hors seed refusée : " + email);
      }
      quoted.add("'" + email + "'");
    }
    return "UPDATE practitioner SET verification_status = 'approved', verified_at = now(),"
        + " updated_at = now() WHERE role = 'practitioner' AND email_normalized IN ("
        + String.join(", ", quoted)
        + ") AND verification_status IN ('docs_pending', 'under_review', 'needs_more')"
        + " RETURNING email_normalized";
  }

  /** Approuve ces comptes ; renvoie les adresses effectivement passées à {@code approved}. */
  public List<String> approve(List<String> emails) {
    if (emails.isEmpty()) {
      return List.of();
    }
    String id = container != null ? container : composeContainer();
    String out =
        run(
            List.of(
                "docker",
                "exec",
                "-i",
                id,
                "psql",
                "-U",
                "psymate",
                "-d",
                "psymate",
                "-v",
                "ON_ERROR_STOP=1",
                "-qAt",
                "-c",
                sql(emails)));
    List<String> approved = new ArrayList<>();
    for (String line : out.split("\\R")) {
      if (SEED_EMAIL.matcher(line.strip()).matches()) {
        approved.add(line.strip());
      }
    }
    return approved;
  }

  /** Conteneur du service postgres de docker compose. */
  private static String composeContainer() {
    Path compose = findCompose();
    String out =
        run(List.of("docker", "compose", "-f", compose.toString(), "ps", "-q", "postgres")).strip();
    String first = out.isEmpty() ? "" : out.split("\\R")[0].strip();
    if (first.isEmpty()) {
      throw new SeedException(
          "Conteneur postgres de docker compose introuvable ("
              + compose
              + ") : lance make up, ou passe --pg-container <nom du conteneur>.");
    }
    return first;
  }

  private static Path findCompose() {
    for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      Path candidate = dir.resolve("docker-compose.yml");
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new SeedException(
        "docker-compose.yml introuvable depuis "
            + Path.of("").toAbsolutePath()
            + " : lance le seed depuis le dépôt, ou passe --pg-container <nom du conteneur>.");
  }

  private static String run(List<String> command) {
    Process process;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
    } catch (IOException e) {
      throw new SeedException(
          "Commande impossible : " + command.get(0) + " " + command.get(1) + " (" + e + ")", e);
    }
    try (InputStream in = process.getInputStream()) {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      in.transferTo(buffer);
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new SeedException("Commande trop longue : " + command.get(0) + " " + command.get(1));
      }
      String out = buffer.toString(StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        throw new SeedException(
            "Échec de "
                + command.get(0)
                + " "
                + command.get(1)
                + " (code "
                + process.exitValue()
                + ") : "
                + out.strip());
      }
      return out;
    } catch (IOException e) {
      throw new SeedException("Lecture de la sortie impossible (" + e + ")", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SeedException("Interrompu pendant " + command.get(0) + " " + command.get(1), e);
    }
  }
}
