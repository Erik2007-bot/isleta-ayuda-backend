package com.isletaayuda.backend;

import com.isletaayuda.backend.model.Categoria;
import com.isletaayuda.backend.model.Organizacion;
import com.isletaayuda.backend.model.Recurso;
import com.isletaayuda.backend.model.Rol;
import com.isletaayuda.backend.model.Usuario;
import com.isletaayuda.backend.repository.CategoriaRepository;
import com.isletaayuda.backend.repository.OrganizacionRepository;
import com.isletaayuda.backend.repository.RecursoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJpaTest(properties = "spring.config.location=classpath:persistence-test.properties")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenciaJpaTests {

    @Autowired
    private OrganizacionRepository organizaciones;

    @Autowired
    private RecursoRepository recursos;

    @Autowired
    private CategoriaRepository categorias;

    @Autowired
    private EntityManager entityManager;

    private Integer idOrganizacion;
    private Integer idRecurso;
    private Integer idCategoria;
    private Integer idSegundaCategoria;

    @BeforeEach
    void guardarDatosFicticios() {
        Organizacion organizacion = new Organizacion();
        organizacion.setNombre("Asociacion ficticia de pruebas");
        idOrganizacion = organizaciones.saveAndFlush(organizacion).getIdOrganizacion();

        Categoria alimentacion = guardarCategoria("Alimentacion de prueba", "alimentacion-prueba");
        Categoria vivienda = guardarCategoria("Vivienda de prueba", "vivienda-prueba");
        idCategoria = alimentacion.getIdCategoria();
        idSegundaCategoria = vivienda.getIdCategoria();

        Recurso recurso = new Recurso();
        recurso.setNombre("Recurso ficticio");
        recurso.setDescripcion("Descripcion del recurso de prueba");
        recurso.setActivo(true);
        recurso.setOrganizacion(organizacion);
        recurso.getCategorias().add(alimentacion);
        recurso.getCategorias().add(vivienda);
        idRecurso = recursos.saveAndFlush(recurso).getIdRecurso();
        entityManager.clear();
    }

    @Test
    void recuperaOrganizacionYDosCategoriasDesdeLaBaseDeDatos() {
        Recurso recuperado = recursos.findById(idRecurso).orElseThrow();

        assertThat(idOrganizacion).isPositive();
        assertThat(idRecurso).isPositive();
        assertThat(recuperado.getNombre()).isEqualTo("Recurso ficticio");
        assertThat(recuperado.isActivo()).isTrue();
        assertThat(recuperado.getOrganizacion().getIdOrganizacion()).isEqualTo(idOrganizacion);
        assertThat(recuperado.getOrganizacion().getNombre()).isEqualTo("Asociacion ficticia de pruebas");
        assertThat(recuperado.getCategorias()).extracting(Categoria::getIdCategoria)
                .containsExactlyInAnyOrder(idCategoria, idSegundaCategoria);
        assertThat(recuperado.getCategorias()).extracting(Categoria::getSlug)
                .containsExactlyInAnyOrder("alimentacion-prueba", "vivienda-prueba");
    }

    @ParameterizedTest
    @CsvSource({
            "'update recurso set id_organizacion = -1 where id_recurso = %1$d', 23506",
            "'insert into recurso_categoria (id_recurso, id_categoria) values (-1, %2$d)', 23506",
            "'insert into recurso_categoria (id_categoria, id_recurso) values (-1, %1$d)', 23506",
            "'update recurso set id_organizacion = null where id_recurso = %1$d', 23502"
    })
    void rechazaClavesForaneasInvalidasYOrganizacionNula(String sql, String estadoSql) {
        comprobarRestriccion(sql.formatted(idRecurso, idCategoria), estadoSql);
    }

    @Test
    void rechazaAsociacionRecursoCategoriaDuplicada() {
        comprobarRestriccion("insert into recurso_categoria (id_recurso, id_categoria) values (%d, %d)"
                .formatted(idRecurso, idCategoria), "23505");
    }

    @ParameterizedTest
    @CsvSource({
            "Alimentacion de prueba, slug-distinto",
            "Nombre distinto, alimentacion-prueba"
    })
    void rechazaNombreOSlugDeCategoriaDuplicados(String nombre, String slug) {
        Throwable error = catchThrowable(() -> guardarCategoria(nombre, slug));
        assertThat(error).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(error).hasCauseInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rechazaEmailDeUsuarioDuplicado() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario ficticio");
        usuario.setEmail("prueba@example.invalid");
        usuario.setPasswordHash("hash-ficticio");
        usuario.setRol(Rol.USER);
        usuario.setFechaRegistro(LocalDateTime.of(2026, 1, 1, 12, 0));
        entityManager.persist(usuario);
        entityManager.flush();

        comprobarRestriccion("insert into usuario (nombre, email, password_hash, rol, fecha_registro) "
                + "values ('Otro usuario', 'prueba@example.invalid', 'otro-hash', 'USER', CURRENT_TIMESTAMP)",
                "23505");
    }

    private Categoria guardarCategoria(String nombre, String slug) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setSlug(slug);
        return categorias.saveAndFlush(categoria);
    }

    private void comprobarRestriccion(String sql, String estadoSql) {
        Throwable error = catchThrowable(() -> entityManager.createNativeQuery(sql).executeUpdate());
        assertThat(error).isInstanceOf(PersistenceException.class);
        assertThat(error).isInstanceOf(ConstraintViolationException.class);
        assertThat(((ConstraintViolationException) error).getSQLState()).isEqualTo(estadoSql);
    }
}
