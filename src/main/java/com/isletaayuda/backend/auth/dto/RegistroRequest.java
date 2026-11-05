package com.isletaayuda.backend.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public record RegistroRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 12, max = 72) String password) {

    public RegistroRequest {
        if (nombre != null) {
            nombre = nombre.strip();
        }
        if (email != null) {
            email = email.strip().toLowerCase(Locale.ROOT);
        }
    }

    @AssertTrue(message = "La contraseña no puede superar 72 bytes UTF-8")
    public boolean isPasswordDentroLimite() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
