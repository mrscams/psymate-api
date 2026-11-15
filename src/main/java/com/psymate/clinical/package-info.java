@ApplicationModule(
    allowedDependencies = {
      "identity :: api",
      "patients :: api",
      "vocabulary :: api",
      "agenda :: api",
      "events :: api",
      "shared"
    })
package com.psymate.clinical;

import org.springframework.modulith.ApplicationModule;
