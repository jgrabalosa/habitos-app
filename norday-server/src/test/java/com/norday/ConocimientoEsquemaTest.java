package com.norday;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.Fuente;
import com.norday.conocimiento.model.LecturaPildora;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraTarjeta;
import com.norday.conocimiento.model.RespuestaReto;
import com.norday.conocimiento.model.Reto;
import com.norday.conocimiento.model.Temario;
import com.norday.conocimiento.model.TipoFuente;
import com.norday.conocimiento.model.UsuarioReto;
import com.norday.conocimiento.repository.ILecturaPildoraDAO;
import com.norday.conocimiento.repository.IRespuestaRetoDAO;
import com.norday.conocimiento.repository.IUsuarioRetoDAO;
import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.UsuarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El esquema del producto mínimo de Conocimiento (V37-V39) contra Postgres de
 * verdad: que las entidades se guardan y se releen, que las consultas de los
 * DAO nuevos filtran y ordenan lo que tienen que filtrar y ordenar, y que la
 * baja y la exportación de la cuenta alcanzan las tablas nuevas, con sus FK
 * reales.
 *
 * Vive en norday-server por lo mismo que PildoraDAOTest: necesita la
 * aplicación ensamblada. El @Transactional deshace todo al terminar.
 *
 * Los tests no ejecutan Flyway (el esquema lo genera Hibernate), así que esto
 * prueba las entidades, no las migraciones. Que las migraciones cuadran con
 * las entidades se comprobó aparte, aplicando V37-V40 sobre el esquema de main
 * y arrancando con ddl-auto=validate (ver el PR).
 */
