package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.LimitesMinutos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.ReversionLogro;
import com.norday.habitos.model.ReversionRegistro;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RegistroService {

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private ILogroDAO logroDAO;

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Autowired
    private IReversionRegistroDAO reversionRegistroDAO;

    @Autowired
    private IHitoPagadoDAO hitoPagadoDAO;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private TextosService textosService;

    // Lo que vale cada paso ya no está aquí: la regla es CalculoPremio y los
    // números, ParametrosPremio.
    // B2: los hitos ya no cuelgan de la racha; sus números están en
    // ParametrosPremio. Se conserva comentado.
    // /** Umbral de racha y puntos que paga. Ordenados de menor a mayor. */
    // private static final int[][] HITOS_RACHA = {
    //         {3, 50}, {7, 100}, {30, 300}, {100, 750}, {365, 2000}
    // };

    /**
     * Lo que pide un completado: la nota, la fecha (null = hoy), los minutos
     * tal como llegan (solo cuentan si el día es de minutos), la clave del
     * cliente para no repetirlo (null = sin clave) y el origen.
     */
    public record Solicitud(String nota, LocalDate fecha, String minutos, UUID clave, OrigenRegistro origen) {}

    @Transactional
    public Map<String, Object> completarHabito(Habito habito, String nota) {
        return completarHabito(habito, nota, null);
    }

    /** La puerta de la app ya instalada: sin minutos, sin clave y a mano. */
    @Transactional
    public Map<String, Object> completarHabito(Habito habito, String nota, LocalDate fechaSolicitada) {
        return completar(habito, new Solicitud(nota, fechaSolicitada, null, null, OrigenRegistro.MANUAL));
    }

    /**
     * Completa un paso del hábito. Devuelve lo que ganó y, con
     * {@code repetido = true}, si la clave del cliente ya estaba usada en
     * este hábito: entonces no se ha creado ni pagado nada y los datos son
     * los del registro original (ver {@link #repetido}).
     *
     * En un hábito de minutos el paso lleva sus minutos (obligatorios, de 1
     * a 600) y se pueden dar varios el mismo día, también en un semanal; los
     * rechazos de un completado por día (semanal) y de meta alcanzada
     * (diario) son solo de VECES. Los minutos de más se guardan.
     */
    @Transactional
    public Map<String, Object> completar(Habito habito, Solicitud solicitud) {
        Usuario usuario = habito.getPropietario();
        // Primero la clave: un reintento de algo que ya se guardó responde lo
        // mismo aunque desde entonces haya cambiado algo (una pausa, el día).
        UUID clave = solicitud.clave();
        if (clave != null) {
            Registro previo = registroDAO.findByClave(clave);
            if (previo != null) return repetido(habito, previo, usuario);
        }

        // Los completados de un mismo usuario van de uno en uno: se bloquean
        // su fila y la del hábito hasta el final de la transacción. Sin el
        // hábito, dos pasos a la vez leen el mismo "antes" y cobran los dos la
        // misma parte del día (o el extra de la semana). Sin el usuario, el
        // paso de otro hábito suyo que confirme en medio entra en el "saldo
        // después − saldo antes" y en los logros nuevos de este, y deshacer
        // este los devolvería. Lo que viene después lee ya lo que confirmó el
        // anterior, así que la clave se vuelve a mirar. El orden (usuario,
        // hábito) lo fija habitoService.bloquear, el mismo que en deshacer.
        // Se sigue con el hábito releído tras el bloqueo: el que llegó se leyó
        // antes de esperar, y una pausa o un objetivo nuevo confirmados
        // entretanto no estarían en él.
        habito = habitoService.bloquear(habito);
        if (clave != null) {
            Registro previo = registroDAO.findByClave(clave);
            if (previo != null) return repetido(habito, previo, usuario);
        }

        // Un hábito en pausa no se marca: la pausa lo para todo.
        if (!habito.isActivo()) {
            throw new ConflictoException("El hábito está en pausa");
        }
        LocalDate hoy = rachaService.hoyDe(habito);
        LocalDate fecha = solicitud.fecha() != null ? solicitud.fecha() : hoy;
        if (fecha.isAfter(hoy)) {
            throw new ConflictoException("No se puede completar un hábito en una fecha futura");
        }
        // Suelo del completado retroactivo: el lunes de la semana en curso.
        // Sin límite, rellenar meses enteros dispara puntos, XP y logros de
        // racha a voluntad. El lunes se calcula explícito porque
        // rangoPeriodo(hoy) devuelve el propio día en DIARIO.
        LocalDate lunesDeEstaSemana = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        if (fecha.isBefore(lunesDeEstaSemana)) {
            throw new ConflictoException("Solo se puede completar una fecha de la semana en curso");
        }
        // Ni un día anterior al alta del hábito: uno creado el viernes no
        // existía de lunes a jueves, y marcarlos el mismo viernes cobraba los
        // pasos, el hito de 3 días y el logro de constancia el día del alta.
        // Deshacer no lleva esta regla: si quedó algún registro así de antes,
        // se puede quitar.
        if (habito.getFechaInicio() != null && fecha.isBefore(habito.getFechaInicio())) {
            throw new ConflictoException(textosService.texto("registro.fecha.antes.alta", usuario));
        }

        // El objetivo de hoy al día, y el día se juzga con el que tenía esa
        // fecha: un completado retroactivo de esta semana puede caer antes
        // de un cambio de objetivo. delDia es una vista: no se guarda.
        HistorialObjetivos historial = HistorialObjetivos.de(
                objetivoService.ponerAlDia(List.of(habito), hoy), habito);
        Habito delDia = historial.vistaEl(fecha);
        Frecuencia frecuencia = delDia.getFrecuencia();
        Objetivo objetivoDelDia = historial.objetivoEl(fecha);
        boolean enMinutos = objetivoDelDia.enMinutos();
        // En minutos, los minutos del paso; nunca 1 por defecto. En veces
        // cada paso vale 1 y los minutos, si llegan, se ignoran.
        int cantidad = enMinutos ? minutosDelPaso(solicitud.minutos(), usuario) : 1;

        // SEMANAL de veces: máximo un completado por día (cada completado es un día distinto)
        if (!enMinutos && frecuencia == Frecuencia.SEMANAL
                && registroDAO.existeRegistroEnFecha(habito, fecha)) {
            throw new ConflictoException("Este hábito ya se ha completado en esa fecha");
        }

        // Lo hecho en el periodo antes de este paso, cada día con su
        // objetivo. completadosAntes va en la unidad de la meta: en veces, los
        // registros del periodo, como siempre.
        LocalDate[] periodo = frecuencia.rangoPeriodo(fecha);
        Hechos delPeriodo = Hechos.de(
                registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]), historial::objetivoEl);
        int completadosAntes = delPeriodo.unidades();
        int meta = delDia.getMeta();

        // DIARIO de veces: no dejar superar la meta del día. Sin esto, un doble
        // toque o un reintento por timeout de red crea un registro de más y, si
        // coincide justo con el punto en que se alcanza la meta, otorga
        // puntos por partida doble. En minutos, lo de más se guarda (y la
        // clave del cliente evita el doble toque).
        if (!enMinutos && frecuencia == Frecuencia.DIARIO && completadosAntes >= meta) {
            throw new ConflictoException("Este hábito ya ha alcanzado la meta de esa fecha");
        }
        boolean diaHechoAntes = delPeriodo.diaHecho(fecha);
        Hechos delPeriodoDespues = delPeriodo.con(fecha, cantidad);
        boolean diaHechoDespues = delPeriodoDespues.diaHecho(fecha);

        // Instantánea del estado previo, para poder deshacer este completado con
        // exactitud (ver ReversionRegistro). Valores primitivos copiados a
        // propósito: si guardásemos la entidad, Hibernate la modificaría más
        // abajo y aquí leeríamos ya los valores nuevos.
        int saldoAntes = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
        Racha rachaPrevia = rachaDAO.findByHabito(habito);
        // La racha ya no se actualiza al completar (apagada el 21-oct-2026,
        // C3 del roadmap); solo se conserva su estado para que deshacer lo
        // restaure. La rotura perezosa se materializa aquí, antes de la
        // instantánea: la regla vive en Racha.sigueViva y la materializa
        // RachaService.rachaActualVigente.
        rachaService.rachaActualVigente(rachaPrevia);
        Integer rachaActualPrevia = rachaPrevia != null ? rachaPrevia.getRachaActual() : null;
        Integer rachaMaximaPrevia = rachaPrevia != null ? rachaPrevia.getRachaMaxima() : null;
        LocalDate periodoMetaAlcanzadaPrevio = rachaPrevia != null ? rachaPrevia.getPeriodoMetaAlcanzada() : null;
        LocalDate ultimaFechaPrevia = rachaPrevia != null ? rachaPrevia.getUltimaFecha() : null;
        Mascota mascotaPrevia = mascotaService.obtenerOCrear(usuario.getUsuarioId());
        LocalDate mascotaDiaCompletoPrevio = mascotaPrevia.getFechaUltimoDiaCompleto();
        Set<Integer> logrosPrevios = idsLogros(usuario);

        // Días hechos del hábito antes y después de este completado, para
        // los hitos. Se leen antes de guardar el registro nuevo. Cada día se
        // juzga con el objetivo que tenía; uno de minutos, con sus minutos.
        Hechos todos = Hechos.de(registroDAO.findByHabito(habito), historial::objetivoEl);
        int diasHechosAntes = todos.diasHechos();
        // ¿Es este paso una vuelta al hábito? Se mira con las fechas en que ya
        // contaba como hecho, antes de añadir la de ahora: en veces, las de
        // cualquier registro; en minutos, los días que llegaron a sus
        // minutos, y la vuelta llega con el paso que completa el día.
        boolean esUnaVuelta = (!enMinutos || (!diaHechoAntes && diaHechoDespues))
                && CalculoPremio.esUnaVuelta(frecuencia, fecha, todos.fechasHechas());
        int diasHechosDespues = todos.con(fecha, cantidad).diasHechos();

        Registro registro = new Registro(habito, true, solicitud.nota(), fecha);
        registro.setCantidad(cantidad);
        registro.setOrigen(solicitud.origen() != null ? solicitud.origen() : OrigenRegistro.MANUAL);
        registro.setClaveCliente(clave);
        if (clave == null) {
            registroDAO.save(registro);
        } else {
            // Con clave, sin chocar: si otro completado con la misma clave se
            // ha guardado entretanto, no se escribe nada y se responde como
            // a un reintento. Todavía no se ha pagado nada.
            Registro guardado = registroDAO.guardarSiClaveLibre(registro);
            if (guardado == null) {
                return repetido(habito, registroDAO.findByClave(clave), usuario);
            }
            registro = guardado;
        }

        int puntosGanados = 0;
        boolean subioNivel = false;
        int nivelNuevo = 0;

        // Cada paso cobra su parte en el momento, y pasarse del objetivo no da
        // nada: la regla está en CalculoPremio. El extra por completar la
        // semana va en un movimiento aparte, para poder explicar de dónde sale.
        // En la semana del paso de veces a minutos (algún día de la semana es
        // de minutos), un paso de veces también va por el cálculo con tope:
        // su día vale un día entero (objetivo 1), pero la semana no cobra más
        // que meta días. En una semana solo de veces, como siempre.
        boolean semanaConMinutos = !enMinutos && frecuencia == Frecuencia.SEMANAL
                && algunDiaEnMinutos(historial, periodo);
        CalculoPremio.Premio premio = enMinutos
                ? CalculoPremio.delPasoEnMinutos(frecuencia, meta, diasDelPeriodo(delPeriodo), fecha,
                        objetivoDelDia.minutosDelDia(), cantidad)
                : semanaConMinutos
                        ? CalculoPremio.delPasoEnMinutos(frecuencia, meta, diasDelPeriodo(delPeriodo), fecha,
                                1, cantidad)
                        : CalculoPremio.delPaso(frecuencia, completadosAntes, meta);
        if (premio.puntos() > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, premio.puntos(), "HABITO_COMPLETADO",
                    habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
            );
        }
        if (premio.puntosExtra() > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, premio.puntosExtra(), "EXTRA_SEMANA_COMPLETA",
                    habito.getHabitoId(), "Semana completada: " + habito.getNombre()
            );
        }
        puntosGanados += premio.puntosTotales();

        // Un paso puede no dar experiencia (el primero de un diario de 6):
        // entonces no se llama a la mascota.
        int xpOtorgada = premio.xpTotal();
        if (xpOtorgada > 0) {
            ResultadoExperienciaDTO resultadoXp =
                    mascotaService.ganarExperiencia(usuario.getUsuarioId(), xpOtorgada);
            subioNivel = resultadoXp.isSubioNivel();
            nivelNuevo = resultadoXp.getNivelNuevo();
        }

        // Hitos: una vez por hábito, al llegar a 3, 7, 30, 100 y 365 días
        // hechos en total, seguidos o no. Cruzar un umbral no basta: tampoco
        // puede estar ya en el libro de hitos, que es quien garantiza que se
        // paga una sola vez, también con dos completados a la vez. Cada umbral se
        // apunta ligado a este registro y solo se paga si la fila se ha
        // escrito: si otro completado simultáneo lo apuntó antes, este no
        // paga. Deshacer borra la fila y devuelve los puntos con el resto de
        // las monedas.
        List<Integer> umbralesCruzados = CalculoPremio.hitosAPagar(diasHechosAntes, diasHechosDespues,
                hitoPagadoDAO.findUmbralesByHabito(habito));
        int puntosHito = 0;
        for (int umbral : umbralesCruzados) {
            if (hitoPagadoDAO.apuntar(habito, umbral, registro)) {
                puntosHito += CalculoPremio.puntosDelHito(umbral);
            }
        }
        if (puntosHito > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntosHito, "HITO_DIAS_HECHOS", habito.getHabitoId(),
                    "Hito de " + diasHechosDespues + " días en: " + habito.getNombre()
            );
        }
        puntosGanados += puntosHito;

        // Con este registro puede haberse cerrado el día entero. Va después de
        // guardar el Registro a propósito: la consulta de esDiaCompleto es JPQL,
        // así que Hibernate hace flush antes y el registro recién creado cuenta.
        if (fecha.equals(hoy) && habitoService.esDiaCompleto(usuario)) {
            mascotaService.registrarDiaCompleto(usuario.getUsuarioId());
        }

        // Los logros de constancia cuentan los días hechos del hábito, con el
        // registro nuevo ya guardado. Resiliencia se evalúa aparte, solo si
        // este paso es una vuelta. La lista es una copia: la que devuelve el
        // servicio de logros puede no admitir añadidos.
        List<String> logros = new ArrayList<>(
                logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito));
        if (esUnaVuelta) {
            logros.addAll(logrosHabitosService.evaluarTrasVolver(usuario));
        }

        // Cuándo mostrar el sheet de valoración: SEMANAL siempre (cada completado es
        // un día distinto), DIARIO solo en el último completado del día (al llegar a la meta).
        // En minutos, con el paso que hace que el día llegue a sus minutos.
        boolean mostrarValoracion = fecha.equals(hoy)
                && (enMinutos
                        ? !diaHechoAntes && diaHechoDespues
                        : frecuencia == Frecuencia.SEMANAL || (completadosAntes + 1) >= meta);

        // Cierra la instantánea con lo que efectivamente ocurrió: el delta de
        // saldo recoge de una vez el completado, el hito de racha y los logros.
        // El usuario está bloqueado desde antes de leer el saldo de antes:
        // ningún completado ni deshacer suyo se cuela en medio. Lo que escriba
        // en su saldo o sus logros una operación que no lo bloquea (la tienda,
        // los logros de ritmo al abrir Hoy) todavía podría.
        int monedasOtorgadas = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId()) - saldoAntes;
        ReversionRegistro reversion = new ReversionRegistro(registro, rachaActualPrevia, rachaMaximaPrevia,
                periodoMetaAlcanzadaPrevio, ultimaFechaPrevia, xpOtorgada,
                mascotaDiaCompletoPrevio, monedasOtorgadas);
        for (String codigo : logros) {
            Logro logro = logroDAO.findByCodigo(codigo);
            if (logro != null) {
                reversion.getLogros().add(new ReversionLogro(reversion, logro.getLogroId()));
            }
        }
        // También captura logros disparados indirectamente por la mascota
        // (por ejemplo, al cambiar de fase al ganar XP), que no forman parte
        // de la lista devuelta por LogrosHabitosService.
        for (Integer logroId : idsLogros(usuario)) {
            if (!logrosPrevios.contains(logroId)
                    && reversion.getLogros().stream().noneMatch(r -> r.getLogroRef() == logroId)) {
                reversion.getLogros().add(new ReversionLogro(reversion, logroId));
            }
        }
        reversionRegistroDAO.save(reversion);

        // puntosGanados sigue siendo la suma, que es lo que lee la app ya
        // instalada. Los cuatro campos siguientes la desglosan.
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("logros", logros);
        resultado.put("puntosGanados", puntosGanados);
        resultado.put("registroId", registro.getRegistroId());
        resultado.put("mostrarValoracion", mostrarValoracion);
        resultado.put("subioNivel", subioNivel);
        resultado.put("nivelNuevo", nivelNuevo);
        resultado.put("puntosPaso", premio.puntos());
        resultado.put("puntosExtraSemana", premio.puntosExtra());
        resultado.put("puntosHito", puntosHito);
        resultado.put("xpGanada", xpOtorgada);
        resultado.put("minutosDia", enMinutos ? delPeriodoDespues.cantidadEl(fecha) : null);
        resultado.put("minutosObjetivo", enMinutos ? objetivoDelDia.minutosObjetivo() : null);
        resultado.put("diaHecho", diaHechoDespues);
        resultado.put("repetido", false);
        return resultado;
    }

    /**
     * La respuesta a un completado cuya clave ya estaba usada: los datos del
     * registro original, con premios a cero, sin crear ni pagar nada. Una
     * clave usada en otro hábito es un conflicto: no se dice nada de él.
     */
    private Map<String, Object> repetido(Habito habito, Registro original, Usuario usuario) {
        if (original == null || original.getHabito() == null
                || original.getHabito().getHabitoId() != habito.getHabitoId()) {
            throw new ConflictoException(textosService.texto("registro.clave.ajena", usuario));
        }
        LocalDate fecha = original.getFecha();
        HistorialObjetivos historial = HistorialObjetivos.de(
                objetivoService.historiales(List.of(habito)), habito);
        Objetivo objetivo = historial.objetivoEl(fecha);
        Hechos delDia = Hechos.de(registroDAO.findByHabitoAndRango(habito, fecha, fecha), historial::objetivoEl);
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("logros", List.of());
        resultado.put("puntosGanados", 0);
        resultado.put("registroId", original.getRegistroId());
        resultado.put("mostrarValoracion", false);
        resultado.put("subioNivel", false);
        resultado.put("nivelNuevo", 0);
        resultado.put("puntosPaso", 0);
        resultado.put("puntosExtraSemana", 0);
        resultado.put("puntosHito", 0);
        resultado.put("xpGanada", 0);
        resultado.put("minutosDia", objetivo.enMinutos() ? delDia.cantidadEl(fecha) : null);
        resultado.put("minutosObjetivo", objetivo.enMinutos() ? objetivo.minutosObjetivo() : null);
        resultado.put("diaHecho", delDia.diaHecho(fecha));
        resultado.put("repetido", true);
        return resultado;
    }

    /**
     * Los minutos de un paso de minutos, validados. Si no llegan, la app es
     * anterior a los minutos: 409 pidiendo actualizarla, nunca 1 minuto por
     * defecto. Si no son un número de 1 a 600, 400.
     */
    private int minutosDelPaso(String texto, Usuario usuario) {
        if (texto == null || texto.isBlank()) {
            throw new ConflictoException(textosService.texto("registro.minutos.actualizar.app", usuario));
        }
        Integer minutos;
        try {
            minutos = Integer.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            minutos = null;
        }
        if (!LimitesMinutos.valido(minutos)) {
            throw new IllegalArgumentException(textosService.texto("registro.minutos.rango", usuario,
                    LimitesMinutos.MINIMO, LimitesMinutos.MAXIMO));
        }
        return minutos;
    }

    /** ¿Es de minutos algún día del periodo, según la versión vigente cada día? */
    private static boolean algunDiaEnMinutos(HistorialObjetivos historial, LocalDate[] periodo) {
        for (LocalDate dia = periodo[0]; !dia.isAfter(periodo[1]); dia = dia.plusDays(1)) {
            if (historial.objetivoEl(dia).enMinutos()) return true;
        }
        return false;
    }

    /**
     * Los días del periodo de un paso de minutos, con lo hecho y su objetivo
     * en minutos. Un día de veces de un semanal (antes de pasar a minutos esa
     * semana) cuenta como un día entero si tiene alguna sesión.
     */
    private static Map<LocalDate, CalculoPremio.DiaDelPeriodo> diasDelPeriodo(Hechos hechos) {
        Map<LocalDate, CalculoPremio.DiaDelPeriodo> dias = new HashMap<>();
        hechos.porDia().forEach((dia, cantidad) -> {
            Objetivo objetivo = hechos.objetivoEl(dia);
            dias.put(dia, new CalculoPremio.DiaDelPeriodo(cantidad,
                    objetivo.enMinutos() ? objetivo.minutosDelDia() : 1));
        });
        return dias;
    }

    private Set<Integer> idsLogros(Usuario usuario) {
        Set<Integer> ids = new HashSet<>();
        usuarioLogroDAO.findByUsuario(usuario).forEach(ul -> {
            if (ul.getLogro() != null) ids.add(ul.getLogro().getLogroId());
        });
        return ids;
    }

    /**
     * Cómo va hoy un hábito, para GET /api/registros/habito/{id}/hoy. Pone
     * antes al día el objetivo del hábito, que es el de hoy.
     *
     * {@code completadoHoy}, {@code completadosPeriodo} y {@code meta}
     * conservan su significado para la app ya instalada: en un hábito de
     * veces, si hay algún registro hoy y los registros del periodo; en uno de
     * minutos, si hoy llegó a sus minutos y los días hechos del periodo.
     * {@code minutosHoy} y {@code minutosObjetivo} son null en VECES.
     */
    @Transactional
    public Map<String, Object> estadoDeHoy(Habito habito) {
        LocalDate hoy = rachaService.hoyDe(habito);
        HistorialObjetivos historial = HistorialObjetivos.de(
                objetivoService.ponerAlDia(List.of(habito), hoy), habito);
        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodo(hoy);
        Hechos hechos = Hechos.de(registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]),
                historial::objetivoEl);
        boolean enMinutos = habito.getUnidad() == Unidad.MINUTOS;
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("completadoHoy", hechos.unidadesEl(hoy) > 0);
        estado.put("completadosPeriodo", hechos.unidades());
        estado.put("meta", habito.getMeta());
        estado.put("minutosHoy", enMinutos ? hechos.cantidadEl(hoy) : null);
        estado.put("minutosObjetivo", enMinutos ? habito.getMinutosObjetivo() : null);
        return estado;
    }

    // B2: los hitos ya no cuelgan de la racha (ver CalculoPremio.hitosAPagar).
    // Se conserva comentado.
    //    /**
    //     * Paga todos los hitos que la racha ha cruzado en este completado, no solo
    //     * el del valor exacto. Con el completado retroactivo la racha puede saltar
    //     * —rellenar un hueco que une dos tramos lleva de 2 a 5— y con un switch
    //     * sobre el valor exacto esos hitos intermedios no se cobraban nunca.
    //     *
    //     * En el camino normal la racha sube de uno en uno, así que el intervalo
    //     * (previa, actual] contiene un único valor y el comportamiento es idéntico
    //     * al de antes. El límite de la semana en curso acota el salto máximo.
    //     *
    //     * Los hitos siguen siendo recobrables: si la racha se rompe y se reconstruye,
    //     * se vuelven a pagar. Eso ya era así y no se cambia aquí.
    //     */
    //    // Misma razón que en actualizarRacha: la instancia la da quien llama.
    //    private int otorgarPuntosPorHitoRacha(Usuario usuario, Habito habito, Racha racha,
    //                                          Integer rachaPrevia) {
    //        if (racha == null) return 0;
    //
    //        int actual = racha.getRachaActual();
    //        int desde = rachaPrevia != null ? rachaPrevia : 0;
    //        if (actual <= desde) return 0;
    //
    //        int puntos = 0;
    //        for (int[] hito : HITOS_RACHA) {
    //            if (hito[0] > desde && hito[0] <= actual) {
    //                puntos += hito[1];
    //            }
    //        }
    //
    //        if (puntos > 0) {
    //            usuarioMonedaService.registrarMovimiento(
    //                    usuario, puntos, "HITO_RACHA", habito.getHabitoId(),
    //                    "Hito de racha (" + actual + ") en: " + habito.getNombre()
    //            );
    //        }
    //        return puntos;
    //    }

    public List<Registro> obtenerRegistros(Habito habito) {
        return registroDAO.findByHabito(habito);
    }

    public Registro buscarPorId(int registroId) {
        return registroDAO.findById(registroId);
    }

    public void actualizarNota(int registroId, String nota) {
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        registro.setNota(nota);
        registroDAO.update(registro);

        Usuario usuario = registro.getHabito().getPropietario();
        logrosHabitosService.evaluarTrasAnadirNota(usuario);
    }

    public void actualizarValoracion(int registroId, Integer valoracion) {
        if (valoracion == null || valoracion < 1 || valoracion > 5) {
            throw new IllegalArgumentException("La valoración debe estar entre 1 y 5");
        }
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        registro.setValoracion(valoracion);
        registroDAO.update(registro);
    }

    /**
     * Deshace un hábito completado, revirtiendo con exactitud a partir de la
     * instantánea capturada al completarlo. Se mantiene la restricción de que
     * debe ser el último registro del hábito: así una reversión nunca pisa los
     * efectos de acciones posteriores independientes. La fecha puede ser hoy
     * o cualquier fecha pasada.
     */
    @Transactional
    public Map<String, Object> deshacerRegistro(int registroId) {
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        Habito habito = registro.getHabito();
        // Igual que al completar: de uno en uno por usuario y hábito, para que
        // "el último registro" no cambie entre comprobarlo y borrarlo, y para
        // no deshacer en medio de un completado de otro hábito suyo que está
        // midiendo su saldo y sus logros. Con el hábito releído, como al
        // completar.
        habito = habitoService.bloquear(habito);
        // Un hábito en pausa no se deshace: la pausa lo para todo.
        if (!habito.isActivo()) {
            throw new ConflictoException("El hábito está en pausa");
        }
        Usuario usuario = habito.getPropietario();

        // Solo se puede deshacer el último registro del hábito.
        List<Registro> registrosHabito = registroDAO.findByHabito(habito);
        Registro ultimo = null;
        for (Registro r : registrosHabito) {
            if (ultimo == null || r.getRegistroId() > ultimo.getRegistroId()) {
                ultimo = r;
            }
        }
        if (ultimo == null || ultimo.getRegistroId() != registroId) {
            throw new ConflictoException("Solo se puede deshacer el último completado");
        }

        // Mismo suelo que el completado retroactivo: la semana en curso. Sin
        // esto se puede deshacer un registro de hace meses y romper la racha
        // hacia atrás, que es justo lo que el límite de completar protege. Las
        // dos puertas al mismo sitio: lo que no se puede marcar, tampoco se
        // desmarca.
        LocalDate hoy = rachaService.hoyDe(habito);
        LocalDate lunesDeEstaSemana = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        if (registro.getFecha().isBefore(lunesDeEstaSemana)) {
            throw new ConflictoException("Solo se puede deshacer un completado de la semana en curso");
        }

        ReversionRegistro reversion = reversionRegistroDAO.findByRegistro(registroId);
        if (reversion == null) {
            throw new ConflictoException(
                    "Este completado es anterior al sistema de deshacer y no se puede revertir");
        }

        // Retira los logros para que puedan reconcederse. Sus monedas ya van
        // dentro del delta que se compensa a continuación.
        List<Integer> logrosRetirados = new ArrayList<>();
        for (ReversionLogro reversionLogro : reversion.getLogros()) {
            usuarioLogroDAO.deleteByUsuarioYLogro(usuario.getUsuarioId(), reversionLogro.getLogroRef());
            logrosRetirados.add(reversionLogro.getLogroRef());
        }

        // Las monedas no se borran, se compensan: el libro es append-only.
        // Se permite saldo negativo.
        int monedasOtorgadas = reversion.getMonedasOtorgadas();
        if (monedasOtorgadas != 0) {
            usuarioMonedaService.registrarMovimiento(usuario, -monedasOtorgadas, "DESHACER_HABITO",
                    habito.getHabitoId(), "Deshecho: " + habito.getNombre());
        }

        if (reversion.getRachaActualPrevia() != null) {
            Racha racha = rachaDAO.findByHabito(habito);
            racha.setRachaActual(reversion.getRachaActualPrevia());
            racha.setRachaMaxima(reversion.getRachaMaximaPrevia());
            racha.setPeriodoMetaAlcanzada(reversion.getPeriodoMetaAlcanzadaPrevio());
            racha.setUltimaFecha(reversion.getUltimaFechaPrevia());
            rachaDAO.update(racha);
        }

        // La XP se compensa con lo que dio este completado, igual que las
        // monedas: fijarla al valor previo borraría la ganada después por
        // otros hábitos o por la comida. Null = reversión anterior a esta
        // columna: no hay nada fiable que restaurar en la mascota.
        boolean mascotaRegistrada = reversion.getMascotaExperienciaOtorgada() != null;
        LocalDate diaCompletoPrevio = reversion.getMascotaDiaCompletoPrevio();
        if (mascotaRegistrada) {
            mascotaService.restarExperiencia(usuario.getUsuarioId(),
                    reversion.getMascotaExperienciaOtorgada());
        }

        // Los hitos que pagó este completado salen del libro, para que se
        // puedan volver a cobrar; sus puntos ya van en el delta de monedas.
        // La reversión y los hitos primero y el registro después: importa
        // por la FK.
        hitoPagadoDAO.deleteByRegistro(registro.getRegistroId());
        reversionRegistroDAO.delete(reversion.getReversionId());
        registroDAO.delete(registro.getRegistroId());

        // La fecha del último día completo sólo retrocede si se deshace algo
        // de hoy y, sin ello, el día deja de estar completo. Completar un día
        // pasado nunca la pone, y deshacer un semanal no rompe el día
        // (esDiaCompleto sólo mira los diarios). Va después de borrar a
        // propósito: la consulta de esDiaCompleto es JPQL, así que Hibernate
        // hace flush antes y el registro borrado ya no cuenta.
        if (mascotaRegistrada && registro.getFecha().equals(hoy)
                && !habitoService.esDiaCompleto(usuario)) {
            mascotaService.restaurarDiaCompleto(usuario.getUsuarioId(), diaCompletoPrevio);
        }

        return Map.of(
                "monedasDevueltas", monedasOtorgadas,
                "logrosRetirados", logrosRetirados
        );
    }
}
