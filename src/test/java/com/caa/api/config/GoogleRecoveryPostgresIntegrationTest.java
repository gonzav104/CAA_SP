package com.caa.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caa.api.dtos.GoogleCompletarRegistroDTO;
import com.caa.api.dtos.GoogleRecuperacionCompletarDTO;
import com.caa.api.dtos.LoginRequestDTO;
import com.caa.api.dtos.RestablecerPasswordDTO;
import com.caa.api.exceptions.CredencialesInvalidasException;
import com.caa.api.exceptions.RecursoNoEncontradoException;
import com.caa.api.models.Usuario;
import com.caa.api.repositories.UsuarioRepository;
import com.caa.api.services.AuthService;
import com.caa.api.services.EmailService;
import com.caa.api.services.GoogleTokenVerifier;
import com.caa.api.services.GoogleTokenVerifier.GoogleUsuario;
import com.caa.api.services.JwtService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real Spring transactions, real PostgreSQL locks and HTTP cookie/JWT validation. */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never", "spring.jpa.show-sql=false",
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdGVzdHMtMTIzNDU2Nzg5MGFiY2RlZg==",
        "jwt.expiration-ms=3600000", "cloudinary.cloud-name=test", "cloudinary.api-key=test",
        "cloudinary.api-secret=test", "resend.api-key=test", "app.resend.health-check.enabled=false",
        "app.rate-limit.enabled=false"
})
class GoogleRecoveryPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");
    private static final String OLD_PASSWORD = "Attacker123!";
    private static final String NEW_PASSWORD = "OwnerPassword123!";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("bootstrap.sql")));
        }
    }

    @Autowired AuthService auth;
    @Autowired UsuarioRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext context;
    @MockitoBean GoogleTokenVerifier google;
    @MockitoBean EmailService email;
    @MockitoBean DataSeeder seeder;
    private final Map<String, String> delivered = new ConcurrentHashMap<>();
    private MockMvc http;
    private Usuario victim;

    @BeforeEach
    void setup() {
        users.deleteAll();
        delivered.clear();
        victim = local("victim@example.test");
        proof("proof", "subject-victim", victim.getEmail());
        doAnswer(call -> {
            Usuario recipient = call.getArgument(0);
            delivered.put(recipient.getEmail(), call.getArgument(1));
            // Delivery happens after the challenge is committed and visible to another connection.
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.prepareStatement(
                    "SELECT google_recovery_token_hash FROM usuarios WHERE id=?")) {
                statement.setObject(1, recipient.getId());
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isNotBlank();
                }
            }
            return null;
        }).when(email).enviarRecuperacionGoogle(any(), anyString());
        http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void attackerPrecreationRefusesBothGoogleEndpointsWithoutCookieOrMutation() throws Exception {
        String before = state(victim.getId());
        for (String endpoint : new String[]{"/auth/google", "/auth/google/completar-registro"}) {
            var response = http.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"idToken\":\"proof\"}"))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        }
        assertThat(state(victim.getId())).isEqualTo(before);
        noTenantAccess();
    }

    @Test
    void newGoogleIdentityAndRepeatedSubjectLoginDoNotGrantTenantAccess() {
        proof("new-proof", "new-subject", "new@example.test");
        assertThat(auth.loginConGoogle("new-proof").token()).isEmpty();
        var created = auth.completarRegistroGoogle(new GoogleCompletarRegistroDTO("new-proof"));
        assertThat(created.token()).isPresent();
        Usuario user = users.findByGoogleSub("new-subject").orElseThrow();
        assertThat(user.getEmail()).isEqualTo("new@example.test");
        assertThat(auth.loginConGoogle("new-proof").token()).isPresent();
        assertThat(auth.completarRegistroGoogle(new GoogleCompletarRegistroDTO("new-proof")).token()).isPresent();
        assertThat(users.count()).isEqualTo(2);
        noTenantAccess();
    }

    @Test
    void recoveryRequiresBothProofsAndReplacesAllAttackerCredentials() throws Exception {
        String attackerJwt = auth.login(new LoginRequestDTO(victim.getEmail(), OLD_PASSWORD)).token();
        auth.olvidePassword(victim.getEmail());
        String resetHash = users.findById(victim.getId()).orElseThrow().getResetToken();
        String token = request("proof", victim.getEmail());
        assertThat(token).hasSize(43);
        Usuario challenged = users.findById(victim.getId()).orElseThrow();
        assertThat(challenged.getGoogleRecoveryTokenHash()).isEqualTo(hash(token)).isNotEqualTo(token);
        assertThat(challenged.getGoogleRecoverySub()).isEqualTo("subject-victim");
        assertThat(challenged.getGoogleRecoveryExpiresAt()).isAfter(LocalDateTime.now());
        var response = http.perform(post("/auth/google/recuperacion/completar")
                        .contentType(MediaType.APPLICATION_JSON).content(completionJson("proof", token, NEW_PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getCookie("jwt")).isNotNull();
        Usuario recovered = users.findById(victim.getId()).orElseThrow();
        assertThat(recovered.getGoogleSub()).isEqualTo("subject-victim");
        assertThat(recovered.getTokenVersion()).isEqualTo(1);
        assertThat(recovered.getResetToken()).isNull();
        assertThat(recovered.getResetTokenExpira()).isNull();
        assertThat(recovered.getGoogleRecoveryTokenHash()).isNull();
        assertThat(recovered.getGoogleRecoveryExpiresAt()).isNull();
        assertThat(recovered.getGoogleRecoverySub()).isNull();
        assertThat(recovered.getGoogleRecoveryEmail()).isNull();
        Optional<Usuario> byResetHash = new TransactionTemplate(transactionManager)
                .execute(transaction -> users.findLockedByResetToken(resetHash));
        assertThat(byResetHash).isEmpty();
        assertThatThrownBy(() -> auth.login(new LoginRequestDTO(victim.getEmail(), OLD_PASSWORD)))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(auth.login(new LoginRequestDTO(victim.getEmail(), NEW_PASSWORD)).token()).isNotBlank();
        http.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + attackerJwt))
                .andExpect(status().isUnauthorized());
        http.perform(get("/api/usuarios/me").cookie(response.getCookie("jwt"))).andExpect(status().isOk());
        assertThatThrownBy(() -> complete("proof", token)).isInstanceOf(CredencialesInvalidasException.class);
        noTenantAccess();
    }

    @Test
    void missingGoogleProofMismatchedSubjectAndOldPasswordDoNotConsumeChallenge() {
        String token = request("proof", victim.getEmail());
        String before = state(victim.getId());
        proof("other", "different-subject", victim.getEmail());
        assertThatThrownBy(() -> complete("invalid", token)).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> complete("other", token)).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> auth.completarRecuperacionGoogle(
                new GoogleRecuperacionCompletarDTO("proof", token, OLD_PASSWORD)))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(state(victim.getId())).isEqualTo(before);
        assertThat(complete("proof", token).token()).isPresent();
    }

    @Test
    void expiredReissuedAndUnknownTokensCannotBeConsumed() {
        String first = request("proof", victim.getEmail());
        String second = request("proof", victim.getEmail());
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> complete("proof", first)).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> complete("proof", "unknown")).isInstanceOf(CredencialesInvalidasException.class);
        jdbc.update("UPDATE usuarios SET google_recovery_expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE id=?",
                victim.getId());
        String before = state(victim.getId());
        assertThatThrownBy(() -> complete("proof", second)).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void existingBoundSubjectAndEmailConflictsNeverRelink() {
        String token = request("proof", victim.getEmail());
        complete("proof", token);
        String before = state(victim.getId());
        proof("changed-sub", "another-subject", victim.getEmail());
        proof("changed-email", "subject-victim", "different@example.test");
        for (String proof : new String[]{"changed-sub", "changed-email"}) {
            assertThatThrownBy(() -> auth.loginConGoogle(proof)).isInstanceOf(CredencialesInvalidasException.class);
            assertThatThrownBy(() -> auth.completarRegistroGoogle(new GoogleCompletarRegistroDTO(proof)))
                    .isInstanceOf(CredencialesInvalidasException.class);
            auth.solicitarRecuperacionGoogle(proof);
        }
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void subjectOwnedByAnotherAccountRejectsRequestAndExistingChallengeWithoutMutation() {
        String token = request("proof", victim.getEmail());
        Usuario owner = local("subject-owner@example.test");
        owner.setGoogleSub("subject-victim");
        users.saveAndFlush(owner);
        String before = state(victim.getId());
        delivered.clear();
        auth.solicitarRecuperacionGoogle("proof");
        assertThat(delivered).isEmpty();
        assertThatThrownBy(() -> complete("proof", token)).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void ordinaryRecoveryEmailIsDeliveredOnlyAfterCommitWithIndependentlyVisibleHash() {
        doAnswer(call -> {
            Usuario recipient = call.getArgument(0);
            String rawToken = call.getArgument(1);
            // A distinct physical connection cannot observe an uncommitted reset hash.
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                    POSTGRES.getUsername(), POSTGRES.getPassword()); var statement = connection.prepareStatement(
                    "SELECT reset_token FROM usuarios WHERE id=?")) {
                statement.setObject(1, recipient.getId());
                try (var row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(1)).isEqualTo(hash(rawToken));
                }
            }
            return null;
        }).when(email).enviarRecuperacionPassword(any(), anyString());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            auth.olvidePassword(victim.getEmail());
            verify(email, never()).enviarRecuperacionPassword(any(), anyString());
        });
        verify(email, times(1)).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    void ordinaryRecoveryRollbackKeepsPreviousTokenAndSendsNoEmail() throws Exception {
        installResetToken();
        String before = state(victim.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            auth.olvidePassword(victim.getEmail());
            verify(email, never()).enviarRecuperacionPassword(any(), anyString());
            transaction.setRollbackOnly();
        });
        verify(email, never()).enviarRecuperacionPassword(any(), anyString());
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void rollbackOfChallengeCreationDoesNotSendAnUncommittedLink() {
        String before = state(victim.getId());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            auth.solicitarRecuperacionGoogle("proof");
            assertThat(delivered).isEmpty();
            transaction.setRollbackOnly();
        });
        assertThat(delivered).isEmpty();
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void emailProofAloneAndWeakReplacementPasswordAreRejectedByHttp() throws Exception {
        String token = request("proof", victim.getEmail());
        String before = state(victim.getId());
        http.perform(post("/auth/google/recuperacion/completar").contentType(MediaType.APPLICATION_JSON)
                        .content(completionJson("", token, NEW_PASSWORD))).andExpect(status().isBadRequest());
        http.perform(post("/auth/google/recuperacion/completar").contentType(MediaType.APPLICATION_JSON)
                        .content(completionJson("proof", token, "weak"))).andExpect(status().isBadRequest());
        assertThat(state(victim.getId())).isEqualTo(before);
    }

    @Test
    void requestResponseDoesNotRevealMissingOrConflictingAccount() throws Exception {
        proof("missing", "missing-subject", "missing@example.test");
        String expected = null;
        for (String proof : new String[]{"proof", "missing"}) {
            var result = http.perform(post("/auth/google/recuperacion/solicitar")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"" + proof + "\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertThat(result.getCookies()).isEmpty();
            if (expected == null) expected = result.getContentAsString();
            else assertThat(result.getContentAsString()).isEqualTo(expected);
        }
        assertThat(delivered).containsOnlyKeys(victim.getEmail());
    }

    @Test
    void concurrentCompletionConsumesExactlyOnce() throws Exception {
        String token = request("proof", victim.getEmail());
        CyclicBarrier start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> concurrentComplete(start, "proof", token));
            var second = pool.submit(() -> concurrentComplete(start, "proof", token));
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(users.findById(victim.getId()).orElseThrow().getTokenVersion()).isEqualTo(1);
    }

    @Test
    void sameSubjectRacingAcrossTwoAccountsHasOneWinnerAndRollsBackLoser() throws Exception {
        Usuario other = local("other@example.test");
        proof("other-proof", "subject-victim", other.getEmail());
        String firstToken = request("proof", victim.getEmail());
        String secondToken = request("other-proof", other.getEmail());
        Map<UUID, String> before = Map.of(victim.getId(), state(victim.getId()), other.getId(), state(other.getId()));
        CyclicBarrier start = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> concurrentComplete(start, "proof", firstToken));
            var b = pool.submit(() -> concurrentComplete(start, "other-proof", secondToken));
            assertThat(a.get(15, TimeUnit.SECONDS) + b.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        for (Usuario user : users.findAll()) {
            if (user.getGoogleSub() == null) assertThat(state(user.getId())).isEqualTo(before.get(user.getId()));
            else assertThat(user.getTokenVersion()).isEqualTo(1);
        }
    }

    @Test
    void forgotPasswordWaitingBehindRecoveryCannotResurrectOldCredentials() throws Exception {
        String token = request("proof", victim.getEmail());
        raceBehindLockedRecovery(() -> auth.olvidePassword(victim.getEmail()), token, false);
        Usuario result = users.findById(victim.getId()).orElseThrow();
        assertThat(result.getGoogleSub()).isEqualTo("subject-victim");
        assertThat(result.getTokenVersion()).isEqualTo(1);
        assertThat(passwords.matches(NEW_PASSWORD, result.getPasswordHash())).isTrue();
    }

    @Test
    void oldResetWaitingBehindRecoveryCannotOverwriteBindingPasswordOrVersion() throws Exception {
        String oldReset = installResetToken();
        String token = request("proof", victim.getEmail());
        raceBehindLockedRecovery(() -> auth.restablecerPassword(new RestablecerPasswordDTO(oldReset, "Other123!")),
                token, true);
        Usuario result = users.findById(victim.getId()).orElseThrow();
        assertThat(result.getGoogleSub()).isEqualTo("subject-victim");
        assertThat(result.getTokenVersion()).isEqualTo(1);
        assertThat(passwords.matches(NEW_PASSWORD, result.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> auth.restablecerPassword(new RestablecerPasswordDTO(oldReset, "Other123!")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void ordinaryResetWinningFirstInvalidatesPendingGoogleChallenge() throws Exception {
        String reset = installResetToken();
        String googleToken = request("proof", victim.getEmail());
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                users.findLockedByEmail(victim.getEmail()).orElseThrow();
                pending[0] = pool.submit(() -> complete("proof", googleToken));
                awaitDatabaseLock();
                auth.restablecerPassword(new RestablecerPasswordDTO(reset, NEW_PASSWORD));
            });
            assertThatThrownBy(() -> pending[0].get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(CredencialesInvalidasException.class);
        }
        Usuario result = users.findById(victim.getId()).orElseThrow();
        assertThat(result.getGoogleSub()).isNull();
        assertThat(result.getGoogleRecoveryTokenHash()).isNull();
        assertThat(result.getTokenVersion()).isEqualTo(1);
    }

    private void raceBehindLockedRecovery(Runnable other, String token, boolean shouldFail) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?>[] pending = new Future<?>[1];
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                users.findLockedByEmail(victim.getEmail()).orElseThrow();
                pending[0] = pool.submit(other);
                awaitDatabaseLock();
                complete("proof", token);
            });
            if (shouldFail) assertThatThrownBy(() -> pending[0].get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RecursoNoEncontradoException.class);
            else pending[0].get(15, TimeUnit.SECONDS);
        }
    }

    private void awaitDatabaseLock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE datname=current_database() AND wait_event_type='Lock'", Integer.class);
            if (waiting != null && waiting > 0) return;
            try { Thread.sleep(20); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
        throw new AssertionError("Competing service never waited on the PostgreSQL row lock");
    }

    private int concurrentComplete(CyclicBarrier start, String proof, String token) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try { complete(proof, token); return 1; }
        catch (CredencialesInvalidasException expected) { return 0; }
    }

    private String installResetToken() throws Exception {
        String token = "ordinary-reset-link";
        jdbc.update("UPDATE usuarios SET reset_token=?,reset_token_expira=CURRENT_TIMESTAMP+INTERVAL '1 hour' WHERE id=?",
                hash(token), victim.getId());
        return token;
    }

    private Usuario local(String email) {
        return users.saveAndFlush(Usuario.builder().email(email).nombre("User")
                .passwordHash(passwords.encode(OLD_PASSWORD)).build());
    }

    private void proof(String token, String sub, String email) {
        when(google.verificar(token)).thenReturn(Optional.of(new GoogleUsuario(sub, email, "Owner")));
    }

    private String request(String proof, String email) {
        auth.solicitarRecuperacionGoogle(proof);
        assertThat(delivered.get(email)).isNotBlank();
        return delivered.get(email);
    }

    private AuthService.GoogleLoginResult complete(String proof, String token) {
        return auth.completarRecuperacionGoogle(new GoogleRecuperacionCompletarDTO(proof, token, NEW_PASSWORD));
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT to_jsonb(u)::text FROM usuarios u WHERE id=?", String.class, id);
    }

    private void noTenantAccess() {
        for (String table : new String[]{"organizaciones", "membresias", "pacientes_terapeutas", "pacientes_familiares"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero();
        }
    }

    private static String completionJson(String proof, String token, String password) {
        return "{\"idToken\":\"" + proof + "\",\"token\":\"" + token + "\",\"password\":\"" + password + "\"}";
    }

    private static String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
