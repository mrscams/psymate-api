package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AuthDtos.LoginRequest;
import com.psymate.identity.auth.AuthDtos.MeResponse;
import com.psymate.identity.auth.AuthDtos.PasswordResetConfirmRequest;
import com.psymate.identity.auth.AuthDtos.PasswordResetRequest;
import com.psymate.identity.auth.AuthDtos.SignupRequest;
import com.psymate.identity.auth.AuthDtos.SignupResponse;
import com.psymate.identity.auth.AuthExceptions.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

  private final AuthService service;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextHolderStrategy strategy =
      SecurityContextHolder.getContextHolderStrategy();
  private final Duration sessionDefault;
  private final Duration sessionRememberMe;

  AuthController(
      AuthService service,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      @Value("${app.auth.session-default}") Duration sessionDefault,
      @Value("${app.auth.session-remember-me}") Duration sessionRememberMe) {
    this.service = service;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.sessionDefault = sessionDefault;
    this.sessionRememberMe = sessionRememberMe;
  }

  @PostMapping("/signup")
  ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest r) {
    PractitionerRow row =
        service.signup(r.email(), r.password(), r.firstName(), r.lastName(), r.profession());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new SignupResponse(row.id(), row.email(), row.displayName(), row.verificationStatus()));
  }

  @PostMapping("/login")
  ResponseEntity<MeResponse> login(
      @Valid @RequestBody LoginRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication result;
    try {
      result =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  AuthService.normalizeEmail(r.email()), r.password()));
    } catch (AuthenticationException e) {
      throw new InvalidCredentialsException();
    }
    PractitionerPrincipal principal = (PractitionerPrincipal) result.getPrincipal();
    CurrentPractitioner current = principal.current();

    // Anti-fixation : nouvel identifiant si une session anonyme existait.
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    Authentication authenticated =
        UsernamePasswordAuthenticationToken.authenticated(current, null, result.getAuthorities());
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(authenticated);
    strategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    HttpSession session = request.getSession(true);
    session.setMaxInactiveInterval(
        (int) (r.rememberMe() ? sessionRememberMe : sessionDefault).toSeconds());
    if (r.rememberMe()) {
      // Lu par RememberMeCookieSerializer à l'écriture du cookie (Max-Age 30 jours).
      request.setAttribute(RememberMeCookieSerializer.REMEMBER_ME_ATTRIBUTE, Boolean.TRUE);
    }
    service.recordLogin(current.id());
    return ResponseEntity.ok(toMe(current));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    strategy.clearContext();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  MeResponse me(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          CurrentPractitioner current) {
    return toMe(current);
  }

  @PostMapping("/password-reset/request")
  ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest r) {
    service.requestPasswordReset(r.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password-reset/confirm")
  ResponseEntity<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest r) {
    service.confirmPasswordReset(r.token(), r.password());
    return ResponseEntity.noContent().build();
  }

  private static MeResponse toMe(CurrentPractitioner c) {
    return new MeResponse(
        c.id(), c.tenantId(), c.email(), c.displayName(), c.profession(), c.verificationStatus());
  }
}
