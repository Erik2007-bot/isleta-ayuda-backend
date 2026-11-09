package com.isletaayuda.backend;

import com.isletaayuda.backend.model.Rol;
import com.isletaayuda.backend.model.Usuario;
import com.isletaayuda.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.location=classpath:persistence-test.properties",
        "spring.datasource.url=jdbc:h2:mem:registro_test;MODE=MariaDB;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class RegistroTests extends SeguridadTestSupport {

    private static final String PASSWORD = "Clave ficticia segura 2026";

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void limpiarBaseTemporal() {
        usuarios.deleteAll();
    }

    @Test
    void usaExclusivamenteH2() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo("jdbc:h2:mem:registro_test");
        }
    }

    @Test
    void registraUsuarioNormalizadoConHashYDatosPublicos() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(registro("  Ana Prueba  ", "  ANA@EXAMPLE.INVALID  ", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUsuario").isNumber())
                .andExpect(jsonPath("$.nombre").value("Ana Prueba"))
                .andExpect(jsonPath("$.email").value("ana@example.invalid"))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.fechaRegistro").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        Usuario guardado = usuarios.findByEmail("ana@example.invalid").orElseThrow();
        assertThat(guardado.getRol()).isEqualTo(Rol.USER);
        assertThat(guardado.getFechaRegistro()).isNotNull();
        assertThat(guardado.getPasswordHash()).startsWith("$2");
        assertThat(guardado.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, guardado.getPasswordHash())).isTrue();
    }

    @Test
    void nuncaPermiteElegirElRolAdmin() throws Exception {
        String body = json.writeValueAsString(Map.of("nombre", "Ana", "email", "ana@example.invalid",
                "password", PASSWORD, "rol", "ADMIN", "idUsuario", 999));
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USER"));
        assertThat(usuarios.findByEmail("ana@example.invalid").orElseThrow().getRol()).isEqualTo(Rol.USER);
    }

    @Test
    void rechazaEmailDuplicadoNormalizado() throws Exception {
        crearUsuario();
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(registro("Otra persona", " ANA@EXAMPLE.INVALID ", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está registrado"));
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @Test
    void traduceLaRestriccionUniqueAunqueLaComprobacionPreviaNoDetecteElDuplicado() throws Exception {
        crearUsuario();
        doReturn(false).when(usuarios).existsByEmail("ana@example.invalid");

        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(registro("Otra persona", "ana@example.invalid", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está registrado"));

        verify(usuarios, org.mockito.Mockito.times(2)).saveAndFlush(org.mockito.ArgumentMatchers.any(Usuario.class));
        assertThat(usuarios.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("registrosInvalidos")
    void rechazaCamposInvalidosSinPersistirNiExponerLaPassword(String body) throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(PASSWORD))));
        assertThat(usuarios.count()).isZero();
    }

    static Stream<String> registrosInvalidos() {
        return Stream.of(
                registro("", "ana@example.invalid", PASSWORD),
                registro(" ".repeat(3), "ana@example.invalid", PASSWORD),
                registro("a".repeat(101), "ana@example.invalid", PASSWORD),
                registro("Ana", "email-invalido", PASSWORD),
                registro("Ana", "", PASSWORD),
                registro("Ana", "a".repeat(140) + "@example.invalid", PASSWORD),
                registro("Ana", "ana@example.invalid", "corta"),
                registro("Ana", "ana@example.invalid", " ".repeat(12)),
                registro("Ana", "ana@example.invalid", "a".repeat(73)),
                registro("Ana", "ana@example.invalid", "á".repeat(37)),
                "{}",
                "{\"nombre\":null,\"email\":null,\"password\":null}"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null"})
    void rechazaJsonInvalido(String body) throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo JSON no es válido"));
        assertThat(usuarios.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/registro", "/api/auth/me", "/api/recursos", "/api/admin", "/login"})
    void cierraLosDemasEndpoints(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginPublicoValidaLosCampos() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void inclusoUnUsuarioAutenticadoTieneCerradasLasDemasRutas() throws Exception {
        mvc.perform(get("/api/recursos")).andExpect(status().isForbidden());
    }

    @Test
    void bcryptUsaSalesDiferentesYConservaLosEspaciosDeLaPassword() {
        String password = "  Clave con espacios  ";
        String primero = passwordEncoder.encode(password);
        String segundo = passwordEncoder.encode(password);
        assertThat(primero).isNotEqualTo(segundo);
        assertThat(passwordEncoder.matches(password, primero)).isTrue();
        assertThat(passwordEncoder.matches(password.strip(), primero)).isFalse();
    }

    private void crearUsuario() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(registro("Ana", "ana@example.invalid", PASSWORD)))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @MethodSource("passwordsValidas")
    void aceptaElLimiteDe72BytesYPreservaLaPasswordOriginal(String password) throws Exception {
        mvc.perform(post("/api/auth/registro").contentType(APPLICATION_JSON)
                        .content(registro("Ana", "ana@example.invalid", password)))
                .andExpect(status().isCreated());
        Usuario usuario = usuarios.findByEmail("ana@example.invalid").orElseThrow();
        assertThat(passwordEncoder.matches(password, usuario.getPasswordHash())).isTrue();
    }

    static Stream<String> passwordsValidas() {
        return Stream.of("a".repeat(72), "á".repeat(36), "  Contraseña con espacios  ");
    }

    private static String registro(String nombre, String email, String password) {
        return JsonMapper.builder().build().writeValueAsString(
                Map.of("nombre", nombre, "email", email, "password", password));
    }
}
