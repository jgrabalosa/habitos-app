package com.norday;

import com.norday.core.model.Usuario;
import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La precedencia del paso 3 al apuntar el resultado de un aporte, con el
 * UPDATE nativo contra Postgres: PENDIENTE lo acepta todo; después, ACEPTADO
 * gana a REPETIDO y a RECHAZADO, y REPETIDO gana a RECHAZADO. Lo que pierde
 * no escribe nada. Importa cuando dos reintentos de la misma sesión llegan a
 * la vez y responden distinto: el que pagó tiene que quedar apuntado.
 */
@SpringBootTest
@Transactional
class GuardarAporteTest {

    @Autowired private ISesionEnfoqueDAO sesionDAO;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private int segundos;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setNombre("Test Guardar Aporte");
        usuario.setUsername("test-ga-" + System.nanoTime());
        usuario.setEmail("test-ga-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        em.flush();
    }

    /** Una sesión con destino y el aporte en {@code estado}. */
    private int sesionCon(EstadoAporte estado) {
        SesionEnfoque s = new SesionEnfoque();
        s.setUsuario(usuario);
        s.setClave(UUID.randomUUID());
        // Cada una en su hueco: aquí no se mira el solape, pero así no se pisan.
        s.setInicioUtc(LocalDateTime.of(2026, 10, 9, 6, 0).plusSeconds(segundos += 3600));
        s.setDuracionSeg(1500);
        s.setDuracionObjetivoMin(25);
        s.setEstado(EstadoSesion.COMPLETADA);
        s.setDia(LocalDate.of(2026, 10, 9));
        s.setDestinoTipo("habito");
        s.setDestinoRef(1L);
        s.setAporteEstado(estado);
        s.setAporteMotivo(estado == EstadoAporte.RECHAZADO ? "aporte.habito.pausado" : null);
        s.setMinutosAportados(estado.minutosEnDestino() ? 25 : 0);
        s.setCreadoEn(LocalDateTime.of(2026, 10, 9, 8, 0));
        return sesionDAO.insertarSiClaveLibre(s);
    }

    private void caso(EstadoAporte antes, EstadoAporte nuevo, boolean escribe) {
        int id = sesionCon(antes);
        String motivo = nuevo == EstadoAporte.RECHAZADO ? "aporte.habito.conflicto" : null;
        int minutos = nuevo.minutosEnDestino() ? 25 : 0;
        int monedas = nuevo == EstadoAporte.ACEPTADO ? 20 : 0;
        String logros = nuevo == EstadoAporte.ACEPTADO ? "PRIMEROS_PASOS" : null;
        boolean escrito = sesionDAO.guardarAporte(id, nuevo, motivo, minutos, monedas, monedas / 5, logros);
        SesionEnfoque leida = sesionDAO.releer(id);
        String caso = antes + " → " + nuevo;
        assertEquals(escribe, escrito, caso);
        assertEquals(escribe ? monedas : 0, leida.getMonedasAportadas(), caso);
        assertEquals(escribe ? monedas / 5 : 0, leida.getXpAportada(), caso);
        assertEquals(escribe ? logros : null, leida.getLogrosAportados(), caso);
        assertEquals(escribe ? minutos : (antes.minutosEnDestino() ? 25 : 0), leida.getMinutosAportados(), caso);
        assertEquals(escribe ? nuevo : antes, leida.getAporteEstado(), caso);
        assertEquals(escribe ? motivo : (antes == EstadoAporte.RECHAZADO ? "aporte.habito.pausado" : null),
                leida.getAporteMotivo(), caso);
    }

    @Test
    void pendiente_loAceptaTodo() {
        caso(EstadoAporte.PENDIENTE, EstadoAporte.ACEPTADO, true);
        caso(EstadoAporte.PENDIENTE, EstadoAporte.REPETIDO, true);
        caso(EstadoAporte.PENDIENTE, EstadoAporte.RECHAZADO, true);
    }

    @Test
    void aceptado_ganaARepetidoYARechazado() {
        caso(EstadoAporte.REPETIDO, EstadoAporte.ACEPTADO, true);
        caso(EstadoAporte.RECHAZADO, EstadoAporte.ACEPTADO, true);
        caso(EstadoAporte.ACEPTADO, EstadoAporte.REPETIDO, false);
        caso(EstadoAporte.ACEPTADO, EstadoAporte.RECHAZADO, false);
        caso(EstadoAporte.ACEPTADO, EstadoAporte.ACEPTADO, false);
    }

    @Test
    void repetido_ganaARechazado() {
        caso(EstadoAporte.RECHAZADO, EstadoAporte.REPETIDO, true);
        caso(EstadoAporte.REPETIDO, EstadoAporte.RECHAZADO, false);
        caso(EstadoAporte.REPETIDO, EstadoAporte.REPETIDO, false);
        caso(EstadoAporte.RECHAZADO, EstadoAporte.RECHAZADO, false);
    }

    @Test
    void sinDestino_noSeToca() {
        caso(EstadoAporte.SIN_DESTINO, EstadoAporte.ACEPTADO, false);
    }
}
