package com.isletaayuda.backend.auth.dto;

import com.isletaayuda.backend.model.Rol;

public record PerfilUsuarioResponse(Integer idUsuario, String nombre, String email, Rol rol) {
}
