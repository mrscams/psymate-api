package com.psymate.identity.auth.controller;

import com.psymate.identity.api.CurrentPractitioner;
import com.psymate.identity.auth.config.EmailConfirmationFilter;
import com.psymate.identity.auth.config.RememberMeCookieSerializer;
import com.psymate.identity.auth.dto.EmailChangeRequest;
import com.psymate.identity.auth.dto.EmailConfirmRequest;
import com.psymate.identity.auth.dto.EmailResendRequest;
import com.psymate.identity.auth.dto.LoginRequest;
import com.psymate.identity.auth.dto.MeResponse;
import com.psymate.identity.auth.dto.OtpRequiredResponse;
import com.psymate.identity.auth.dto.PasswordChangeRequest;
import com.psymate.identity.auth.dto.PasswordResetConfirmRequest;
import com.psymate.identity.auth.dto.PasswordResetRequest;
import com.psymate.identity.auth.dto.RegisterRequest;
import com.psymate.identity.auth.dto.RegisterResponse;
import com.psymate.identity.auth.dto.RevokedSessionsResponse;
import com.psymate.identity.auth.dto.SessionResponse;
import com.psymate.identity.auth.model.AccountState;
import com.psymate.identity.auth.model.FieldValidationException;
import com.psymate.identity.auth.model.InvalidCredentialsException;
import com.psymate.identity.auth.model.PractitionerRow;
import com.psymate.identity.auth.service.AuthMessages;
import com.psymate.identity.auth.service.AuthService;
import com.psymate.identity.auth.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

@Tag(
    name = "Authentification",
    description =
        "Inscription, confirmation d'adresse e-mail, connexion par session à cookie, changement et"
            + " réinitialisation du mot de passe, et état de la session du praticien.")
@RestController
@RequestMapping("/api/auth")
class AuthController {

  static final String REGISTER_STATUS = "confirmation_email_sent";

  private final AuthService service;
  private final LoginService loginService;
  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextHolderStrategy strategy =
      SecurityContextHolder.getContextHolderStrategy();
  private final Duration sessionDefault;
  private final Duration sessionRememberMe;

  AuthController(
      AuthService service,
      LoginService loginService,
      SecurityContextRepository securityContextRepository,
      @Value("${app.auth.session-default}") Duration sessionDefault,
      @Value("${app.auth.session-remember-me}") Duration sessionRememberMe) {
    this.service = service;
    this.loginService = loginService;
    this.securityContextRepository = securityContextRepository;
    this.sessionDefault = sessionDefault;
    this.sessionRememberMe = sessionRememberMe;
  }

