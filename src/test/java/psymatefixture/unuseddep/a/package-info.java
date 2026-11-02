/** Declare b (utilise) et c (jamais utilise) : « autorise ⊆ utilise » signale a -> c. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"b", "c :: api"})
package psymatefixture.unuseddep.a;
