package psymatefixture.badadmin.identity.admin.bootstrap;

/** Violation : bootstrap importe identity.auth (promesse de son package-info). */
public class BootstrapBad {
  Object o = new psymatefixture.badadmin.identity.auth.AuthThing();
}
