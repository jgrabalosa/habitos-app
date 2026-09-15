package com.norday.gamificacion.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.MascotaDTO;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.IMascotaDAO;
import com.norday.gamificacion.service.LogroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MascotaService {

    @Autowired
    private IMascotaDAO mascotaDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private LogroService logroService;

    @Autowired
    private CumplimientoDiarioPort cumplimientoDiario;

    /** Subir a nivel N cuesta 15×(N-1) XP. */
    private int costoNivel(int nivel) {
        return 15 * (nivel - 1);
    }

    private int xpAcumuladoInicioNivel(int nivel) {
        int total = 0;
        for (int n = 2; n <= nivel; n++) {
            total += costoNivel(n);
        }
        return total;
    }

    private int calcularNivel(int xpTotal) {
        int nivel = 1;
        while (xpTotal >= xpAcumuladoInicioNivel(nivel + 1)) {
            nivel++;
        }
        return nivel;
    }

    /**
     * Devuelve un código, no un texto: el cliente lo traduce, igual que hace
     * con categorías, logros y productos. El servidor no elige idioma.
     */
    private String calcularFase(int nivel) {
        if (nivel <= 2) return "HUEVO";
        if (nivel <= 9) return "CRIA";
        return "ADULTO";
    }

    /** Orden de evolución. El índice es lo que hace comparables las fases. */
    private static final List<String> FASES = List.of("HUEVO", "CRIA", "ADULTO");

    /**
     * Si el usuario puede ver esa fase con el nivel que tiene ahora. Vale
     * cualquiera anterior o igual a la real: quien tiene la adulta puede
     * mostrar el huevo, y al revés no.
     *
     * Se comprueba también al leer, no sólo al guardar, porque el nivel puede
     * bajar: restaurarProgreso devuelve la experiencia a un valor anterior
     * cuando se deshace un completado. La elección se conserva en la BD, así
     * que si el usuario vuelve a subir recupera lo que había elegido.
     */
    private boolean faseDesbloqueada(String fase, int nivel) {
        if (fase == null) return false;
        int pedida = FASES.indexOf(fase);
        if (pedida < 0) return false;
        return pedida <= FASES.indexOf(calcularFase(nivel));
    }

    /**
     * El ánimo. "feliz" se pregunta al vuelo, para que no pueda quedarse
     * desfasado cuando algo deshace el día. Los otros dos sí son históricos:
     * miran cuánto hace que se cumplió por última vez.
     * Código, no texto: el cliente lo traduce.
     */
    private String calcularEstado(int usuarioId, LocalDate fechaUltimoDiaCompleto, ZoneId zona) {
        if (cumplimientoDiario.hoyCumplido(usuarioId)) return "feliz";
        if (fechaUltimoDiaCompleto == null) return "triste";
        long dias = ChronoUnit.DAYS.between(fechaUltimoDiaCompleto, LocalDate.now(zona));
        if (dias < 3) return "dormida";
        return "triste";
    }

    /** La zona del dueño de la mascota: su "hoy" no es el del servidor. */
    private ZoneId zonaDeMascota(Mascota mascota) {
        return zonaUsuarioService.zonaDe(mascota != null ? mascota.getUsuario() : null);
    }

    /** Fila creada perezosamente al primer acceso. */
    public Mascota obtenerOCrear(int usuarioId) {
        Mascota mascota = mascotaDAO.findByUsuarioId(usuarioId);
        if (mascota == null) {
            Usuario usuario = usuarioDAO.findById(usuarioId);
            mascota = new Mascota(usuario);
            mascotaDAO.save(mascota);
        }
        return mascota;
    }

    private MascotaDTO construirDTO(Mascota mascota) {
        int nivel = calcularNivel(mascota.getExperiencia());
        int xpInicioNivel = xpAcumuladoInicioNivel(nivel);
        // La elegida manda si sigue desbloqueada; si no, la que toca por nivel.
        String elegida = mascota.getFaseElegida();
        String faseAMostrar = faseDesbloqueada(elegida, nivel) ? elegida : calcularFase(nivel);
        return new MascotaDTO(
                mascota.getNombre(),
                mascota.getExperiencia(),
                nivel,
                mascota.getExperiencia() - xpInicioNivel,
                costoNivel(nivel + 1),
                faseAMostrar,
                calcularFase(nivel),
                calcularEstado(mascota.getUsuario().getUsuarioId(),
                        mascota.getFechaUltimoDiaCompleto(), zonaDeMascota(mascota)),
                mascota.getFechaUltimaComida()
        );
    }

    public MascotaDTO obtenerDTO(int usuarioId) {
        return construirDTO(obtenerOCrear(usuarioId));
    }

    /** El motor no conoce el origen de la XP: los disparadores (completar hábito, usar comida) sí. */
    public ResultadoExperienciaDTO ganarExperiencia(int usuarioId, int cantidad) {
        Mascota mascota = obtenerOCrear(usuarioId);
        int nivelAntes = calcularNivel(mascota.getExperiencia());

        mascota.setExperiencia(mascota.getExperiencia() + cantidad);
        mascotaDAO.update(mascota);

        int nivelDespues = calcularNivel(mascota.getExperiencia());
        boolean subioNivel = nivelDespues > nivelAntes;

        // La fase no se guarda: se deriva del nivel en calcularFase. Compararla
        // antes y después es lo que convierte un cálculo en un evento, y evita
        // cablear aquí los niveles 3 y 10 —si calcularFase cambia sus umbrales,
        // esto sigue siendo correcto sin tocarlo.
        //
        // El logro no viaja en ResultadoExperienciaDTO: aparecerá en la
        // Colección al refrescar. La CelebracionNivel ya salta con subioNivel,
        // así que el momento no se queda mudo.
        if (subioNivel) {
            String faseAntes = calcularFase(nivelAntes);
            String faseDespues = calcularFase(nivelDespues);
            if (!faseAntes.equals(faseDespues)) {
                // Al evolucionar se descarta la elección: se muestra la fase
                // nueva, que es como el usuario se entera de que ha cambiado.
                // Después puede volver a elegir la que prefiera.
                mascota.setFaseElegida(null);
                mascotaDAO.update(mascota);

                Usuario usuario = usuarioDAO.findById(usuarioId);
                if (usuario != null) {
                    if ("CRIA".equals(faseDespues)) {
                        logroService.otorgarSiNoTiene(usuario, "MASCOTA_CRIA");
                    } else if ("ADULTO".equals(faseDespues)) {
                        logroService.otorgarSiNoTiene(usuario, "MASCOTA_ADULTO");
                    }
                }
            }
        }

        return new ResultadoExperienciaDTO(subioNivel, nivelDespues, construirDTO(mascota));
    }

    public void ponerNombre(int usuarioId, String nuevoNombre) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setNombre(nuevoNombre);
        mascotaDAO.update(mascota);
    }

    /**
     * Guarda qué fase quiere ver el usuario. Sólo afecta a la imagen: no toca
     * XP, evolución ni logros. Se rechaza una fase que no exista o que aún no
     * esté desbloqueada — ver la adulta sin haberla ganado sería regalarla.
     */
    public void elegirFase(int usuarioId, String fase) {
        Mascota mascota = obtenerOCrear(usuarioId);
        if (!faseDesbloqueada(fase, calcularNivel(mascota.getExperiencia()))) {
            throw new IllegalArgumentException("Fase no válida o no desbloqueada");
        }
        mascota.setFaseElegida(fase);
        mascotaDAO.update(mascota);
    }

    /**
     * Marca hoy como día cumplido. Genérico a propósito: el motor no sabe qué
     * hace falta cumplir —eso lo decide cada app—, solo que se ha cumplido.
     * Idempotente: si ya estaba marcado hoy, no toca la BD.
     */
    public void registrarDiaCompleto(int usuarioId) {
        Mascota mascota = obtenerOCrear(usuarioId);
        LocalDate hoy = LocalDate.now(zonaDeMascota(mascota));
        if (hoy.equals(mascota.getFechaUltimoDiaCompleto())) return;
        mascota.setFechaUltimoDiaCompleto(hoy);
        mascotaDAO.update(mascota);
    }

    public void registrarComida(int usuarioId) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setFechaUltimaComida(LocalDate.now(zonaDeMascota(mascota)));
        mascotaDAO.update(mascota);
    }

    /**
     * Restaura el progreso a un estado anterior. Genérico a propósito: el
     * motor no sabe por qué se revierte, solo a qué valores volver.
     */
    public void restaurarProgreso(int usuarioId, int experiencia,
                                  LocalDate fechaUltimoDiaCompleto) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setExperiencia(experiencia);
        mascota.setFechaUltimoDiaCompleto(fechaUltimoDiaCompleto);
        mascotaDAO.update(mascota);
    }
}