package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.HabitoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Editar un hábito, con la aplicación ensamblada y la base de datos. Lo que
 * se comprueba aquí no se ve con mocks: que el ritmo se ancla con el hábito
 * como estaba —antes de que guardarlo le cambie los valores— y que el hábito
 * queda guardado con los nuevos.
 *
 * El ancla se pone en el "hoy" real para que no cierre ningún periodo: así
 * el resultado no depende del día en que se lance el test.
 */
@SpringBootTest
@Transactional
class CambiarHabitoTest {

    @Autowired
    private HabitoService habitoService;

    @PersistenceContext
    private EntityManager em;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private int usuarioId;
    private int habitoId;

    /** Un diario de una vez al día, creado hace un mes, con el ritmo en 0,8. */
    @BeforeEach
    void unDiarioConRitmo() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Cambio");
        usuario.setUsername("test-cambio-" + System.nanoTime());
        usuario.setEmail("test-cambio-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, hoy.minusDays(30));
        em.persist(habito);
        em.persist(new Racha(habito, hoy));
        Ritmo ritmo = new Ritmo(habito, hoy);
        ritmo.setValor(0.8);
        em.persist(ritmo);
        em.flush();
        em.clear();

        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();
    }

    /** El hábito tal como lo arma el controlador a partir del cuerpo de la petición. */
    private Habito editado(String nombre, Frecuencia frecuencia, int meta) {
        Habito habito = new Habito();
        habito.setHabitoId(habitoId);
        habito.setNombre(nombre);
        habito.setDescripcion("Descripción");
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setPropietario(em.find(Usuario.class, usuarioId));
        return habito;
    }

    private Ritmo ritmoGuardado() {
        em.flush();
        em.clear();
        return em.createQuery(
                        "SELECT r FROM Ritmo r WHERE r.habito.habitoId = :habitoId", Ritmo.class)
                .setParameter("habitoId", habitoId)
                .getSingleResult();
    }

    @Test
    void alSubirElObjetivo_elRitmoSeAnclaEnHoy_yElHabitoGuardaElNuevo() {
        habitoService.actualizar(editado("Leer", Frecuencia.DIARIO, 3), null);

        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
        assertEquals(hoy, ritmo.getCambio());
        assertEquals(3, em.find(Habito.class, habitoId).getMeta());
    }

    @Test
    void alCambiarLaFrecuencia_elRitmoSeConserva() {
        habitoService.actualizar(editado("Leer", Frecuencia.SEMANAL, 3), null);

        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
        assertEquals(hoy, ritmo.getCambio());
        assertEquals(Frecuencia.SEMANAL, em.find(Habito.class, habitoId).getFrecuencia());
    }

    @Test
    void alCambiarSoloElNombre_elRitmoNoSeToca() {
        habitoService.actualizar(editado("Leer cada noche", Frecuencia.DIARIO, 1), null);

        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
        assertNull(ritmo.getCambio());
        assertEquals("Leer cada noche", em.find(Habito.class, habitoId).getNombre());
    }
}
