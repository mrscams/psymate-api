# Modules de psymate-api

Fichier genere par `ModularityTests.writesModuleDocumentation` (`./mvnw verify`) : ne pas modifier a la main. Les dependances autorisees viennent des `package-info.java` ; les dependances utilisees, du bytecode (Spring Modulith) : une constante recopiee a la compilation, comme `AdviceOrder.MODULE`, n'y apparait pas.

| Module | Package | Dependances autorisees | Dependances utilisees | Interfaces nommees |
| --- | --- | --- | --- | --- |
| ai | `com.psymate.ai` | clinical, events, linking, shared | — | — |
| clinical | `com.psymate.clinical` | events, identity, patients, shared, vocabulary | events, identity, patients, shared, vocabulary | api |
| companion | `com.psymate.companion` | clinical, events, patients, shared | — | — |
| directory | `com.psymate.directory` | events, identity, shared | events, identity, shared | api |
| events | `com.psymate.events` | shared | — | api |
| identity | `com.psymate.identity` | events, shared | events, shared | api |
| linking | `com.psymate.linking` | clinical, events, identity, patients, shared, vocabulary | clinical, events, identity, patients, shared, vocabulary | api |
| overview | `com.psymate.overview` | clinical, linking, patients, shared | clinical, linking, patients, shared | — |
| patients | `com.psymate.patients` | events, identity, shared, vocabulary | events, identity, shared, vocabulary | api |
| search | `com.psymate.search` | clinical, identity, patients, shared | clinical, identity, patients, shared | — |
| shared | `com.psymate.shared` | — | — | — |
| vocabulary | `com.psymate.vocabulary` | identity, shared | identity | api |
