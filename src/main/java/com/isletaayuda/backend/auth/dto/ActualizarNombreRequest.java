package com.isletaayuda.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarNombreRequest(@NotBlank @Size(max = 100) String nombre) {
}
