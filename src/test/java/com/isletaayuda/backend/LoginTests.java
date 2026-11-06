package com.isletaayuda.backend;

import com.isletaayuda.backend.repository.UsuarioRepository;
import com.isletaayuda.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.location=classpath:persistence-test.properties",
        "spring.datasource.url=jdbc:h2:mem:login_test;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class LoginTests extends SeguridadTestSupport {

    private static final String EMAIL = "ana@example.invalid";
    private static final String PASSWORD = "  Clave ficticia segura 2026  ";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private DataSource dataSource;

    private Integer idUsuario;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void prepararUsuarioEnH2() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo("jdbc:h2:mem:login_test");
        }
        usuarios.deleteAll();
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "nombre", "Ana", "email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isCreated());
        idUsuario = usuarios.findByEmail(EMAIL).orElseThrow().getIdUsuario();
    }

    @Test
    void loginEmiteTokenFirmadoDe15MinutosYPermiteConsultarPerfil() throws Exception {
        String token = login();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(idUsuario.toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.ISSUER);
        assertThat(jwt.getAudience()).containsExactly(JwtService.AUDIENCE);
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plusSeconds(900));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario").value(idUsuario))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void contraseñaIncorrectaYEmailInexistenteTienenElMismoError() throws Exception {
        String incorrecta = errorLogin(EMAIL, "Otra clave incorrecta");
        String inexistente = errorLogin("nadie@example.invalid", PASSWORD);
        assertThat(incorrecta).isEqualTo(inexistente);
        assertThat(incorrecta).doesNotContain(PASSWORD, "passwordHash");
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void noRecortaLosEspaciosDeLaContraseña() throws Exception {
        errorLogin(EMAIL, PASSWORD.strip());
    }

    @Test
    void tokenManipuladoSeRechaza() throws Exception {
        String[] partes = login().split("\\.");
        partes[2] = (partes[2].startsWith("A") ? "B" : "A") + partes[2].substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + String.join(".", partes)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"caducado", "emisor", "audiencia", "sin-caducidad", "subject", "futuro"})
    void rechazaClaimsInvalidos(String caso) throws Exception {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(caso.equals("emisor") ? "otro-emisor" : JwtService.ISSUER)
                .audience(List.of(caso.equals("audiencia") ? "otra-api" : JwtService.AUDIENCE))
                .subject(caso.equals("subject") ? "no-es-un-id" : idUsuario.toString())
                .issuedAt(ahora.minusSeconds(1800));
        if (!caso.equals("sin-caducidad")) {
            claims.expiresAt(caso.equals("caducado") ? ahora.minusSeconds(1) : ahora.plusSeconds(900));
        }
        if (caso.equals("futuro")) {
            claims.notBefore(ahora.plusSeconds(300));
        }
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalido", "Basic dXN1YXJpbzpjbGF2ZQ==", "Bearer a.b.c"})
    void rechazaTokenMalFormadoYHttpBasic(String authorization) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioEliminadoNoPuedeConsultarSuPerfil() throws Exception {
        String token = login();
        usuarios.deleteById(idUsuario);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilLeeLosDatosActualesDeLaBaseDeDatos() throws Exception {
        String token = login();
        var usuario = usuarios.findById(idUsuario).orElseThrow();
        usuario.setNombre("Nombre actualizado");
        usuarios.saveAndFlush(usuario);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre actualizado"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/recursos", "/api/admin", "/api/auth/desconocido"})
    void tokenValidoNoAbreLasDemasRutas(String path) throws Exception {
        mvc.perform(get(path).header("Authorization", "Bearer " + login()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "null",
            "{\"email\":\"no-es-email\",\"password\":\"una-clave\"}"})
    void validaElCuerpoDelLogin(String body) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsAceptaSoloElOrigenConfiguradoYSinCookies() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mvc.perform(options("/api/auth/me").header("Origin", FRONTEND)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));

        mvc.perform(get("/api/auth/me").header("Origin", FRONTEND)
                        .header("Authorization", "Bearer " + login()))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void corsRechazaOtroOrigenInclusoEnElRegistroPublico() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "https://otro.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(post("/api/auth/registro").header("Origin", "https://otro.example")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private String login() throws Exception {
        var resultado = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "  ANA@EXAMPLE.INVALID  ", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull())
                .andReturn();
        return json.readTree(resultado.getResponse().getContentAsString()).get("token").asString();
    }

    private String errorLogin(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas o usuario no disponible"))
                .andReturn().getResponse().getContentAsString();
    }
}
