package com.psymate.shared.ratelimit;

import com.psymate.shared.problem.ErrorCode;
import com.psymate.shared.problem.ProblemMessages;
import com.psymate.shared.problem.ProblemWriter;
import com.psymate.shared.problem.Problems;
import com.psymate.shared.ratelimit.RateLimitProperties.Key;
import com.psymate.shared.ratelimit.RateLimitProperties.Rule;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtre d'une règle de limitation de débit (G Q24) : une instance par règle, fabriquée par {@link
 * RateLimitFilters}. Le chemin est reconnu sur sa forme décodée (même matcher que Spring MVC),
 * jamais sur l'URI brute : {@code /%63ontact} compte comme {@code /contact}.
 *
 * <p>Clé {@code ip} : {@code getRemoteAddr()} (derrière un proxy, le déploiement doit activer
 * {@code server.forward-headers-strategy}, l'en-tête n'est jamais lu ici), IPv6 ramenée à son /64.
 * Clé {@code session} : identifiant de la session HTTP existante ; sans session, la requête n'est
 * pas comptée par cette règle (les routes sans session, connexion, inscription, annuaire public,
 * ont leurs propres protections). Un refus répond 429 problem+json avec {@code Retry-After}.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

  private final String ruleName;
  private final Rule rule;
  private final RateLimiter limiter;
  private final RequestMatcher matcher;

  RateLimitFilter(String ruleName, RateLimiter limiter) {
    this.ruleName = ruleName;
    this.rule = limiter.rule(ruleName);
    this.limiter = limiter;
    this.matcher = matcherOf(rule);
  }

  private static RequestMatcher matcherOf(Rule rule) {
    PathPatternRequestMatcher.Builder builder = PathPatternRequestMatcher.withDefaults();
    if (rule.methods().isEmpty()) {
      return builder.matcher(rule.pattern());
    }
    List<RequestMatcher> perMethod =
        rule.methods().stream()
            .map(
                m ->
                    (RequestMatcher)
                        builder.matcher(HttpMethod.valueOf(m.toUpperCase()), rule.pattern()))
            .toList();
    return new OrRequestMatcher(perMethod);
  }

  /**
   * Deux filtres de cette classe peuvent se suivre dans la même chaîne (annuaire puis contact) : le
   * marqueur « déjà filtré » doit donc être propre à la règle.
   */
  @Override
  protected String getAlreadyFilteredAttributeName() {
    return RateLimitFilter.class.getName() + "." + ruleName + ".FILTERED";
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !matcher.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = requesterKey(request);
    if (key == null) {
      chain.doFilter(request, response);
      return;
    }
    ConsumptionProbe probe = limiter.tryConsume(ruleName, key);
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    reject(request, response, probe);
  }

  /** Clé de comptage de la requête, ou {@code null} si cette règle ne la compte pas. */
  private String requesterKey(HttpServletRequest request) {
    if (rule.key() == Key.IP) {
      return request.getRemoteAddr();
    }
    HttpSession session = request.getSession(false);
    return session == null ? null : session.getId();
  }

  /** Réponse 429 problem+json avec Retry-After (jamais mise en cache). */
  private static void reject(
      HttpServletRequest request, HttpServletResponse response, ConsumptionProbe probe)
      throws IOException {
    long retryAfter =
        Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    ProblemDetail pd =
        Problems.of(
            HttpStatus.TOO_MANY_REQUESTS,
            ErrorCode.RATE_LIMITED,
            Problems.titleOf(HttpStatus.TOO_MANY_REQUESTS),
            ProblemMessages.detail(
                ErrorCode.RATE_LIMITED,
                ProblemMessages.localeOf(request),
                Long.toString(retryAfter)));
    pd.setProperty("retryAfterSeconds", retryAfter);
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    ProblemWriter.write(response, pd);
  }
}
