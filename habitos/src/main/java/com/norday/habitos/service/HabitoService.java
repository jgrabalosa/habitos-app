package com.norday.habitos.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.habitos.model.CalculoObjetivo;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.dto.DashboardHabitoDTO;
import com.norday.habitos.model.dto.DiaSemanaDTO;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.model.dto.HabitoDiaDTO;
import com.norday.habitos.model.dto.HabitoFlexibleDTO;
import com.norday.habitos.model.dto.HabitoResumenDTO;
import com.norday.habitos.model.dto.ObjetivoProgramadoDTO;
import com.norday.habitos.model.dto.RegistroDiaDTO;
import com.norday.habitos.model.dto.RegistroResumenDTO;
import com.norday.habitos.model.dto.SemanaDashboardDTO;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Service
public class HabitoService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private IRitmoDAO ritmoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IReversionRegistroDAO reversionRegistroDAO;

    @Autowired
    private IHitoPagadoDAO hitoPagadoDAO;

    @Autowired
    private IHabitoObjetivoDAO objetivoDAO;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private com.norday.core.service.ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    /** Normaliza la relación frecuencia/díasSemana/meta antes de guardar:
     *  los días solo aplican a SEMANAL, y si hay días la meta se deriva de ellos. */
    private void normalizarPlanificacion(Habito habito) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) {
            habito.setDiasSemana(null);
            return;
        }
        String dias = habito.getDiasSemana();
        if (dias != null && !dias.isBlank()) {
            habito.setMeta((int) java.util.Arrays.stream(dias.split(","))
                    .filter(d -> !d.isBlank()).count());
        }
    }

    /**
     * Resuelve la categoría que pide el cliente. Solo valen las globales y
     * las del propio propietario. Una ajena responde lo mismo que una que no
     * existe, para no revelar qué ids hay.
     */
    private Categoria resolverCategoria(Integer categoriaId, Usuario propietario) {
        if (categoriaId == null) {
            return null;
        }
        Categoria categoria = categoriaDAO.findById(categoriaId);
        boolean permitida = categoria != null
                && (categoria.isEsGlobal()
                    || (categoria.getCreador() != null
                        && propietario != null
                        && categoria.getCreador().getUsuarioId() == propietario.getUsuarioId()));
        if (!permitida) {
            throw new RecursoNoEncontradoException("Categoría no encontrada");
        }
        return categoria;
    }

    @Transactional
    public List<String> crearHabito(Habito habito, Integer categoriaId) {
        habito.setTipo(resolverCategoria(categoriaId, habito.getPropietario()));
        normalizarPlanificacion(habito);
        LocalDate hoy = LocalDate.now(rachaService.zonaDe(habito));
        habito.setFechaInicio(hoy);
        habito.setActivo(true);
        habitoDAO.save(habito);
        objetivoService.crearInicial(habito);
        Racha racha = new Racha(habito, hoy);
        rachaDAO.save(racha);
        ritmoDAO.save(new Ritmo(habito, hoy));

        return logrosHabitosService.evaluarTrasCrearHabito(habito.getPropietario());
    }

    public Habito buscarPorId(int id) {
        return habitoDAO.findById(id);
    }

    /**
     * Pone al día el objetivo que guarda el hábito, por si ha llegado la
     * fecha de un cambio programado (ver {@link ObjetivoService#ponerAlDia}).
     * Para las lecturas de un hábito suelto que devuelven su objetivo.
     */
    @Transactional
    public void ponerObjetivoAlDia(Habito habito) {
        objetivoService.ponerAlDia(List.of(habito), rachaService.hoyDe(habito));
    }

    @Transactional
    public List<HabitoResumenDTO> obtenerResumen(Usuario propietario) {
        List<Habito> todos = habitoDAO.findByPropietario(propietario);
        objetivoService.ponerAlDia(todos, LocalDate.now(zonaUsuarioService.zonaDe(propietario)));
        Map<Integer, Long> conteos = registroDAO.contarCompletadosPorUsuario(propietario.getUsuarioId());

        List<HabitoResumenDTO> resumen = new ArrayList<>();
        for (Habito habito : todos) {
            long total = conteos.getOrDefault(habito.getHabitoId(), 0L);
            resumen.add(new HabitoResumenDTO(habito, (int) total));
        }
        return resumen;
    }

    /**
     * Guarda la edición de un hábito y devuelve desde qué día se aplica su
     * objetivo: hoy, o una fecha futura si el cambio de objetivo no puede
     * aplicarse ya (la regla está en {@link CalculoObjetivo}). Sin cambio de
     * objetivo, hoy.
     *
     * Con fecha futura, el objetivo nuevo queda programado en el historial
     * y el hábito conserva el de hoy hasta entonces; el nombre, la
     * descripción, la categoría y el recordatorio se guardan ya. Un
     * formulario que trae el objetivo de hoy no cambia nada: tampoco anula
     * un cambio programado.
     */
    @Transactional
    public LocalDate actualizar(Habito habito, Integer categoriaId) {
        Habito existente = habitoDAO.findById(habito.getHabitoId());
        if (existente == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        habito.setTipo(resolverCategoria(categoriaId, habito.getPropietario()));

        // Primero, el objetivo de hoy: si ha llegado un cambio programado,
        // es contra ese contra el que se compara.
        LocalDate hoy = rachaService.hoyDe(existente);
        objetivoService.ponerAlDia(List.of(existente), hoy);

        habito.setFechaInicio(existente.getFechaInicio());
        habito.setActivo(existente.isActivo());
        // Va antes de comparar porque en un semanal con días fijos el
        // objetivo sale de los días marcados.
        normalizarPlanificacion(habito);

        Objetivo actual = Objetivo.de(existente);
        Objetivo nuevo = Objetivo.de(habito);
        LocalDate aplicaDesde = hoy;
        if (!nuevo.equals(actual)) {
            List<LocalDate> fechasDeLaSemana = registroDAO
                    .findByHabitoAndRango(existente, hoy.with(DayOfWeek.MONDAY), hoy).stream()
                    .map(Registro::getFecha)
                    .toList();
            aplicaDesde = CalculoObjetivo.fechaDeEfecto(actual, nuevo, hoy,
                    existente.getFechaInicio(), fechasDeLaSemana);
            objetivoService.programar(existente, nuevo, aplicaDesde, hoy);

            if (aplicaDesde.isAfter(hoy)) {
                // Programado: hasta su fecha, el hábito sigue con el de hoy.
                // El ritmo se anclará al aplicarlo (ObjetivoService.ponerAlDia).
                habito.setFrecuencia(existente.getFrecuencia());
                habito.setMeta(existente.getMeta());
                habito.setDiasSemana(existente.getDiasSemana());
            } else if (actual.frecuencia() != nuevo.frecuencia() || actual.meta() != nuevo.meta()) {
                // El ritmo se ancla ANTES de guardar, con el hábito tal como
                // estaba: hay que calcularlo con las reglas antiguas.
                ritmoService.anclarAntesDeCambiar(existente, hoy);
            }
        }
        habitoDAO.update(habito);
        return aplicaDesde;
    }

    @Transactional
    public void eliminar(int id) {
        // El libro de hitos cuelga del hábito y de sus registros: va antes
        // que los dos.
        hitoPagadoDAO.deleteByHabito(id);
        reversionRegistroDAO.deleteByHabito(id);
        registroDAO.deleteByHabito(id);
        rachaDAO.deleteByHabito(id);
        ritmoDAO.deleteByHabito(id);
        objetivoDAO.deleteByHabito(id);
        habitoDAO.delete(id);
    }

    @Transactional
    public HabitoDetalleDTO obtenerDetalle(int habitoId, YearMonth mes) {
        Habito habito = habitoDAO.findById(habitoId);
        if (habito == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        if (mes == null) {
            mes = YearMonth.now(rachaService.zonaDe(habito));
        }

        ZoneId zona = rachaService.zonaDe(habito);
        // Antes de leer el ritmo y el objetivo: si ha llegado un cambio
        // programado, se aplica ahora.
        LocalDate hoy = LocalDate.now(zona);
        HistorialObjetivos historial = HistorialObjetivos.de(
                objetivoService.ponerAlDia(List.of(habito), hoy), habito);
        Racha racha = rachaDAO.findByHabito(habito);
        List<Registro> todosRegistros = registroDAO.findByHabito(habito);

        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        List<Registro> registrosMes = registroDAO.findByHabitoAndRango(habito, desde, hasta);

        List<RegistroDiaDTO> heatmap = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDate fechaActual = dia;
            int veces = (int) registrosMes.stream()
                    .filter(r -> r.getFecha().equals(fechaActual) && r.isCompletado())
                    .count();
            heatmap.add(new RegistroDiaDTO(fechaActual, veces > 0, veces));
        }

        // Días distintos con al menos un completado (no registros: un diario
        // con meta 3/día que se hace 3 veces el lunes cuenta como 1 día)
        int completadosMesActual = (int) registrosMes.stream()
                .filter(Registro::isCompletado)
                .map(Registro::getFecha)
                .distinct()
                .count();

        YearMonth mesActual = YearMonth.now(zona);
        Double porcentaje = null;
        if (habito.getFrecuencia().name().equals("DIARIO")) {
            int diasTranscurridos = Math.min(LocalDate.now(zona).getDayOfMonth(), mes.lengthOfMonth());
            if (mes.equals(mesActual) && diasTranscurridos > 0) {
                porcentaje = (completadosMesActual * 100.0) / diasTranscurridos;
            } else if (!mes.equals(mesActual)) {
                porcentaje = (completadosMesActual * 100.0) / mes.lengthOfMonth();
            }
        }

        List<RegistroResumenDTO> ultimosRegistros = todosRegistros.stream()
                .limit(10)
                .map(r -> new RegistroResumenDTO(r.getRegistroId(), r.getFecha(), r.isCompletado(), r.getNota(), r.getValoracion()))
                .collect(Collectors.toList());

        var valoracionStats = todosRegistros.stream()
                .filter(r -> r.getValoracion() != null)
                .mapToInt(Registro::getValoracion)
                .average();
        Double valoracionMedia = valoracionStats.isPresent() ? valoracionStats.getAsDouble() : null;

        RitmoLeido ritmo = ritmoService.ritmoLeido(habito, hoy);

        HabitoDetalleDTO dto = new HabitoDetalleDTO();
        dto.setHabitoId(habito.getHabitoId());
        dto.setNombre(habito.getNombre());
        dto.setDescripcion(habito.getDescripcion());
        dto.setRachaActual(rachaService.rachaActualVigente(racha));
        dto.setRachaMaxima(racha != null ? racha.getRachaMaxima() : 0);
        dto.setRitmoNivel(CalculoRitmo.nivel(ritmo.valor()));
        dto.setRitmoTendencia(ritmo.tendencia());
        dto.setTotalCompletados((int) todosRegistros.stream().filter(Registro::isCompletado).count());
        dto.setMeta(habito.getMeta());
        dto.setFrecuencia(habito.getFrecuencia().name());
        dto.setDiasSemana(habito.getDiasSemana());
        dto.setCompletadosMesActual(completadosMesActual);
        dto.setPorcentajeMesActual(porcentaje);
        dto.setMesConsultado(mes.toString());
        dto.setHeatmap(heatmap);
        dto.setUltimosRegistros(ultimosRegistros);
        dto.setValoracionMedia(valoracionMedia);
        HabitoObjetivo programado = historial.programadaTras(hoy);
        dto.setObjetivoProgramado(programado == null ? null : ObjetivoProgramadoDTO.desde(programado));

        return dto;
    }

    /**
     * ¿Ha cumplido hoy el usuario todo lo que tenía comprometido?
     *
     * Solo miran los DIARIO. Un SEMANAL no tiene un día en el que "toque" —su
     * meta es flexible dentro de la semana—, así que uno a medias no significa
     * que hoy quede algo pendiente. Contarlo bloqueaba el día completo los
     * siete días de la semana aunque los diarios estuvieran todos hechos.
     *
     * Sin hábitos activos no hay día completo: no había nada que cumplir.
     * Tener solo semanales es distinto: hoy no había nada que cumplir, y eso
     * sí cuenta como cumplido.
     */
    public boolean esDiaCompleto(Usuario usuario) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        if (activos.isEmpty()) return false;

        // Cada hábito con el objetivo vigente hoy, sin escribir nada: esto se
        // pregunta también desde la mascota, fuera de las lecturas que ponen
        // al día el objetivo.
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate hoy = LocalDate.now(zona);
        Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(activos);
        List<Habito> diarios = activos.stream()
                .map(h -> HistorialObjetivos.de(historiales, h).vistaEl(hoy))
                .filter(h -> h.getFrecuencia() == Frecuencia.DIARIO)
                .toList();
        if (diarios.isEmpty()) return true;

        for (Habito habito : diarios) {
            LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);
            int completadosPeriodo =
                    registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
            if (completadosPeriodo < habito.getMeta()) return false;
        }
        return true;
    }

    /**
     * ¿Le toca a {@code habito} el día {@code fecha}?
     *
     * DIARIO siempre toca. SEMANAL sin días fijos (diasSemana null o en
     * blanco) es "semanal flexible": toca cualquier día, igual que un hábito
     * sin planificación concreta. SEMANAL con días fijos solo toca si el día
     * ISO de {@code fecha} (1=lunes...7=domingo, ver
     * {@link LocalDate#getDayOfWeek()} y el comentario de
     * {@link Habito#getDiasSemana()}) está en la lista separada por comas.
     * Cada trozo se recorta antes de comparar, así que " 2, 4 " vale igual
     * que "2,4".
     */
    private boolean estaProgramadoPara(Habito habito, LocalDate fecha) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) return true;

        String dias = habito.getDiasSemana();
        if (dias == null || dias.isBlank()) return true;

        String diaIso = String.valueOf(fecha.getDayOfWeek().getValue());
        for (String trozo : dias.split(",")) {
            if (trozo.trim().equals(diaIso)) return true;
        }
        return false;
    }

    @Transactional
    public List<DashboardHabitoDTO> obtenerDashboard(Usuario usuario) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate hoy = LocalDate.now(zona);
        // Todo aquí es de hoy: con el objetivo al día, basta con el hábito.
        objetivoService.ponerAlDia(activos, hoy);

        // Ventana: los 10 días de la mini-heatmap, extendida hacia atrás hasta
        // el lunes de la semana del día más antiguo (para calcular "semana
        // cumplida" con la semana completa). Máximo 16 días.
        LocalDate baseVentana = hoy.minusDays(9);
        LocalDate inicioVentana =
                baseVentana.minusDays(baseVentana.getDayOfWeek().getValue() - 1L);

        List<Habito> programadosHoy = activos.stream()
                .filter(habito -> estaProgramadoPara(habito, hoy))
                .collect(Collectors.toList());

        // Una sola consulta para todos los hábitos programados hoy, en vez de
        // una consulta por hábito dentro del bucle: era el N+1 más caliente
        // de la app, se ejecuta en cada carga del dashboard.
        Map<Integer, List<Registro>> registrosPorHabito = registroDAO
                .findByHabitosAndRango(programadosHoy, inicioVentana, hoy)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getHabito().getHabitoId()));

        List<DashboardHabitoDTO> dashboard = new ArrayList<>();
        for (Habito habito : programadosHoy) {
            List<Registro> registrosVentana =
                    registrosPorHabito.getOrDefault(habito.getHabitoId(), List.of());

            LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);

            int completadosPeriodo = (int) registrosVentana.stream()
                    .filter(r -> !r.getFecha().isBefore(periodo[0]) && !r.getFecha().isAfter(periodo[1]))
                    .count();

            boolean completadoHoy = registrosVentana.stream()
                    .anyMatch(r -> r.getFecha().equals(hoy));

            List<String> fechasCompletadas = registrosVentana.stream()
                    .filter(Registro::isCompletado)
                    .map(r -> r.getFecha().toString())
                    .collect(Collectors.toList());

            dashboard.add(new DashboardHabitoDTO(habito, completadoHoy, completadosPeriodo, fechasCompletadas));
        }
        return dashboard;
    }

    /**
     * Un SEMANAL sin días fijos (diasSemana null o en blanco): su meta es
     * flexible dentro de la semana, así que no tiene un día concreto que lo
     * represente en la cuadrícula de {@link #obtenerSemana}.
     */
    private boolean esSemanalFlexible(Habito habito) {
        return habito.getFrecuencia() == Frecuencia.SEMANAL
                && (habito.getDiasSemana() == null || habito.getDiasSemana().isBlank());
    }

    /**
     * La semana completa (lunes a domingo) en una sola consulta de registros
     * por hábito, para no repetir {@link #obtenerDashboard} siete veces.
     *
     * {@code desde} fija la semana a devolver: se toma el lunes de esa
     * semana con el mismo patrón que ya usa
     * {@link Frecuencia#rangoPeriodoActual}. Sin {@code desde}, la semana es
     * la de hoy en la zona del usuario.
     *
     * Los SEMANAL flexibles (ver {@link #esSemanalFlexible}) no entran en
     * ningún día: van aparte, con su progreso agregado de toda la semana.
     * El resto —DIARIO y SEMANAL con días fijos— usa
     * {@link #estaProgramadoPara} para decidir en qué días de los siete
     * aparece, igual que decide qué aparece hoy en el dashboard normal.
     */
    @Transactional
    public SemanaDashboardDTO obtenerSemana(Usuario usuario, LocalDate desde) {
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate base = desde != null ? desde : LocalDate.now(zona);
        LocalDate lunes = base.with(DayOfWeek.MONDAY);

        List<Habito> activos = habitoDAO.findActivos(usuario);
        // La semana pedida puede ser otra: cada día se juzga con su objetivo.
        LocalDate hoy = LocalDate.now(zona);
        Map<Integer, HistorialObjetivos> historiales = objetivoService.ponerAlDia(activos, hoy);
        // Lo que es de toda la semana (si es flexible y su meta), con el
        // objetivo de su último día ya llegado: en la semana en curso, el de
        // hoy, como antes del historial.
        LocalDate domingo = lunes.plusDays(6);
        LocalDate ultimoDia = domingo.isAfter(hoy) ? hoy : domingo;

        // Una sola consulta para todos los hábitos activos, en vez de una
        // por hábito dentro del bucle.
        Map<Integer, List<Registro>> registrosPorHabito = registroDAO
                .findByHabitosAndRango(activos, lunes, lunes.plusDays(6))
                .stream()
                .collect(Collectors.groupingBy(r -> r.getHabito().getHabitoId()));

        List<List<HabitoDiaDTO>> habitosPorDia = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            habitosPorDia.add(new ArrayList<>());
        }
        List<HabitoFlexibleDTO> flexibles = new ArrayList<>();

        for (Habito habito : activos) {
            List<Registro> registrosSemana =
                    registrosPorHabito.getOrDefault(habito.getHabitoId(), List.of());
            HistorialObjetivos historial = HistorialObjetivos.de(historiales, habito);

            Habito deLaSemana = historial.vistaEl(ultimoDia);
            if (esSemanalFlexible(deLaSemana)) {
                int completadosSemana = (int) registrosSemana.stream()
                        .filter(Registro::isCompletado)
                        .count();
                flexibles.add(new HabitoFlexibleDTO(deLaSemana, completadosSemana, deLaSemana.getMeta()));
                continue;
            }

            for (int i = 0; i < 7; i++) {
                LocalDate fecha = lunes.plusDays(i);
                Habito delDia = historial.vistaEl(fecha);
                if (esSemanalFlexible(delDia) || !estaProgramadoPara(delDia, fecha)) continue;

                boolean completado = registrosSemana.stream()
                        .anyMatch(r -> r.getFecha().equals(fecha) && r.isCompletado());

                habitosPorDia.get(i).add(new HabitoDiaDTO(delDia, completado));
            }
        }

        List<DiaSemanaDTO> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dias.add(new DiaSemanaDTO(lunes.plusDays(i).toString(), habitosPorDia.get(i)));
        }

        // Hoy del usuario: ni el de la semana pedida ni el del servidor.
        return new SemanaDashboardDTO(dias, flexibles, hoy.toString());
    }
}