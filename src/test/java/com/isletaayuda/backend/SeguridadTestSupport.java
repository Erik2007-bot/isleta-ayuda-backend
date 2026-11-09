package com.isletaayuda.backend;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;

abstract class SeguridadTestSupport {

    protected static final String FRONTEND = "http://localhost:5173";
    private static final String SECRET = generarClave();

    @DynamicPropertySource
    static void propiedadesSeguridad(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("FRONTEND_ORIGIN", () -> FRONTEND);
    }

    private static String generarClave() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
