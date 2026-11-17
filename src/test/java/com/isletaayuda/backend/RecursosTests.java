package com.isletaayuda.backend;

import com.isletaayuda.backend.model.Categoria;
import com.isletaayuda.backend.model.Organizacion;
import com.isletaayuda.backend.model.Recurso;
import com.isletaayuda.backend.repository.CategoriaRepository;
import com.isletaayuda.backend.repository.OrganizacionRepository;
import com.isletaayuda.backend.repository.RecursoRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.location=classpath:persistence-test.properties",
        "spring.datasource.url=jdbc:h2:mem:recursos_test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureMockMvc
class RecursosTests extends SeguridadTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecursoRepository recursos;

    @Autowired
    private OrganizacionRepository organizaciones;

    @Autowired
    private CategoriaRepository categorias;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void limpiarDatos() {
        recursos.deleteAll();
        categorias.deleteAll();
        organizaciones.deleteAll();
    }

    @Test
    void listaActivosSinJwtConDtosYUnaSolaConsulta() throws Exception {
        Organizacion organizacion = guardarOrganizacion("Asociacion de ayuda");
        Categoria vivienda = guardarCategoria("Vivienda", "vivienda");
        Categoria alimentos = guardarCategoria("Alimentacion", "alimentacion");
        Recurso recurso = guardarRecurso("Comedor", true, organizacion, Set.of(vivienda, alimentos));
        guardarRecurso("Orientacion", true, guardarOrganizacion("Otra asociacion"), Set.of(vivienda));
        var estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        mvc.perform(get("/api/recursos"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].length()").value(9))
                .andExpect(jsonPath("$[0].id").value(recurso.getIdRecurso()))
                .andExpect(jsonPath("$[0].nombre").value("Comedor"))
                .andExpect(jsonPath("$[0].descripcion").value("Descripcion de Comedor"))
                .andExpect(jsonPath("$[0].direccion").value("Calle de prueba 1"))
                .andExpect(jsonPath("$[0].telefono").value("928123456"))
                .andExpect(jsonPath("$[0].web").value("https://example.invalid"))
                .andExpect(jsonPath("$[0].horario").value("Lunes a viernes"))
                .andExpect(jsonPath("$[0].organizacion.id").value(organizacion.getIdOrganizacion()))
                .andExpect(jsonPath("$[0].organizacion.nombre").value("Asociacion de ayuda"))
                .andExpect(jsonPath("$[0].categorias.length()").value(2))
                .andExpect(jsonPath("$[0].categorias[0].id").value(alimentos.getIdCategoria()))
                .andExpect(jsonPath("$[0].categorias[0].nombre").value("Alimentacion"))
                .andExpect(jsonPath("$[0].categorias[0].slug").value("alimentacion"))
                .andExpect(jsonPath("$[0].categorias[1].nombre").value("Vivienda"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));

        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void devuelveListaVaciaSinRecursos() throws Exception {
        mvc.perform(get("/api/recursos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void excluyeRecursosInactivos() throws Exception {
        Organizacion organizacion = guardarOrganizacion("Asociacion");
        guardarRecurso("Inactivo", false, organizacion, Set.of());
        Recurso activo = guardarRecurso("Activo", true, organizacion, Set.of());

        mvc.perform(get("/api/recursos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(activo.getIdRecurso()))
                .andExpect(jsonPath("$[0].categorias").isEmpty());

        recursos.deleteById(activo.getIdRecurso());
        mvc.perform(get("/api/recursos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void ordenaPorNombreYDesempataPorIdentificador() throws Exception {
        Organizacion organizacion = guardarOrganizacion("Asociacion");
        Recurso ultimo = guardarRecurso("Zeta", true, organizacion, Set.of());
        Recurso primero = guardarRecurso("Ayuda", true, organizacion, Set.of());
        Recurso segundo = guardarRecurso("Ayuda", true, organizacion, Set.of());
        primero.setDireccion(null);
        primero.setTelefono(null);
        primero.setWeb(null);
        primero.setHorario(null);
        recursos.saveAndFlush(primero);

        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(get("/api/recursos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].id").value(primero.getIdRecurso()))
                    .andExpect(jsonPath("$[1].id").value(segundo.getIdRecurso()))
                    .andExpect(jsonPath("$[2].id").value(ultimo.getIdRecurso()))
                    .andExpect(jsonPath("$[0].categorias").isEmpty());
        }
    }

    @Test
    void noAbreLaEscrituraPublica() throws Exception {
        mvc.perform(post("/api/recursos").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private Organizacion guardarOrganizacion(String nombre) {
        Organizacion organizacion = new Organizacion();
        organizacion.setNombre(nombre);
        return organizaciones.saveAndFlush(organizacion);
    }

    private Categoria guardarCategoria(String nombre, String slug) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setSlug(slug);
        return categorias.saveAndFlush(categoria);
    }

    private Recurso guardarRecurso(String nombre, boolean activo, Organizacion organizacion,
                                   Set<Categoria> categoriasRecurso) {
        Recurso recurso = new Recurso();
        recurso.setNombre(nombre);
        recurso.setDescripcion("Descripcion de " + nombre);
        recurso.setDireccion("Calle de prueba 1");
        recurso.setTelefono("928123456");
        recurso.setWeb("https://example.invalid");
        recurso.setHorario("Lunes a viernes");
        recurso.setActivo(activo);
        recurso.setOrganizacion(organizacion);
        recurso.getCategorias().addAll(categoriasRecurso);
        return recursos.saveAndFlush(recurso);
    }
}
