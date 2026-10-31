@ApplicationModule(
    allowedDependencies = {"identity :: api", "events :: api", "vocabulary :: api", "shared"})
package com.psymate.patients;

import org.springframework.modulith.ApplicationModule;
