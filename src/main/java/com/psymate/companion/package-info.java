@ApplicationModule(
    allowedDependencies = {"patients :: api", "clinical :: api", "events :: api", "shared"})
package com.psymate.companion;

import org.springframework.modulith.ApplicationModule;