  @PostMapping("/register")
  @Operation(
      summary = "Inscription d'un praticien (ouverte, sans code d'invitation)",
      description =
          """
          Public, sans session. Crée un compte au statut email_pending et envoie un e-mail de \
          confirmation (lien valable 24 h) ; la réponse est identique que l'adresse soit déjà \
          inscrite ou non (anti-énumération : dans ce cas l'adresse reçoit un e-mail « compte \
          existant »). Aucune session n'est ouverte : confirmer l'adresse via \
          POST /api/auth/email/confirm. Les consentements CGU et confidentialité (consents) sont \
          obligatoires et consignés. Langue des e-mails : en-tête Accept-Language (anglais si \
          préféré, français sinon).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Inscription enregistrée (ou adresse déjà connue : réponse identique), e-mail envoyé."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_FIELD (champ du corps absent, trop long, e-mail mal formé, profession"
                + " hors liste, consents.terms ou consents.privacy différent de true),"
                + " PHONE_INVALID (phone), ADDRESS_REQUIRED (address, sauf remoteOnly), REQUIRED"
                + " (address.street, address.city), POSTAL_CODE_INVALID (address.postalCode) ;"
                + " PASSWORD_POLICY avec errors[] sur le champ password et les codes"
                + " PASSWORD_TOO_SHORT, PASSWORD_CASE, PASSWORD_COMPROMISED,"
                + " PASSWORD_CONTAINS_PERSONAL_DATA.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<RegisterResponse> register(
      @Valid @RequestBody RegisterRequest r,
      @Parameter(
              description =
                  "Langue des e-mails envoyés : en si la langue préférée est l'anglais, fr sinon"
                      + " (ou si l'en-tête est absent).",
              example = "fr-FR,fr;q=0.9")
          @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
          String acceptLanguage,
      HttpServletRequest request) {
    service.register(r, preferredLocale(acceptLanguage), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(REGISTER_STATUS));
  }

  /** Alias déprécié de /register, même contrat. */
  @PostMapping("/signup")
  @Deprecated
  @Operation(
      summary = "Alias déprécié de l'inscription d'un praticien",
      description =
          """
          Même contrat, mêmes codes et mêmes effets que POST /api/auth/register ; utiliser \
          /api/auth/register.\
          """,
      deprecated = true)
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description =
            "Inscription enregistrée (ou adresse déjà connue : réponse identique), e-mail envoyé."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] {field, code, message} ; codes de champ :"
                + " INVALID_FIELD (champ du corps absent, trop long, e-mail mal formé, profession"
                + " hors liste, consents.terms ou consents.privacy différent de true),"
                + " PHONE_INVALID (phone), ADDRESS_REQUIRED (address, sauf remoteOnly), REQUIRED"
                + " (address.street, address.city), POSTAL_CODE_INVALID (address.postalCode) ;"
                + " PASSWORD_POLICY avec errors[] sur le champ password et les codes"
                + " PASSWORD_TOO_SHORT, PASSWORD_CASE, PASSWORD_COMPROMISED,"
                + " PASSWORD_CONTAINS_PERSONAL_DATA.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<RegisterResponse> signup(
      @Valid @RequestBody RegisterRequest r,
      @Parameter(
              description =
                  "Langue des e-mails envoyés : en si la langue préférée est l'anglais, fr sinon"
                      + " (ou si l'en-tête est absent).",
              example = "fr-FR,fr;q=0.9")
          @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false)
          String acceptLanguage,
      HttpServletRequest request) {
    return register(r, acceptLanguage, request);
  }

  @PostMapping("/email/confirm")
  @Operation(
      summary = "Confirme un lien reçu par e-mail : adresse du compte ou changement d'adresse",
      description =
          """
          Public. Consomme le jeton reçu par e-mail (usage unique, valable 24 h). Jeton \
          d'inscription : le compte passe de email_pending à docs_pending, la confirmation est \
          journalisée et une session est ouverte (cookie SESSION HttpOnly posé dans la réponse, \
          12 h d'inactivité). Jeton de changement d'adresse (POST /api/auth/email/change) : \
          l'adresse de connexion bascule vers la nouvelle (l'ancienne ne permet plus de se \
          connecter), email_changed est journalisé, les sessions du compte sont révoquées sauf \
          celle qui porte l'appel si elle appartient à ce compte (aucune session n'est ouverte), \
          et l'ancienne adresse reçoit un e-mail d'information. Un jeton déjà utilisé, expiré, \
          inconnu ou remplacé par un renvoi donne 410.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description =
            "Adresse confirmée ; pour une inscription, session ouverte (cookie SESSION), pour un"
                + " changement d'adresse aucune session n'est ouverte.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "Cookie de session SESSION (HttpOnly, Secure, SameSite=Lax), à renvoyer sur"
                        + " les appels suivants ; seulement pour un jeton d'inscription.")),
    @ApiResponse(
        responseCode = "410",
        description = "TOKEN_INVALID_OR_EXPIRED : jeton inconnu, déjà utilisé, expiré ou remplacé.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_FIELD (token absent, vide ou de plus de 200"
                + " caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> confirmEmail(
      @Valid @RequestBody EmailConfirmRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    // Session portée par l'appel : conservée si le jeton change l'e-mail de ce même compte.
    UUID sessionOwner = CurrentPractitioner.get().map(CurrentPractitioner::id).orElse(null);
    HttpSession existing = request.getSession(false);
    AuthService.Confirmation outcome =
        service.confirm(
            r.token(),
            request.getRemoteAddr(),
            existing == null ? null : sessionOwner,
            existing == null ? null : existing.getId());
    if (outcome instanceof AuthService.Confirmation.EmailChanged changed) {
      if (existing != null && changed.practitioner().id().equals(sessionOwner)) {
        // La session conservée porte l'adresse à jour dès cette réponse.
        EmailConfirmationFilter.refresh(
            changed.practitioner(), securityContextRepository, request, response);
      }
      return ResponseEntity.noContent().build();
    }
    PractitionerRow row = ((AuthService.Confirmation.AccountConfirmed) outcome).practitioner();
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + row.role().toUpperCase(Locale.ROOT)));
    startSession(service.toCurrent(row), authorities, false, request, response);
    service.recordLogin(row.id());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/email/resend")
  @Operation(
      summary = "Renvoie l'e-mail de confirmation (202 dans tous les cas)",
      description =
          """
          Public. Réponse 202 identique que le compte existe ou non, qu'il soit déjà confirmé ou \
          non, et que la limite soit atteinte : rien ne distingue un envoi réussi d'un refus \
          silencieux. Seul un compte en email_pending reçoit un nouvel e-mail ; chaque renvoi \
          invalide le lien précédent. Limites : 1 renvoi par minute et 5 par jour et par compte.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Demande acceptée ; un e-mail part seulement si le compte est à confirmer."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_FIELD (email absent, mal formé ou de plus de"
                + " 254 caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> resendEmail(@Valid @RequestBody EmailResendRequest r) {
    service.resendConfirmation(r.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/email/change")
  @Operation(
      summary = "Change l'e-mail de connexion (session) ou corrige celui d'un compte non confirmé",
      description =
          """
          Deux variantes, même route. Avec une session (compte confirmé : docs_pending, \
          under_review, needs_more ou approved) : corps { newEmail, password }, le compte est celui \
          de la session (email est ignoré). Le mot de passe est vérifié : faux, il donne 422 \
          INVALID_PASSWORD et compte dans le verrou e-mail + IP du login (5 échecs, 423 pendant \
          15 min). Sinon la réponse est 202 dans tous les cas : un lien de confirmation (24 h, \
          usage unique) part à la NOUVELLE adresse, et si cette adresse appartient déjà à un compte \
          elle reçoit à la place l'e-mail « vous avez déjà un compte », sans que la réponse \
          diffère. L'ancienne adresse reste valide pour se connecter jusqu'à la confirmation par \
          POST /api/auth/email/confirm ; une nouvelle demande invalide le lien précédent (1 par \
          minute, 5 par jour, au-delà rien ne part). Sans session (variante publique, compte \
          email_pending) : corps { email, password, newEmail } ; réponse 202 dans tous les cas \
          (compte inconnu, mot de passe faux, compte déjà confirmé, nouvelle adresse déjà prise, \
          limite atteinte : aucune différence visible) ; seul un compte en email_pending change \
          d'adresse, un nouveau lien de confirmation part à la nouvelle adresse (1 changement ou \
          renvoi par minute, 5 par jour) ; les mots de passe faux comptent dans le verrou e-mail + \
          IP du login (5 échecs, 423 pendant 15 min).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description =
            "Demande acceptée ; l'adresse n'est changée que si les conditions sont réunies."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_FIELD (password ou newEmail absent, email"
                + " absent sans session, newEmail mal formé, champ trop long) ; INVALID_PASSWORD"
                + " (champ password, variante avec session : mot de passe faux).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : verrouillage e-mail + IP après 5 mots de passe faux (15 min) ;"
                + " retryAfterSeconds et en-tête Retry-After.",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> changeEmail(
      @Valid @RequestBody EmailChangeRequest r, HttpServletRequest request) {
    Optional<CurrentPractitioner> current = CurrentPractitioner.get();
    if (current.isPresent()) {
      service.changeEmail(current.get().id(), r.password(), r.newEmail(), request.getRemoteAddr());
    } else {
      if (r.email() == null || r.email().isBlank()) {
        throw new FieldValidationException(
            List.<String[]>of(new String[] {"email", "INVALID_FIELD", "error.email.required"}));
      }
      service.changeEmail(r.email(), r.password(), r.newEmail(), request.getRemoteAddr());
    }
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/session")
  @Operation(
      summary = "État courant de la session (statut de vérification à jour)",
      description =
          """
          Session praticien, y compris tant que l'e-mail n'est pas confirmé. Relit le compte en \
          base : si le statut de vérification ou l'adresse e-mail ont changé depuis l'ouverture de \
          la session (confirmation depuis un autre navigateur, décision d'un administrateur), la \
          session est rafraîchie. emailConfirmed vaut false tant que le statut est email_pending.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "État à jour de la session."),
    @ApiResponse(
        responseCode = "401",
        description =
            "UNAUTHENTICATED : aucune session valide (cookie absent, expiré ou révoqué) ;"
                + " INVALID_CREDENTIALS : le compte de la session n'existe plus.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
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
        !PractitionerRow.EMAIL_PENDING.equals(row.verificationStatus()));
  }

  @PostMapping("/login")
  @Operation(
      operationId = "login",
      summary = "Connexion par e-mail et mot de passe (code 2FA si activée)",
      description =
          """
          Public. Ouvre une session : cookie SESSION HttpOnly posé dans la réponse, valable 12 h \
          d'inactivité, ou 30 jours avec rememberMe. Si la double authentification est active et \
          que otp manque, réponse 202 {otpRequired: true} : renvoyer la requête avec otp (code \
          TOTP ou code de secours). E-mail inconnu, mot de passe faux et code faux donnent la même \
          réponse 401. Les états du compte (403) ne sont révélés qu'après un mot de passe correct. \
          5 échecs pour le même e-mail et la même IP : 423 pendant 15 min.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Session ouverte ; le praticien connecté est renvoyé.",
        headers =
            @Header(
                name = "Set-Cookie",
                description =
                    "Cookie de session SESSION (HttpOnly, Secure, SameSite=Lax) ; Max-Age de 30"
                        + " jours seulement avec rememberMe, sinon cookie de navigateur."),
        content = @Content(schema = @Schema(implementation = MeResponse.class))),
    @ApiResponse(
        responseCode = "202",
        description =
            "Mot de passe correct, 2FA active et otp absent : aucune session ouverte, renvoyer la"
                + " requête avec otp.",
        content = @Content(schema = @Schema(implementation = OtpRequiredResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description =
            "INVALID_CREDENTIALS : e-mail inconnu, mot de passe faux ou code 2FA faux (message"
                + " unique).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "EMAIL_NOT_CONFIRMED : adresse non confirmée ; ACCOUNT_SUSPENDED : compte refusé"
                + " (rejected), suspendu (suspended) ou clos (closed), sessions révoquées (un"
                + " recours via POST /api/me/appeal n'est possible que pour un compte refusé ou"
                + " suspendu, pas pour un compte clos) ; PASSWORD_CHANGE_REQUIRED : mot de passe"
                + " à usage unique à remplacer d'abord via POST /api/auth/password/change.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_FIELD (email ou password absent ou trop"
                + " long, otp de plus de 32 caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : verrouillage après 5 échecs pour cet e-mail et cette IP (15 min) ;"
                + " retryAfterSeconds et en-tête Retry-After.",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Object> login(
      @Valid @RequestBody LoginRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    LoginService.Outcome outcome =
        loginService.login(r.email(), r.password(), r.otp(), request.getRemoteAddr());
    if (outcome instanceof LoginService.Outcome.OtpRequired) {
      return ResponseEntity.accepted().body(new OtpRequiredResponse(true));
    }
    LoginService.Outcome.Authenticated authenticated = (LoginService.Outcome.Authenticated) outcome;
    PractitionerRow row = authenticated.practitioner();
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + row.role().toUpperCase(Locale.ROOT)));
    CurrentPractitioner current = service.toCurrent(row);
    // Réponse assemblée avant d'ouvrir la session : rien ne peut échouer après startSession.
    MeResponse me = toMe(current, authenticated.account());
    startSession(current, authorities, Boolean.TRUE.equals(r.rememberMe()), request, response);
    service.recordLogin(current.id());
    return ResponseEntity.ok(me);
  }

  /**
   * Public car un compte de bootstrap (M7-08) reçoit 403 PASSWORD_CHANGE_REQUIRED au login, donc
   * n'a aucune session : l'appelant prouve son identité par e-mail + mot de passe actuel (+ code
   * 2FA si elle est active).
   */
  @PostMapping("/password/change")
  @Operation(
      operationId = "changePassword",
      summary = "Change le mot de passe (public : e-mail + mot de passe actuel)",
      description =
          """
          Public, sans session : email + currentPassword prouvent l'identité (+ otp si la 2FA est \
          active ; s'il manque, 202 {otpRequired: true}). Sert notamment à remplacer le mot de \
          passe à usage unique d'un compte de bootstrap (403 PASSWORD_CHANGE_REQUIRED au login). \
          Les contrôles du nouveau mot de passe qui ne dépendent que de la requête (longueur, casse, \
          mots de passe courants, e-mail, identique à l'actuel) passent avant les identifiants : \
          ces 422 ne consomment ni code 2FA ni essai ; le contrôle du prénom et du nom \
          (PASSWORD_CONTAINS_PERSONAL_DATA) n'a lieu qu'après, le code 2FA peut alors être \
          consommé. Succès : toutes les sessions du compte sont révoquées, aucune n'est \
          ouverte (se reconnecter via POST /api/auth/login). Mêmes refus 403 et même verrou 423 \
          que le login (5 échecs, 15 min).\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "Mot de passe changé, sessions révoquées, aucune session ouverte."),
    @ApiResponse(
        responseCode = "202",
        description = "Mot de passe actuel correct, 2FA active et code manquant.",
        content = @Content(schema = @Schema(implementation = OtpRequiredResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description =
            "INVALID_CREDENTIALS : e-mail inconnu, mot de passe actuel faux ou code 2FA faux"
                + " (message unique).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description =
            "EMAIL_NOT_CONFIRMED : adresse non confirmée ; ACCOUNT_SUSPENDED : compte refusé,"
                + " suspendu ou clos.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "PASSWORD_POLICY avec errors[] sur le champ password et les codes PASSWORD_TOO_SHORT,"
                + " PASSWORD_CASE, PASSWORD_COMPROMISED, PASSWORD_CONTAINS_PERSONAL_DATA ;"
                + " VALIDATION_FAILED avec errors[] : PASSWORD_UNCHANGED (newPassword identique au"
                + " mot de passe actuel), INVALID_FIELD (champ du corps absent ou trop long).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "423",
        description =
            "ACCOUNT_LOCKED : verrouillage après 5 échecs pour cet e-mail et cette IP (15 min) ;"
                + " retryAfterSeconds et en-tête Retry-After.",
        headers = @Header(name = "Retry-After", description = "Secondes avant nouvel essai"),
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Object> changePassword(
      @Valid @RequestBody PasswordChangeRequest r, HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    // 422 avant toute vérification d'identifiants : ne consomme ni verrou ni code 2FA.
    AuthService.precheckNewPassword(r.email(), r.currentPassword(), r.newPassword());
    LoginService.Outcome outcome =
        loginService.verifyForPasswordChange(r.email(), r.currentPassword(), r.otp(), ip);
    if (outcome instanceof LoginService.Outcome.OtpRequired) {
      return ResponseEntity.accepted().body(new OtpRequiredResponse(true));
    }
    PractitionerRow row = ((LoginService.Outcome.Authenticated) outcome).practitioner();
    service.changePassword(row, r.currentPassword(), r.newPassword(), ip);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/sessions/revoke-others")
  @Operation(
      operationId = "revokeOtherSessions",
      summary = "Déconnecte tous les autres appareils (la session courante est conservée)",
      description =
          """
          Session praticien. Supprime les sessions du compte autres que celle qui porte l'appel et \
          consigne l'opération (session_revoked). Renvoie le nombre de sessions révoquées, 0 s'il \
          n'y en avait pas d'autre.\
          """)
  @ApiResponses(
      @ApiResponse(
          responseCode = "200",
          description = "Autres sessions révoquées ; revoked = leur nombre."))
  RevokedSessionsResponse revokeOthers(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          CurrentPractitioner current,
      HttpServletRequest request) {
    int revoked =
        service.revokeOtherSessions(
            current.id(), request.getSession().getId(), request.getRemoteAddr());
    return new RevokedSessionsResponse(revoked);
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Ferme la session courante",
      description =
          """
          Public : répond 204 même sans session valide (idempotent). Invalide la session serveur \
          portée par le cookie SESSION ; les autres appareils restent connectés.\
          """)
  @ApiResponses(
      @ApiResponse(responseCode = "204", description = "Session fermée (ou déjà absente)."))
  ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    strategy.clearContext();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @Operation(
      summary = "Praticien connecté (identité, profession, statut, rôle, 2FA)",
      description =
          """
          Session praticien, y compris tant que l'e-mail n'est pas confirmé. L'identité et le \
          statut de vérification sont ceux mémorisés dans la session : le statut peut être en \
          retard, utiliser GET /api/auth/session pour l'état à jour. Le rôle, \
          twoFactorEnabled et passwordChangeRequired sont relus en base à chaque appel, pour que \
          le shell web sache d'emblée si le compte est administrateur, si la double \
          authentification est active et si un changement de mot de passe est imposé. Ces trois \
          champs sont en lecture seule : aucun corps de requête ne les accepte.\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Praticien de la session courante."),
    @ApiResponse(
        responseCode = "401",
        description =
            "UNAUTHENTICATED : aucune session valide (cookie absent, expiré ou révoqué) ;"
                + " INVALID_CREDENTIALS : le compte de la session n'existe plus.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  MeResponse me(
      @org.springframework.security.core.annotation.AuthenticationPrincipal
          CurrentPractitioner current) {
    return toMe(current, service.accountState(current.id()));
  }

  @PostMapping("/password-reset/request")
  @Operation(
      summary = "Demande un lien de réinitialisation du mot de passe (202 dans tous les cas)",
      description =
          """
          Public. Réponse 202 identique que l'adresse corresponde à un compte ou non : elle ne \
          révèle jamais l'existence du compte. Si le compte existe, un e-mail contient un lien \
          à usage unique valable 30 minutes ; une nouvelle demande invalide le lien précédent.\
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Demande acceptée ; un e-mail part seulement si le compte existe."),
    @ApiResponse(
        responseCode = "422",
        description =
            "VALIDATION_FAILED avec errors[] : INVALID_FIELD (email absent, mal formé ou de plus de"
                + " 254 caractères).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
  ResponseEntity<Void> requestReset(@Valid @RequestBody PasswordResetRequest r) {
    service.requestPasswordReset(r.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/password-reset/confirm")
  @Operation(
      summary = "Définit un nouveau mot de passe à partir du lien reçu par e-mail",
      description =
          """
          Public. Consomme le jeton (usage unique, 30 minutes). Le jeton n'est consommé que si le \
          nouveau mot de passe respecte la politique. Succès : mot de passe remplacé, obligation de \
          changement d'un mot de passe à usage unique levée, toutes les sessions du compte \
          révoquées ; aucune session n'est ouverte (se connecter via POST /api/auth/login).\
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Mot de passe remplacé, sessions révoquées."),
    @ApiResponse(
        responseCode = "410",
        description = "TOKEN_INVALID_OR_EXPIRED : jeton inconnu, déjà utilisé, expiré ou remplacé.",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "422",
        description =
            "PASSWORD_POLICY avec errors[] sur le champ password et les codes PASSWORD_TOO_SHORT,"
                + " PASSWORD_CASE, PASSWORD_COMPROMISED, PASSWORD_CONTAINS_PERSONAL_DATA ;"
                + " VALIDATION_FAILED avec errors[] : INVALID_FIELD (token ou password absent ou"
                + " trop long).",
        content =
            @Content(
                mediaType = "application/problem+json",
                schema = @Schema(implementation = ProblemDetail.class)))
  })
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

  private static MeResponse toMe(CurrentPractitioner c, AccountState state) {
    return new MeResponse(
        c.id(),
        c.tenantId(),
        c.email(),
        c.displayName(),
        c.profession(),
        c.verificationStatus(),
        state.role(),
        state.twoFactorEnabled(),
        state.passwordChangeRequired());
  }
}
