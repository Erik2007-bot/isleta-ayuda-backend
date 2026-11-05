package com.isletaayuda.backend.auth.dto;

import com.isletaayuda.backend.model.Rol;

import java.time.LocalDateTime;

public record UsuarioResponse(Integer idUsuario, String nombre, String email, Rol rol,
                              LocalDateTime fechaRegistro) {
}
