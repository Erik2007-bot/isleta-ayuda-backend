package com.isletaayuda.backend.recursos.dto;

import java.util.List;

public record RecursoResponse(Integer id, String nombre, String descripcion, String direccion,
                              String telefono, String web, String horario,
                              OrganizacionResponse organizacion, List<CategoriaResponse> categorias) {
}
