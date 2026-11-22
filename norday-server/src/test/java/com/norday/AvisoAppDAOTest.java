package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.service.ExportadorHabitos;
import com.norday.habitos.service.LimpiadorHabitos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las consultas de aviso_app contra Postgres de verdad (con mocks no se valida
 * el JPQL), y la baja y la exportación sobre filas reales. Vive en
 * norday-server por lo mismo que UsuarioProductoDAOTest: necesita la aplicación
 * ensamblada. El @Transactional deshace todo al terminar.
 */
@SpringBootTest
@Transactional
class AvisoAppDAOTest {

    @Autowired
    private IAvisoAppDAO avisoAppDAO;

    @Autowired
    private LimpiadorHabitos limpiadorHabitos;

    @Autowired
    private ExportadorHabitos exportadorHabitos;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private Usuario otro;

    @BeforeEach
    void prepararUsuarios() {
        usuario = nuevoUsuario("aviso-uno");
        otro = nuevoUsuario("aviso-otro");
    }

    private Usuario nuevoUsuario(String prefijo) {
        Usuario u = new Usuario();
        u.setNombre("Test " + prefijo);
        u.setUsername(prefijo + "-" + System.nanoTime());
        u.setEmail(prefijo + "-" + System.nanoTime() + "@example.com");
        u.setContrasena("contrasenaDePrueba");
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        return u;
    }

    private AvisoApp aviso(Usuario u, TipoAviso tipo, int variante, LocalDateTime enviadoEn) {
        AvisoApp a = new AvisoApp(u, tipo, variante);
        a.setEnviadoEn(enviadoEn);
        return avisoAppDAO.save(a);
    }

    @Test
    void guardar_rellenaElSelloEnUtcSiNoSePone() {
        AvisoApp guardado = avisoAppDAO.save(new AvisoApp(usuario, TipoAviso.VUELTA_1, 0));
        em.flush();

        assertTrue(guardado.getId() > 0);
        LocalDateTime ahoraUtc = LocalDateTime.now(ZoneOffset.UTC);
        assertTrue(guardado.getEnviadoEn().isAfter(ahoraUtc.minusMinutes(1))
                        && !guardado.getEnviadoEn().isAfter(ahoraUtc.plusMinutes(1)),
                "enviado_en es la hora UTC de ahora");
    }

    @Test
    void findByUsuario_devuelveSoloLosDelUsuario_delMasRecienteAlMasAntiguo() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 9, 0);
        aviso(usuario, TipoAviso.VUELTA_1, 0, base);
        aviso(usuario, TipoAviso.MOMENTO_DEBIL, 3, base.plusDays(2));
        aviso(usuario, TipoAviso.VUELTA_2, 1, base.plusDays(1));
        aviso(otro, TipoAviso.VUELTA_1, 0, base.plusDays(5));
        em.flush();
        em.clear();

        List<AvisoApp> avisos = avisoAppDAO.findByUsuario(usuario.getUsuarioId());

        assertEquals(List.of(TipoAviso.MOMENTO_DEBIL, TipoAviso.VUELTA_2, TipoAviso.VUELTA_1),
                avisos.stream().map(AvisoApp::getTipo).toList());
        assertEquals(3, avisos.get(0).getVariante());
    }

    @Test
    void findByUsuarioDesde_incluyeElLimiteYFiltraPorUsuario() {
        LocalDateTime limite = LocalDateTime.of(2026, 11, 10, 12, 0);
        aviso(usuario, TipoAviso.VUELTA_1, 0, limite.minusSeconds(1));
        aviso(usuario, TipoAviso.VUELTA_2, 0, limite);
        aviso(usuario, TipoAviso.MOMENTO_DEBIL, 1, limite.plusHours(3));
        aviso(otro, TipoAviso.MOMENTO_DEBIL, 1, limite.plusHours(4));
        em.flush();
        em.clear();

        List<AvisoApp> avisos = avisoAppDAO.findByUsuarioDesde(usuario.getUsuarioId(), limite);

        assertEquals(List.of(TipoAviso.MOMENTO_DEBIL, TipoAviso.VUELTA_2),
                avisos.stream().map(AvisoApp::getTipo).toList());
    }

    @Test
    void elTipoSeGuardaPorNombre() {
        aviso(usuario, TipoAviso.MOMENTO_DEBIL, 0, LocalDateTime.of(2026, 11, 1, 9, 0));
        em.flush();

        Object tipo = em.createNativeQuery(
                        "SELECT tipo FROM aviso_app WHERE usuario_id = :id")
                .setParameter("id", usuario.getUsuarioId())
                .getSingleResult();

        assertEquals("MOMENTO_DEBIL", tipo);
    }

    @Test
    void limpiador_borraLosAvisosDelUsuarioYNoLosDeOtro() {
        aviso(usuario, TipoAviso.VUELTA_1, 0, LocalDateTime.of(2026, 11, 1, 9, 0));
        aviso(usuario, TipoAviso.MOMENTO_DEBIL, 1, LocalDateTime.of(2026, 11, 2, 9, 0));
        aviso(otro, TipoAviso.VUELTA_1, 0, LocalDateTime.of(2026, 11, 1, 9, 0));
        em.flush();
        em.clear();

        limpiadorHabitos.limpiar(usuario);
        em.flush();
        em.clear();

        assertEquals(0, avisoAppDAO.findByUsuario(usuario.getUsuarioId()).size());
        assertEquals(1, avisoAppDAO.findByUsuario(otro.getUsuarioId()).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportador_incluyeLosAvisosDelUsuario() {
        aviso(usuario, TipoAviso.VUELTA_2, 4, LocalDateTime.of(2026, 11, 1, 9, 5));
        aviso(otro, TipoAviso.VUELTA_1, 0, LocalDateTime.of(2026, 11, 1, 9, 0));
        em.flush();
        em.clear();

        Map<String, Object> datos = exportadorHabitos.exportar(usuario);

        assertEquals(List.of(Map.of("tipo", "VUELTA_2", "variante", 4,
                        "enviadoEn", "2026-11-01T09:05")),
                (List<Map<String, Object>>) datos.get("avisosApp"));
    }
}
