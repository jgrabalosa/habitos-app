package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.service.TextosService;
import com.norday.core.model.Usuario;
import com.norday.habitos.model.CalculoObjetivo;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.LimitesMinutos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.model.dto.HabitoResumenDTO;
import com.norday.habitos.model.dto.ObjetivoProgramadoDTO;
import com.norday.habitos.model.dto.RegistroDiaDTO;
import com.norday.habitos.model.dto.RegistroResumenDTO;
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

    @Autowired
    private TextosService textosService;

    @Autowired
    private com.norday.core.repository.IUsuarioDAO usuarioDAO;

    /** Normaliza la relación frecuencia/díasSemana/meta antes de guardar:
     *  los días solo aplican a SEMANAL, y si hay días la meta se deriva de ellos.
     *  En un diario de minutos la meta es siempre 1: lo que se pide es un día. */
    private void normalizarPlanificacion(Habito habito) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) {
            habito.setDiasSemana(null);
            if (habito.getUnidad() == Unidad.MINUTOS) {
                habito.setMeta(1);
            }
            return;
        }
        String dias = habito.getDiasSemana();
        if (dias != null && !dias.isBlank()) {
            habito.setMeta((int) java.util.Arrays.stream(dias.split(","))
                    .filter(d -> !d.isBlank()).count());
        }
    }

    /**
     * Pone en el hábito su unidad y sus minutos. De MINUTOS, los minutos por
     * día tienen que ir de 1 a 600 (400 si no); de VECES, no hay minutos.
     */
    private void aplicarUnidad(Habito habito, Unidad unidad, Integer minutosObjetivo) {
        if (unidad == Unidad.MINUTOS && !LimitesMinutos.valido(minutosObjetivo)) {
            throw new IllegalArgumentException(textosService.texto("habito.minutos.objetivo.rango",
                    habito.getPropietario(), LimitesMinutos.MINIMO, LimitesMinutos.MAXIMO));
        }
        habito.setUnidad(unidad);
        habito.setMinutosObjetivo(unidad == Unidad.MINUTOS ? minutosObjetivo : null);
    }

    private static final java.util.regex.Pattern ICONO_VALIDO = java.util.regex.Pattern.compile("[a-zA-Z0-9]{1,40}");

    /** El icono, si llega, es un código de 1 a 40 letras o cifras (400 si no). */
    private void validarIcono(String icono, Usuario propietario) {
        if (icono != null && !ICONO_VALIDO.matcher(icono).matches()) {
            throw new IllegalArgumentException(textosService.texto("habito.icono.invalido", propietario));
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
        return crearHabito(habito, categoriaId, null, null);
    }

    /**
     * Crea un hábito. Sin {@code unidad}, de VECES (los minutos se ignoran).
     * De MINUTOS pide {@code minutosObjetivo} de 1 a 600 (400 si no); en un
     * diario de minutos la meta es 1, y en un semanal, los días por semana.
     */
    @Transactional
    public List<String> crearHabito(Habito habito, Integer categoriaId, Unidad unidad, Integer minutosObjetivo) {
        // Crear un hábito concede logros (activos, categorías): el usuario,
        // bloqueado antes que nada (ver bloquearUsuario).
        validarIcono(habito.getIcono(), habito.getPropietario());
        bloquearUsuario(habito.getPropietario());
        aplicarUnidad(habito, unidad != null ? unidad : Unidad.VECES, minutosObjetivo);
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
     * Bloquea a su propietario y al hábito, en ese orden, hasta el final de
     * la transacción en curso. Dos completados (o un completado y un
     * deshacer) del mismo hábito se hacen uno detrás de otro, y el segundo ve
     * lo que hizo el primero. El usuario también: lo que dio un completado se
     * mide como saldo y logros después menos antes, y un completado de otro
     * hábito del mismo usuario confirmado en medio se colaba en la cuenta (y
     * deshacer el primero lo devolvía y retiraba sus logros).
     *
     * Siempre usuario y después hábito, y siempre por aquí: es el único sitio
     * que bloquea un hábito, así que dos operaciones no pueden pedirlos en
     * orden contrario y abrazarse. Lo usan completar, deshacer, editar,
     * pausar, reanudar, borrar y anular un cambio programado.
     *
     * Devuelve el hábito releído tras el bloqueo, y es con ese con el que hay
     * que seguir: el que se leyó antes de esperar puede ser viejo (una pausa
     * o un objetivo nuevo confirmados entretanto). Si ya no existe, 404.
     */
    @Transactional
    public Habito bloquear(Habito habito) {
        // Sin usuario (su baja ha confirmado mientras se esperaba), tampoco
        // hay hábito.
        if (!usuarioDAO.bloquear(habito.getPropietario().getUsuarioId())) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        Habito releido = habitoDAO.bloquear(habito.getHabitoId());
        if (releido == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        return releido;
    }

    /**
     * Bloquea solo al usuario (ver IUsuarioDAO.bloquear) hasta el final de
     * la transacción; 404 si ya no existe. Para lo que concede logros o
     * monedas sin cambiar un hábito concreto (abrir Hoy, crear un hábito o
     * una categoría, poner una nota): sin él, lo concedido se cuela en el
     * «saldo después − saldo antes» de un completado del mismo usuario que
     * esté a medias. Va antes de cualquier escritura de la transacción, para
     * que el orden siga siendo usuario y después lo demás.
     */
    @Transactional
    public void bloquearUsuario(Usuario usuario) {
        if (!usuarioDAO.bloquear(usuario.getUsuarioId())) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
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
        Map<Integer, HistorialObjetivos> historiales =
                objetivoService.ponerAlDia(todos, LocalDate.now(zonaUsuarioService.zonaDe(propietario)));
        Map<Integer, Long> conteos = registroDAO.contarCompletadosPorUsuario(propietario.getUsuarioId());

        List<HabitoResumenDTO> resumen = new ArrayList<>();
        for (Habito habito : todos) {
            HistorialObjetivos historial = HistorialObjetivos.de(historiales, habito);
            // En veces, los registros. Un hábito con algo de minutos cuenta en
            // la unidad de su meta: sus días de minutos, por días hechos.
            long total = tieneMinutos(habito, historial)
                    ? Hechos.de(registroDAO.findByHabito(habito), historial::objetivoEl).unidades()
                    : conteos.getOrDefault(habito.getHabitoId(), 0L);
            resumen.add(new HabitoResumenDTO(habito, (int) total));
        }
        return resumen;
    }

    /** ¿Tiene el hábito algo de minutos: hoy o en alguna versión de su objetivo? */
    private static boolean tieneMinutos(Habito habito, HistorialObjetivos historial) {
        return habito.getUnidad() == Unidad.MINUTOS
                || historial.versiones().stream().anyMatch(v -> v.getUnidad() == Unidad.MINUTOS);
    }

    /**
     * Guarda la edición de un hábito y devuelve desde qué día se aplica su
     * objetivo: hoy, el lunes de esta semana (un cambio semanal sin
     * registros esta semana), o una fecha futura si el cambio de objetivo no
     * puede aplicarse ya (la regla está en {@link CalculoObjetivo}). Sin
     * cambio de objetivo, hoy.
     *
     * Con fecha futura, el objetivo nuevo queda programado en el historial
     * y el hábito conserva el de hoy hasta entonces; el nombre, la
     * descripción, la categoría y el recordatorio se guardan ya. Un
     * formulario que trae el objetivo de hoy no cambia nada: tampoco anula
     * un cambio programado.
     */
    @Transactional
    public LocalDate actualizar(Habito habito, Integer categoriaId) {
        return actualizar(habito, categoriaId, null, null);
    }

    /**
     * Igual, con la unidad y los minutos que pide el cliente. null en
     * cualquiera de los dos es "sin cambio": la app ya instalada no los manda,
     * y un null no puede borrar nada (se conserva lo que tenía el hábito).
     *
     * De MINUTOS a VECES no se puede (409). De MINUTOS, los minutos por día
     * van de 1 a 600 (400 si no). Cambiar los minutos o pasar de veces a
     * minutos se aplica por días (hoy o mañana); si el mismo cambio toca
     * también lo semanal, todo va a la fecha de la semana (ver
     * {@link CalculoObjetivo}).
     */
    @Transactional
    public LocalDate actualizar(Habito habito, Integer categoriaId, Unidad unidadPedida,
                                Integer minutosPedidos) {
        validarIcono(habito.getIcono(), habito.getPropietario());
        Habito existente = habitoDAO.findById(habito.getHabitoId());
        if (existente == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        // De uno en uno con completar, deshacer y la pausa, y con el hábito
        // releído: sin esto, un paso confirmado mientras se decide la fecha
        // del cambio no se ve (el día se reinterpreta con el objetivo nuevo y
        // cobra dos veces), y una pausa confirmada después de que el
        // controlador leyera el hábito se deshace al guardar.
        existente = bloquear(existente);
        habito.setTipo(resolverCategoria(categoriaId, habito.getPropietario()));

        // Primero, el objetivo de hoy: si ha llegado un cambio programado,
        // es contra ese contra el que se compara.
        LocalDate hoy = rachaService.hoyDe(existente);
        HistorialObjetivos historial = HistorialObjetivos.de(
                objetivoService.ponerAlDia(List.of(existente), hoy), existente);

        // La unidad y los minutos, con "ausente = sin cambio". Van siempre en
        // el hábito que se guarda: update hace merge de la entidad entera, y
        // sin esto un PUT de la app ya instalada la devolvería a VECES.
        Unidad unidad = unidadPedida != null ? unidadPedida : existente.getUnidad();
        Integer minutos = minutosPedidos != null ? minutosPedidos : existente.getMinutosObjetivo();
        if (existente.getUnidad() == Unidad.MINUTOS && unidad != Unidad.MINUTOS) {
            throw new ConflictoException(textosService.texto("habito.minutos.a.veces", existente.getPropietario()));
        }
        aplicarUnidad(habito, unidad, minutos);

        habito.setFechaInicio(existente.getFechaInicio());
        habito.setActivo(existente.isActivo());
        // El icono, con "ausente = sin cambio" como la unidad: la app ya
        // instalada no lo manda, y update hace merge de la entidad entera, así
        // que un null lo borraría. No es del objetivo: no crea versión.
        if (habito.getIcono() == null) {
            habito.setIcono(existente.getIcono());
        }
        // Va antes de comparar porque en un semanal con días fijos el
        // objetivo sale de los días marcados.
        normalizarPlanificacion(habito);

        Objetivo actual = Objetivo.de(existente);
        Objetivo nuevo = Objetivo.de(habito);
        LocalDate aplicaDesde = hoy;
        if (nuevo.equals(actual)) {
            // Volver al objetivo de hoy con un cambio programado pendiente lo
            // anula, pero solo si la petición trae la unidad. La app nueva
            // siempre la manda; la ya instalada nunca, y con "ausente = sin
            // cambio" su formulario entero da siempre el objetivo de hoy: si
            // anulara, un PUT que solo cambia el nombre borraría el cambio
            // programado desde otro dispositivo con la app nueva.
            if (unidadPedida != null && historial.programadaTras(hoy) != null) {
                objetivoService.anularProgramados(existente, hoy);
            }
        } else {
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
                habito.setUnidad(existente.getUnidad());
                habito.setMinutosObjetivo(existente.getMinutosObjetivo());
                habito.setFrecuencia(existente.getFrecuencia());
                habito.setMeta(existente.getMeta());
                habito.setDiasSemana(existente.getDiasSemana());
            } else if (actual.cambiaElCreditoRespectoA(nuevo)) {
                // El ritmo se ancla ANTES de guardar, con el hábito tal como
                // estaba: hay que calcularlo con las reglas antiguas. Se
                // ancla al empezar el periodo de hoy con las reglas nuevas,
                // sin pasar de aplicaDesde: un semanal que empieza el lunes,
                // el lunes, y la semana entera es su primer periodo, que
                // solo puede subir; un diario, hoy, para no cerrar como días
                // sin hacer los de esta semana anteriores al cambio.
                LocalDate inicioDelPeriodo = nuevo.frecuencia().rangoPeriodo(hoy)[0];
                LocalDate ancla = inicioDelPeriodo.isAfter(aplicaDesde) ? inicioDelPeriodo : aplicaDesde;
                ritmoService.anclarAntesDeCambiar(existente, ancla);
            }
        }
        habitoDAO.update(habito);
        return aplicaDesde;
    }

    /**
     * Anula el cambio de objetivo programado del hábito: borra las versiones
     * que empiezan después de hoy y deja la copia del objetivo como está.
     */
    @Transactional
    public void anularObjetivoProgramado(Habito habito) {
        // Cambia el historial del hábito: de uno en uno con lo demás que lo
        // cambia (un PUT que programa, un completado que lo lee).
        habito = bloquear(habito);
        LocalDate hoy = rachaService.hoyDe(habito);
        // Antes, lo que ya haya llegado se aplica: solo se anula lo futuro.
        objetivoService.ponerAlDia(List.of(habito), hoy);
        objetivoService.anularProgramados(habito, hoy);
    }

    @Transactional
    public void eliminar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        // Como todo lo que cambia un hábito: usuario y hábito bloqueados
        // antes de tocar nada. Borrar bloquea las filas que borra, y aquí el
        // hábito va el último: sin bloquearlo antes, con un completado o un
        // deshacer suyo a medias (que ya tiene el hábito y quiere esas filas)
        // Postgres abortaría una de las dos por abrazo mortal.
        bloquear(habito);
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

        // Lo hecho de cada día con el objetivo de ese día. En un día de
        // minutos, la celda está completada si el día llegó a sus minutos y
        // lleva los minutos y los minutos objetivo de ese día; en uno de
        // veces, como siempre, con los dos a null.
        Hechos hechos = Hechos.de(todosRegistros, historial::objetivoEl);
        List<RegistroDiaDTO> heatmap = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDate fechaActual = dia;
            int veces = (int) registrosMes.stream()
                    .filter(r -> r.getFecha().equals(fechaActual) && r.isCompletado())
                    .count();
            // Con la versión vigente ese día: un día de veces (por ejemplo,
            // anterior al paso a minutos) no lleva minutos ni objetivo.
            Objetivo objetivoDelDia = historial.objetivoEl(fechaActual);
            heatmap.add(objetivoDelDia.enMinutos()
                    ? new RegistroDiaDTO(fechaActual, hechos.diaHecho(fechaActual), veces,
                            hechos.cantidadEl(fechaActual), objetivoDelDia.minutosObjetivo())
                    : new RegistroDiaDTO(fechaActual, veces > 0, veces));
        }

        // Días distintos con al menos un completado (no registros: un diario
        // con meta 3/día que se hace 3 veces el lunes cuenta como 1 día). En
        // un día de minutos, solo si llegó a sus minutos.
        int completadosMesActual = 0;
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            if (hechos.unidadesEl(dia) > 0) completadosMesActual++;
        }

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
                .map(r -> new RegistroResumenDTO(r.getRegistroId(), r.getFecha(), r.isCompletado(), r.getNota(),
                        r.getValoracion(), r.getCantidad(), r.getOrigen()))
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
        dto.setIcono(habito.getIcono());
        dto.setRachaActual(rachaService.rachaActualVigente(racha));
        dto.setRachaMaxima(racha != null ? racha.getRachaMaxima() : 0);
        dto.setRitmoNivel(CalculoRitmo.nivel(ritmo.valor()));
        dto.setRitmoTendencia(ritmo.tendencia());
        // En la unidad de la meta: veces en un hábito de veces, días hechos en
        // los días de minutos.
        dto.setTotalCompletados(hechos.unidades());
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
        dto.setUnidad(habito.getUnidad());
        boolean enMinutos = habito.getUnidad() == Unidad.MINUTOS;
        dto.setMinutosObjetivo(enMinutos ? habito.getMinutosObjetivo() : null);
        // Todos los minutos de sus días de minutos, también los de más. Null
        // en un hábito de veces: no hay minutos que sumar.
        dto.setTotalMinutos(enMinutos
                ? todosRegistros.stream()
                        .filter(r -> r.isCompletado() && historial.objetivoEl(r.getFecha()).enMinutos())
                        .mapToInt(Registro::getCantidad)
                        .sum()
                : null);

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
            List<Registro> deHoy = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]);
            if (habito.getUnidad() == Unidad.MINUTOS) {
                // Un diario de minutos, al llegar a sus minutos.
                Objetivo objetivo = Objetivo.de(habito);
                if (!Hechos.de(deHoy, fecha -> objetivo).diaHecho(hoy)) return false;
            } else if (deHoy.size() < habito.getMeta()) {
                return false;
            }
        }
        return true;
    }

}