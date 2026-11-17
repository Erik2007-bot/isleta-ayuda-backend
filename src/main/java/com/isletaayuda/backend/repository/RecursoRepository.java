package com.isletaayuda.backend.repository;

import com.isletaayuda.backend.model.Recurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecursoRepository extends JpaRepository<Recurso, Integer> {

    @Query("""
            select distinct r from Recurso r
            join fetch r.organizacion
            left join fetch r.categorias
            where r.activo = true
            order by r.nombre, r.idRecurso
            """)
    List<Recurso> findActivosConRelaciones();
}
