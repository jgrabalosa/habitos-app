package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.ReversionLogro;
import com.norday.habitos.model.ReversionRegistro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LimpiadorHabitos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduce el DataIntegrityViolationException de fk_reversion_registro al
 * borrar un hábito con una ReversionRegistro colgando de sus registros.
 * Con mocks no se reproduce, porque la clave ajena no existe; hace falta la
 * aplicación ensamblada, y norday-server es el único módulo que la tiene.
 */
@SpringBootTest
@Transactional
class BorradoHabitoTest {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private LimpiadorHabitos limpiadorHabitos;

    @PersistenceContext
    private EntityManager em;

    @Test
    void eliminarHabitoConReversionPendienteNoRevientaPorFk() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Borrado");
        usuario.setUsername("test-borrado-" + System.nanoTime());
        usuario.setEmail("test-borrado-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Hábito de prueba", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.now());
        em.persist(habito);

        Registro registro = new Registro(habito, true, null, LocalDate.now());
        em.persist(registro);

        ReversionRegistro reversion = new ReversionRegistro(registro, 1, 1,
                LocalDate.now(), LocalDate.now(), null, null, 10);
        em.persist(reversion);

        ReversionLogro reversionLogro = new ReversionLogro(reversion, 1);
        reversion.getLogros().add(reversionLogro);

        em.flush();

        int habitoId = habito.getHabitoId();
        int registroId = registro.getRegistroId();

        // Sin este clear(), el contexto de persistencia sigue con `registro` y
        // `reversion` gestionados mientras eliminar() los borra por SQL en
        // bloque (JPQL bulk delete no sincroniza la caché de primer nivel), y
        // el flush posterior revienta al comprobar la referencia a un Habito
        // ya eliminado. Una petición real parte de un contexto limpio.
        em.clear();

        habitoService.eliminar(habitoId);
        em.flush();

        assertNull(em.find(Habito.class, habitoId));

        Long reversionesRestantes = em.createQuery(
                        "SELECT count(r) FROM ReversionRegistro r WHERE r.registro.registroId = :registroId",
                        Long.class)
                .setParameter("registroId", registroId)
                .getSingleResult();
        assertEquals(0L, reversionesRestantes);

        Long logrosRestantes = em.createQuery(
                        "SELECT count(rl) FROM ReversionLogro rl WHERE rl.reversion.registro.registroId = :registroId",
                        Long.class)
                .setParameter("registroId", registroId)
                .getSingleResult();
        assertEquals(0L, logrosRestantes);
    }

    private Usuario usuarioDePrueba(String prefijo) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test " + prefijo);
        usuario.setUsername(prefijo + "-" + System.nanoTime());
        usuario.setEmail(prefijo + "-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        return usuario;
    }

    private long ritmosDe(int habitoId) {
        return em.createQuery(
                        "SELECT count(r) FROM Ritmo r WHERE r.habito.habitoId = :habitoId", Long.class)
                .setParameter("habitoId", habitoId)
                .getSingleResult();
    }

    @Test
    void eliminarHabitoConRitmoNoRevientaPorFk() {
        Usuario usuario = usuarioDePrueba("test-ritmo-borrado");
        Habito habito = new Habito("Hábito con ritmo", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.now());
        em.persist(habito);
        em.persist(new Ritmo(habito, LocalDate.now()));
        em.flush();
        int habitoId = habito.getHabitoId();
        assertEquals(1L, ritmosDe(habitoId));
        em.clear();

        habitoService.eliminar(habitoId);
        em.flush();

        assertNull(em.find(Habito.class, habitoId));
        assertEquals(0L, ritmosDe(habitoId));
    }

    @Test
    void limpiarCuentaConRitmoNoRevientaPorFk() {
        Usuario usuario = usuarioDePrueba("test-ritmo-cuenta");
        Habito habito = new Habito("Hábito con ritmo", "Descripción", Frecuencia.SEMANAL, 3,
                usuario, null, LocalDate.now());
        em.persist(habito);
        em.persist(new Ritmo(habito, LocalDate.now()));
        em.flush();
        int habitoId = habito.getHabitoId();
        em.clear();

        limpiadorHabitos.limpiar(em.find(Usuario.class, usuario.getUsuarioId()));
        em.flush();

        assertNull(em.find(Habito.class, habitoId));
        assertEquals(0L, ritmosDe(habitoId));
    }
}
