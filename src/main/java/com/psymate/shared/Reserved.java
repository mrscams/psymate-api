package com.psymate.shared;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Type public d'un package {@code api} gardé exprès sans consommateur hors de son module (M9-06, G
 * Q40) : un événement publié dès la V1 comme point d'ancrage (A4.9), ou un type réservé à la V2. La
 * garde {@code ApiUsageTests} refuse tout type public d'{@code api} qui n'est ni utilisé par un
 * autre module, ni annoté ainsi.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Reserved {

  /** Qui l'utilisera : une version ou une tâche, par exemple « V2, module ai ». */
  String consumer();

  /** Pourquoi le type existe déjà sans consommateur. */
  String reason();
}
