package com.psymate.identity.auth;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.AuthDtos.EmailChangeRequest;
import com.psymate.identity.auth.AuthDtos.EmailConfirmRequest;
import com.psymate.identity.auth.AuthDtos.EmailResendRequest;
import com.psymate.identity.auth.AuthDtos.LoginRequest;
import com.psymate.identity.auth.AuthDtos.MeResponse;
import com.psymate.identity.auth.AuthDtos.PasswordResetConfirmRequest;
import com.psymate.identity.auth.AuthDtos.PasswordResetRequest;
import com.psymate.identity.auth.AuthDtos.RegisterRequest;
import com.psymate.identity.auth.AuthDtos.RegisterResponse;
import com.psymate.identity.auth.AuthDtos.SessionResponse;
import com.psymate.identity.auth.AuthExceptions.InvalidCredentialsException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

  static final String REGISTER_STATUS = "confirmation_email_sent";

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

  @PostMapping("/register")
  @Operation(summary = "Inscription d'un praticien (ouverte, sans code d'invitation)")
  ResponseEntity<RegisterResponse> register(
      @Valid @RequestBody RegisterRequest r,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
      HttpServletRequest request) {
    service.register(r, preferredLocale(acceptLanguage), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(REGISTER_STATUS));
  }

  /** Alias déprécié de /register, même contrat. */
  @PostMapping("/signup")
  @Deprecated
  @Operation(summary = "Alias déprécié de POST /api/auth/register", deprecated = true)
  ResponseEntity<RegisterResponse> signup(
      @Valid @RequestBody RegisterRequest r,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
      HttpServletRequest request) {
    return register(r, acceptLanguage, request);
  }

  @PostMapping("/email/confirm")
  @Operation(summary = "Confirme l'adresse e-mail et ouvre une session")
  ResponseEntity<Void> confirmEmail(
      @Valid @RequestBody EmailConfirmRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    PractitionerRow row = service.confirmEmail(r.token(), request.getRemoteAddr());
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + row.role().toUpperCase(Locale.ROOT)));
    startSession(service.toCurrent(row), authorities, false, request, response);
    service.recordLogin(row.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/email/resend")
  @Operation(summary = "Renvoie l'e-mail de confirmation (202 dans tous les cas)")
  ResponseEntity<Void> resendEmail(@Valid @RequestBody EmailResendRequest r) {
    service.resendConfirmation(r.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/email/change")
  @Operation(summary = "Corrige l'adresse e-mail d'un compte non confirmé")
  ResponseEntity<Void> changeEmail(
      @Valid @RequestBody EmailChangeRequest r,
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          CurrentPractitioner current,
      HttpServletRequest request,
      HttpServletResponse response) {
    PractitionerRow row = service.changeEmail(current.id(), r.newEmail());
    EmailConfirmationFilter.refresh(row, securityContextRepository, request, response);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/session")
  @Operation(summary = "État courant de la session (statut de vérification à jour)")
  SessionResponse session(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          CurrentPractitioner current,
      HttpServletRequest request,
      HttpServletResponse response) {
    PractitionerRow row = service.find(current.id()).orElseThrow(InvalidCredentialsException::new);
    if (!row.verificationStatus().equals(current.verificationStatus())
        || !row.email().equals(current.email())) {
      EmailConfirmationFilter.refresh(row, securityContextRepository, request, response);
    }
    return new SessionResponse(
        row.id(),
        row.email(),
        row.displayName(),
        row.profession(),
        row.verificationStatus(),
        !EmailConfirmationFilter.PENDING.equals(row.verificationStatus()));
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

    startSession(current, result.getAuthorities(), r.rememberMe(), request, response);
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

  /** Ouvre la session serveur (anti-fixation, durée 12 h ou 30 jours avec « rester connecté »). */
  private void startSession(
      CurrentPractitioner current,
      Collection<? extends GrantedAuthority> authorities,
      boolean rememberMe,
      HttpServletRequest request,
      HttpServletResponse response) {
    // Anti-fixation : nouvel identifiant si une session anonyme existait.
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    Authentication authenticated =
        UsernamePasswordAuthenticationToken.authenticated(current, null, authorities);
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(authenticated);
    strategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    HttpSession session = request.getSession(true);
    session.setMaxInactiveInterval(
        (int) (rememberMe ? sessionRememberMe : sessionDefault).toSeconds());
    if (rememberMe) {
      // Lu par RememberMeCookieSerializer à l'écriture du cookie (Max-Age 30 jours).
      request.setAttribute(RememberMeCookieSerializer.REMEMBER_ME_ATTRIBUTE, Boolean.TRUE);
    }
  }

  /** Langue des e-mails : celle du navigateur si c'est l'anglais, le français sinon. */
  static Locale preferredLocale(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return Locale.FRENCH;
    }
    try {
      List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguage);
      return ranges.isEmpty()
          ? Locale.FRENCH
          : AuthMessages.normalize(Locale.forLanguageTag(ranges.get(0).getRange()));
    } catch (IllegalArgumentException e) {
      return Locale.FRENCH;
    }
  }

  private static MeResponse toMe(CurrentPractitioner c) {
    return new MeResponse(
        c.id(), c.tenantId(), c.email(), c.displayName(), c.profession(), c.verificationStatus());
  }
}
