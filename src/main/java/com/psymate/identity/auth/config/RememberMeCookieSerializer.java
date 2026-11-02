package com.psymate.identity.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Cookie SESSION HttpOnly, Secure, SameSite=Lax. Quand la requête porte l'attribut {@link
 * #REMEMBER_ME_ATTRIBUTE} (posé par le login avec « rester connecté »), le cookie est écrit avec un
 * Max-Age égal à la durée longue de session ; sinon il n'a pas de Max-Age (cookie de navigateur).
 */
public final class RememberMeCookieSerializer implements CookieSerializer {

  public static final String REMEMBER_ME_ATTRIBUTE = RememberMeCookieSerializer.class.getName();

  private final DefaultCookieSerializer browserSession = secureSerializer();
  private final DefaultCookieSerializer rememberMe = secureSerializer();

  RememberMeCookieSerializer(Duration rememberMeMaxAge) {
    this.rememberMe.setCookieMaxAge((int) rememberMeMaxAge.toSeconds());
  }

  private static DefaultCookieSerializer secureSerializer() {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setUseHttpOnlyCookie(true);
    serializer.setUseSecureCookie(true);
    serializer.setSameSite("Lax");
    return serializer;
  }

  @Override
  public void writeCookieValue(CookieValue cookieValue) {
    HttpServletRequest request = cookieValue.getRequest();
    boolean remembered =
        !cookieValue.getCookieValue().isEmpty()
            && request.getAttribute(REMEMBER_ME_ATTRIBUTE) != null;
    (remembered ? rememberMe : browserSession).writeCookieValue(cookieValue);
  }

  @Override
  public List<String> readCookieValues(HttpServletRequest request) {
    return browserSession.readCookieValues(request);
  }
}
