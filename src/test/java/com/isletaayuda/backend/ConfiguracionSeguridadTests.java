package com.isletaayuda.backend;

import com.isletaayuda.backend.security.JwtConfig;
import com.isletaayuda.backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ConfiguracionSeguridadTests {

    @ParameterizedTest
    @ValueSource(strings = {"", "%%%"})
    void rechazaClaveVaciaOBase64Invalido(String secret) {
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtConfig().jwtSecretKey(secret));
    }

    @Test
    void rechazaClaveCorta() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        String secret = Base64.getEncoder().encodeToString(bytes);
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtConfig().jwtSecretKey(secret));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.example", "https://frontend.example/ruta",
            "https://frontend.example/", "https://frontend.example?parametro=1",
            "https://frontend.example#fragmento", "https://usuario@frontend.example", "file:///frontend"})
    void rechazaOrigenesQueNoSeanUnUnicoOrigenHttp(String origin) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SecurityConfig().corsConfigurationSource(origin));
    }

    @Test
    void permiteUnOrigenHttpsExplicito() {
        assertThat(new SecurityConfig().corsConfigurationSource("https://frontend.example")).isNotNull();
    }
}
