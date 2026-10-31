package com.psymate.identity.admin.bootstrap;

/**
 * Sortie du processus après la commande {@code --create-admin}. Interface pour que les tests
 * remplacent {@code System.exit} par un enregistreur.
 */
interface ProcessExit {

  void exit(int code);
}
