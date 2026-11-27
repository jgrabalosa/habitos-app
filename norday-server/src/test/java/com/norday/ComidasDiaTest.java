package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.service.MascotaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Como mucho 3 comidas al día (T-077a), de punta a punta: el endpoint de usar
 * comida y GET /api/mascota con Postgres de verdad.
 *
 * El «día siguiente» se consigue con la zona del usuario: de Pago Pago
 * (UTC−11) a Kiritimati (UTC+14) hay 25 horas, así que la fecha siempre es
 * otra, sea la hora que sea cuando corre la prueba.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ComidasDiaTest {

    private static final String AYER = "Pacific/Pago_Pago";
    private static final String HOY = "Pacific/Kiritimati";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private MascotaService mascotaService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private final List<Integer> usuarios = new ArrayList<>();
    private int usuarioId;
    private int comidaId;

    @BeforeEach
    void usuarioConCincoComidas() {
        Producto comida = productoDAO.findByCodigo("COMIDA_BASICA");
        assertNotNull(comida, "el catálogo siembra COMIDA_BASICA al arrancar");
        comidaId = comida.getProductoId();
        usuarioId = tx.execute(estado -> {
            Usuario u = new Usuario();
            u.setNombre("Test Comidas");
            u.setUsername("test-comidas-" + System.nanoTime());
            u.setEmail("test-comidas-" + System.nanoTime() + "@example.com");
            u.setContrasena("contrasenaDePrueba");
            u.setProveedorAuth("LOCAL");
            u.setFechaRegistro(LocalDateTime.now());
            u.setIdioma("es");
            u.setZonaHoraria(AYER);
            em.persist(u);
            em.persist(new UsuarioProducto(u, em.find(Producto.class, comidaId), 5));
            return u.getUsuarioId();
        });
        usuarios.add(usuarioId);
        mascotaService.obtenerOCrear(usuarioId);
    }

    @AfterEach
    void borrar() {
        for (Integer id : usuarios) {
            usuarioService.eliminarCuenta(id);
        }
    }

    private String token() {
        return "Bearer " + jwtUtil.generateToken(usuarioId, "comidas@example.com");
    }

    private ResultActions comer() throws Exception {
        return mockMvc.perform(post("/api/gamificacion/productos/usar/" + usuarioId + "/" + comidaId)
                .header("Authorization", token()));
    }

    private ResultActions mascota() throws Exception {
        return mockMvc.perform(get("/api/mascota/" + usuarioId).header("Authorization", token()));
    }

    private int unidades() {
        return tx.execute(estado -> ((Number) em.createNativeQuery(
                "SELECT cantidad FROM usuario_producto WHERE usuario_id = :u AND producto_id = :p")
                .setParameter("u", usuarioId).setParameter("p", comidaId)
                .getSingleResult()).intValue());
    }

    private int xp() {
        return tx.execute(estado -> em.find(Mascota.class,
                mascotaService.obtenerOCrear(usuarioId).getMascotaId()).getExperiencia());
    }

    private void cambiarDeZona(String zona) {
        tx.executeWithoutResult(estado -> em.find(Usuario.class, usuarioId).setZonaHoraria(zona));
    }

    @Test
    void lasTresPrimerasDanQuinceXpYRestanUnaUnidad_laCuartaEsUn409SinGastarNada() throws Exception {
        for (int i = 1; i <= 3; i++) {
            comer().andExpect(status().isOk()).andExpect(jsonPath("$.xpGanada").value(15));
            assertEquals(5 - i, unidades());
            assertEquals(15 * i, xp());
        }

        comer().andExpect(status().isConflict());

        assertEquals(2, unidades(), "la cuarta no gasta la unidad");
        assertEquals(45, xp(), "la cuarta no da XP");
    }

    @Test
    void alDiaSiguiente_vuelveAPoderComer() throws Exception {
        for (int i = 0; i < 3; i++) comer().andExpect(status().isOk());
        comer().andExpect(status().isConflict());

        cambiarDeZona(HOY);

        comer().andExpect(status().isOk()).andExpect(jsonPath("$.xpGanada").value(15));
        assertEquals(1, unidades());
        assertEquals(60, xp());
        mascota().andExpect(jsonPath("$.comidasHoy").value(1));
    }

    @Test
    void laMascotaDaLasComidasDeHoyYElMaximo() throws Exception {
        mascota().andExpect(status().isOk())
                .andExpect(jsonPath("$.comidasHoy").value(0))
                .andExpect(jsonPath("$.comidasMaxDia").value(3));
        for (int i = 1; i <= 3; i++) {
            comer().andExpect(status().isOk());
            mascota().andExpect(jsonPath("$.comidasHoy").value(i))
                    .andExpect(jsonPath("$.comidasMaxDia").value(3));
        }
    }

    @Test
    void lasComidasDeOtroDia_noCuentanHoy() throws Exception {
        for (int i = 0; i < 3; i++) comer().andExpect(status().isOk());

        cambiarDeZona(HOY);

        mascota().andExpect(jsonPath("$.comidasHoy").value(0))
                .andExpect(jsonPath("$.comidasMaxDia").value(3));
    }

    /**
     * La V35, ejecutada tal cual está en el fichero sobre una tabla con filas:
     * las que ya existen quedan a 0. En los tests la columna la crea Hibernate,
     * así que se quita antes, como estaba antes de la V35.
     */
    @Test
    void laV35_dejaLasFilasQueYaExistian_aCero() throws Exception {
        tx.executeWithoutResult(estado -> {
            em.createNativeQuery("UPDATE mascota SET comidas_dia = 2 WHERE usuario_id = :u")
                    .setParameter("u", usuarioId).executeUpdate();
            em.createNativeQuery("ALTER TABLE mascota DROP COLUMN comidas_dia").executeUpdate();
        });
        try {
            String sql = new ClassPathResource("db/migration/V35__comidas_dia.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            List<String> sentencias = Arrays.stream(sql.replaceAll("(?m)^--.*$", "").split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            assertEquals(1, sentencias.size());
            tx.executeWithoutResult(estado -> sentencias.forEach(
                    s -> em.createNativeQuery(s).executeUpdate()));

            Number comidas = tx.execute(estado -> (Number) em.createNativeQuery(
                    "SELECT comidas_dia FROM mascota WHERE usuario_id = :u")
                    .setParameter("u", usuarioId).getSingleResult());
            assertEquals(0, comidas.intValue());
        } finally {
            // Si la migración no llegó a crear la columna, que el resto de la suite no la eche de menos.
            tx.executeWithoutResult(estado -> em.createNativeQuery(
                    "ALTER TABLE mascota ADD COLUMN IF NOT EXISTS comidas_dia INTEGER NOT NULL DEFAULT 0")
                    .executeUpdate());
        }
    }
}
