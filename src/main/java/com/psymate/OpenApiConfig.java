package com.psymate;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadonnees du contrat OpenAPI publie (contract/openapi.yaml). */
@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI psymateOpenApi(@Value("${app.contract.version}") String contractVersion) {
    return new OpenAPI()
        // Serveur relatif : le client genere vise l'origine qui sert l'API, aucun hote en dur.
        .servers(List.of(new Server().url("/").description("Serveur courant")))
        .info(
            new Info()
                .title("PsyMate API")
                .version(contractVersion)
                .description(
                    "API de PsyMate, suivi clinique pour psychologues, psychiatres et coachs."));
  }
}
