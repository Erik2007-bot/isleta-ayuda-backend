package com.isletaayuda.backend.recursos;

import com.isletaayuda.backend.model.Categoria;
import com.isletaayuda.backend.model.Recurso;
import com.isletaayuda.backend.recursos.dto.CategoriaResponse;
import com.isletaayuda.backend.recursos.dto.OrganizacionResponse;
import com.isletaayuda.backend.recursos.dto.RecursoResponse;
import com.isletaayuda.backend.repository.RecursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class RecursoService {

    private final RecursoRepository recursos;

    public RecursoService(RecursoRepository recursos) {
        this.recursos = recursos;
    }

    @Transactional(readOnly = true)
    public List<RecursoResponse> listarActivos() {
        return recursos.findActivosConRelaciones().stream().map(this::toResponse).toList();
    }

    private RecursoResponse toResponse(Recurso recurso) {
        var organizacion = recurso.getOrganizacion();
        var categorias = recurso.getCategorias().stream()
                .sorted(Comparator.comparing(Categoria::getNombre).thenComparing(Categoria::getIdCategoria))
                .map(categoria -> new CategoriaResponse(categoria.getIdCategoria(), categoria.getNombre(),
                        categoria.getSlug()))
                .toList();
        return new RecursoResponse(recurso.getIdRecurso(), recurso.getNombre(), recurso.getDescripcion(),
                recurso.getDireccion(), recurso.getTelefono(), recurso.getWeb(), recurso.getHorario(),
                new OrganizacionResponse(organizacion.getIdOrganizacion(), organizacion.getNombre()), categorias);
    }
}