@SpringBootTest
@Transactional
class ConocimientoEsquemaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 21, 7, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();

    @Autowired
    private IUsuarioRetoDAO usuarioRetoDAO;

    @Autowired
    private ILecturaPildoraDAO lecturaPildoraDAO;

    @Autowired
    private IRespuestaRetoDAO respuestaRetoDAO;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ExportacionDatosService exportacionDatosService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private Usuario otro;
    private Pildora pildora;

    @BeforeEach
    void prepararDatos() {
        usuario = nuevoUsuario("a");
        otro = nuevoUsuario("b");

        String sufijo = String.valueOf(System.nanoTime());
        Categoria rama = new Categoria("RAMA_TEST_" + sufijo, "Rama de prueba", null, null, null, 1);
        em.persist(rama);
        Temario temario = new Temario("temario-test-" + sufijo, rama, "Luz y tiempo", null, 1);
        em.persist(temario);
        Fuente libro = new Fuente("fuente-test-" + sufijo, TipoFuente.LIBRO, "Cosmos", "Carl Sagan");
        libro.setAnio(1980);
        em.persist(libro);

        pildora = new Pildora("El Sol que ves es de hace ocho minutos",
                "El Sol que ves ahora mismo es el de hace unos ocho minutos.",
                "Tarjeta uno\n\nTarjeta dos", "Carl Sagan, Cosmos", null, true, AHORA);
        pildora.setCodigo("pildora-test-" + sufijo);
        pildora.setTemario(temario);
        pildora.setOrdenTemario(1);
        pildora.setFuente(libro);
        pildora.setLicencia("texto-propio");
        em.persist(pildora);
        em.persist(new PildoraTarjeta(pildora, 1, "Tarjeta uno"));
        em.persist(new PildoraTarjeta(pildora, 2, "Tarjeta dos"));
    }

    @Test
    void lasOpcionesDeUnRetoVuelvenEnSuOrden() {
        Reto reto = reto("orden", true);
        em.flush();
        em.clear();

        Reto releido = em.find(Reto.class, reto.getRetoId());

        assertEquals(List.of("Al instante", "A los ocho minutos, más o menos", "Al día siguiente"),
                releido.getOpciones());
        assertTrue(releido.esCorrecta(1));
        assertFalse(releido.esCorrecta(0));
    }

    @Test
    void laPildoraGuardaSusColumnasNuevasYSusAsociaciones() {
        em.flush();
        em.clear();

        Pildora releida = em.find(Pildora.class, pildora.getPildoraId());

        assertEquals(1, releida.getNivel());
        assertEquals(1, releida.getVersion());
        assertEquals(1, releida.getOrdenTemario());
        assertEquals("texto-propio", releida.getLicencia());
        assertEquals("Luz y tiempo", releida.getTemario().getTitulo());
        assertEquals("Cosmos", releida.getFuente().getTitulo());
    }

    @Test
    void laBandejaSonLosRetosVencidosDelMasAntiguoAlMasNuevo() {
        UsuarioReto ayer = repaso(usuario, reto("ayer", true), AHORA.minusDays(1));
        UsuarioReto haceUnaSemana = repaso(usuario, reto("semana", true), AHORA.minusDays(7));
        UsuarioReto justoAhora = repaso(usuario, reto("ahora", true), AHORA);
        repaso(usuario, reto("manana", true), AHORA.plusDays(1));      // aún no vence
        repaso(usuario, reto("retirado", false), AHORA.minusDays(3));  // retirado del repo
        repaso(otro, reto("de-otro", true), AHORA.minusDays(2));       // de otro usuario
        em.flush();

        List<UsuarioReto> bandeja = usuarioRetoDAO.findVencidos(usuario.getUsuarioId(), AHORA, 10);

        assertEquals(List.of(haceUnaSemana.getId(), ayer.getId(), justoAhora.getId()),
                bandeja.stream().map(UsuarioReto::getId).toList());
        assertEquals(3, usuarioRetoDAO.contarVencidos(usuario.getUsuarioId(), AHORA));
        assertEquals(List.of(haceUnaSemana.getId(), ayer.getId()),
                usuarioRetoDAO.findVencidos(usuario.getUsuarioId(), AHORA, 2).stream()
                        .map(UsuarioReto::getId).toList());
    }

    @Test
    void lasLecturasYLasRespuestasSeCuentanPorDia() {
        Reto reto = reto("dia", true);
        em.persist(new LecturaPildora(usuario, pildora, UUID.randomUUID(), HOY, AHORA));
        em.persist(new LecturaPildora(usuario, pildora, UUID.randomUUID(), HOY.minusDays(1), AHORA.minusDays(1)));
        em.persist(new LecturaPildora(otro, pildora, UUID.randomUUID(), HOY, AHORA));
        em.persist(new RespuestaReto(usuario, reto, UUID.randomUUID(), 1, true, HOY, AHORA));
        em.persist(new RespuestaReto(usuario, reto, UUID.randomUUID(), 0, false, HOY, AHORA.plusMinutes(1)));
        em.persist(new RespuestaReto(usuario, reto, UUID.randomUUID(), 1, true, HOY.minusDays(1), AHORA.minusDays(1)));
        em.flush();

        assertEquals(1, lecturaPildoraDAO.findByUsuarioYDia(usuario.getUsuarioId(), HOY).size());
        List<RespuestaReto> deHoy = respuestaRetoDAO.findByUsuarioYDia(usuario.getUsuarioId(), HOY);
        assertEquals(2, deHoy.size());
        assertTrue(deHoy.get(0).isAcierto());
        assertFalse(deHoy.get(1).isAcierto());
    }

    /**
     * La idempotencia la garantiza la base de datos, no la lógica. Con ids
     * IDENTITY el INSERT sale en el propio persist.
     */
    @Test
    void dosLecturasConLaMismaClaveNoCaben() {
        UUID clave = UUID.randomUUID();
        em.persist(new LecturaPildora(usuario, pildora, clave, HOY, AHORA));

        ConstraintViolationException error = assertThrows(ConstraintViolationException.class,
                () -> em.persist(new LecturaPildora(usuario, pildora, clave, HOY, AHORA)));
        assertEquals("23505", error.getSQLState());
    }

    @Test
    void laBajaDeLaCuentaBorraLecturasRepasosYRespuestas() {
        Reto reto = reto("baja", true);
        em.persist(new LecturaPildora(usuario, pildora, UUID.randomUUID(), HOY, AHORA));
        repaso(usuario, reto, AHORA);
        em.persist(new RespuestaReto(usuario, reto, UUID.randomUUID(), 1, true, HOY, AHORA));
        em.persist(new LecturaPildora(otro, pildora, UUID.randomUUID(), HOY, AHORA));
        em.flush();
        // Los limpiadores borran con DELETE en bloque, que no saca de la
        // sesión las entidades ya cargadas: sin vaciarla, el flush siguiente
        // vería lecturas que apuntan a un usuario borrado.
        em.clear();
        int id = usuario.getUsuarioId();

        usuarioService.eliminarCuenta(id);
        em.flush();

        assertEquals(0, filasDe("LecturaPildora", id));
        assertEquals(0, filasDe("UsuarioReto", id));
        assertEquals(0, filasDe("RespuestaReto", id));
        assertEquals(1, filasDe("LecturaPildora", otro.getUsuarioId()));
        // El contenido no es del usuario: sigue ahí.
        assertNotNull(em.find(Reto.class, reto.getRetoId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void laExportacionLlevaLecturasRepasosYRespuestas() {
        Reto reto = reto("exportar", true);
        em.persist(new LecturaPildora(usuario, pildora, UUID.randomUUID(), HOY, AHORA));
        repaso(usuario, reto, AHORA);
        em.persist(new RespuestaReto(usuario, reto, UUID.randomUUID(), 1, true, HOY, AHORA));
        em.flush();
        em.clear();

        Map<String, Object> conocimiento = (Map<String, Object>)
                exportacionDatosService.exportar(usuario.getUsuarioId()).get("conocimiento");

        List<Map<String, Object>> lecturas = (List<Map<String, Object>>) conocimiento.get("lecturas");
        List<Map<String, Object>> repasos = (List<Map<String, Object>>) conocimiento.get("repasos");
        List<Map<String, Object>> respuestas = (List<Map<String, Object>>) conocimiento.get("respuestas");
        assertEquals(1, lecturas.size());
        assertEquals("El Sol que ves es de hace ocho minutos", lecturas.get(0).get("titulo"));
        assertEquals(HOY.toString(), lecturas.get(0).get("dia"));
        assertEquals(1, repasos.size());
        assertEquals("Si el Sol desapareciera ahora mismo, ¿cuándo lo notarías?", repasos.get(0).get("enunciado"));
        assertEquals(1, respuestas.size());
        assertEquals(true, respuestas.get(0).get("acierto"));
        assertFalse(lecturas.get(0).containsKey("clave"));
        assertFalse(respuestas.get(0).containsKey("clave"));
    }

    // --- datos ---

    private Usuario nuevoUsuario(String marca) {
        Usuario u = new Usuario();
        u.setNombre("Test Conocimiento");
        u.setUsername("test-cono-" + marca + "-" + System.nanoTime());
        u.setEmail("test-cono-" + marca + "-" + System.nanoTime() + "@example.com");
        u.setContrasena("contrasenaDePrueba"); // Usuario valida mínimo 6
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        return u;
    }

    private Reto reto(String marca, boolean activo) {
        Reto reto = new Reto(pildora.getCodigo() + "-" + marca, pildora,
                "Si el Sol desapareciera ahora mismo, ¿cuándo lo notarías?",
                List.of("Al instante", "A los ocho minutos, más o menos", "Al día siguiente"),
                1, "La luz no llega al instante.");
        reto.setActivo(activo);
        em.persist(reto);
        return reto;
    }

    private UsuarioReto repaso(Usuario quien, Reto reto, LocalDateTime venceEn) {
        UsuarioReto repaso = new UsuarioReto(quien, reto, venceEn);
        em.persist(repaso);
        return repaso;
    }

    private long filasDe(String entidad, int usuarioId) {
        return em.createQuery("SELECT COUNT(x) FROM " + entidad + " x WHERE x.usuario.usuarioId = :id", Long.class)
                .setParameter("id", usuarioId)
                .getSingleResult();
    }
}
