package com.norday.habitos;

import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.service.DispositivoService;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.AvisosAppScheduler;
import com.norday.habitos.service.ObjetivoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las reglas de los avisos de la app contra un reloj fijo. Los textos se
 * sustituyen por su clave, así que lo que recibe FCM dice qué texto se eligió.
 *
 * Calendario de noviembre de 2026 que usan los tests: lunes 16, viernes 20,
 * lunes 23, viernes 27, lunes 30. Madrid va en UTC+1 y Sao Paulo en UTC-3.
 */
@ExtendWith(MockitoExtension.class)
class AvisosAppSchedulerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    /** Las 19:00 de Madrid un viernes (las 15:00 en Sao Paulo). */
    private static final Instant VIERNES_19_MADRID = Instant.parse("2026-11-20T18:00:00Z");
    /** Las 18:00 de Madrid un viernes. */
    private static final Instant VIERNES_18_MADRID = Instant.parse("2026-11-20T17:00:00Z");

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);

    @Mock
    private IHabitoDAO habitoDAO;
    @Mock
    private IRegistroDAO registroDAO;
    @Mock
    private IAvisoAppDAO avisoAppDAO;
    @Mock
    private DispositivoService dispositivoService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private ZonaUsuarioService zonaUsuarioService;
    @Mock
    private TextosService textosService;
    @Mock
    private ObjetivoService objetivoService;

    @InjectMocks
    private AvisosAppScheduler scheduler;

    /** Token de Habits de cada usuario; el de Focus no se ofrece nunca. */
    private final Map<Integer, String> tokensDeHabits = new HashMap<>();
    private final Map<Integer, List<AvisoApp>> avisos = new HashMap<>();
    private final Map<Integer, LocalDate> ultimaActividad = new HashMap<>();
    private final Map<Integer, List<LocalDateTime>> completados = new HashMap<>();

    @BeforeEach
    void preparar() {
        lenient().when(zonaUsuarioService.zonaDe(any(Usuario.class)))
                .thenAnswer(inv -> ZoneId.of(((Usuario) inv.getArgument(0)).getZonaHoraria()));
        lenient().when(dispositivoService.tokensDe(anyCollection(), eq(AppNorday.HABITOS)))
                .thenAnswer(inv -> {
                    Map<Integer, String> pedidos = new HashMap<>();
                    for (Integer id : (java.util.Collection<Integer>) inv.getArgument(0)) {
                        if (tokensDeHabits.containsKey(id)) pedidos.put(id, tokensDeHabits.get(id));
                    }
                    return pedidos;
                });
        lenient().when(dispositivoService.tokensDe(anyCollection(), eq(AppNorday.ENFOQUE)))
                .thenReturn(Map.of(1, "token-enfoque", 2, "token-enfoque-2"));
        lenient().when(avisoAppDAO.findByUsuarios(anyCollection())).thenAnswer(inv -> avisos);
        lenient().when(registroDAO.ultimaFechaCompletadaPorUsuario(anyCollection())).thenAnswer(inv -> ultimaActividad);
        lenient().when(registroDAO.creadosCompletadosDesde(anyInt(), any(LocalDateTime.class)))
                .thenAnswer(inv -> completados.getOrDefault((Integer) inv.getArgument(0), List.of()));
        lenient().when(textosService.texto(anyString(), any(Usuario.class), any(Object[].class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- datos ----------

    private Usuario usuario(int id, ZoneId zona) {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(id);
        usuario.setZonaHoraria(zona.getId());
        usuario.setAvisosApp(true);
        tokensDeHabits.put(id, "token-" + id);
        return usuario;
    }

    private Habito diario(Usuario dueno, int id) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setActivo(true);
        habito.setPropietario(dueno);
        habito.setFechaInicio(LocalDate.of(2026, 10, 1));
        habito.setRecordatorioActivo(false);
        return habito;
    }

    /** Semanal flexible de veces, antiguo y sin recordatorio: candidato al momento débil. */
    private Habito semanal(Usuario dueno, int id, String nombre, int meta) {
        Habito habito = diario(dueno, id);
        habito.setNombre(nombre);
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(meta);
        return habito;
    }

    private Registro hecho(Habito habito, LocalDate dia, int cantidad) {
        Registro registro = new Registro(habito, true, "", dia);
        registro.setCantidad(cantidad);
        return registro;
    }

    private AvisoApp aviso(Usuario dueno, TipoAviso tipo, int variante, Instant enviado) {
        AvisoApp aviso = new AvisoApp(dueno, tipo, variante);
        aviso.setEnviadoEn(LocalDateTime.ofInstant(enviado, ZoneOffset.UTC));
        return aviso;
    }

    /** Los avisos del usuario, del más reciente al más antiguo. */
    private void conAvisos(Usuario dueno, AvisoApp... delMasRecienteAlMasAntiguo) {
        avisos.put(dueno.getUsuarioId(), List.of(delMasRecienteAlMasAntiguo));
    }

    private void losHabitosSon(Habito... habitos) {
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habitos));
    }

    private void laSemanaTiene(Registro... registros) {
        lenient().when(registroDAO.findByHabitosAndRango(anyList(), any(), any())).thenReturn(List.of(registros));
    }

    private void seEnvia(String token, String tituloClave) {
        verify(notificacionService).enviarNotificacion(eq(token), eq(tituloClave), anyString());
    }

    private void noSeEnviaNada() {
        verify(notificacionService, never()).enviarNotificacion(anyString(), anyString(), anyString());
    }

    private AvisoApp avisoGuardado() {
        ArgumentCaptor<AvisoApp> captor = ArgumentCaptor.forClass(AvisoApp.class);
        verify(avisoAppDAO).save(captor.capture());
        return captor.getValue();
    }

    // ---------- la vuelta ----------

    @Test
    void vueltaALos3DiasSinNada_conSuPrimerTexto() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES); // martes, miércoles y jueves en blanco; hoy es viernes

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        // El usuario 1 acaba en 1 + 1 % 3 = 2 al no tener anterior.
        seEnvia("token-1", "push.aviso.vuelta1.2.titulo");
        AvisoApp guardado = avisoGuardado();
        assertEquals(TipoAviso.VUELTA_1, guardado.getTipo());
        assertEquals(2, guardado.getVariante());
        assertEquals(1, guardado.getUsuario().getUsuarioId());
    }

    @Test
    void antesDeLos3Dias_noSale() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LocalDate.of(2026, 11, 17)); // solo miércoles y jueves en blanco

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
    }

    @Test
    void quienNuncaHaCompletadoNada_noRecibeLaVuelta() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
    }

    @Test
    void laPrimeraVueltaNoSeRepiteEnLaMismaAusencia() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        conAvisos(ana, aviso(ana, TipoAviso.VUELTA_1, 2, VIERNES_19_MADRID));

        // Sábado 21 a las 19:00 locales: sigue ausente, pero la primera ya salió.
        scheduler.enviarAvisos(Instant.parse("2026-11-21T18:00:00Z"));

        noSeEnviaNada();
    }

    @Test
    void laSegundaVueltaSaleALos7DiasSinNada_yNoAntes() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        conAvisos(ana, aviso(ana, TipoAviso.VUELTA_1, 2, VIERNES_19_MADRID));

        // Lunes 23: seis días enteros en blanco. Todavía no.
        scheduler.enviarAvisos(Instant.parse("2026-11-23T18:00:00Z"));
        noSeEnviaNada();

        // Martes 24: siete.
        scheduler.enviarAvisos(Instant.parse("2026-11-24T18:00:00Z"));
        seEnvia("token-1", "push.aviso.vuelta2.2.titulo");
        assertEquals(TipoAviso.VUELTA_2, avisoGuardado().getTipo());
    }

    @Test
    void despuesDeLaSegunda_silencioHastaQueVuelvaACompletarAlgo() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        conAvisos(ana,
                aviso(ana, TipoAviso.VUELTA_2, 2, Instant.parse("2026-11-24T18:00:00Z")),
                aviso(ana, TipoAviso.VUELTA_1, 2, VIERNES_19_MADRID));

        // Lunes 30: dos semanas sin nada y la semana ISO libre de avisos.
        scheduler.enviarAvisos(Instant.parse("2026-11-30T18:00:00Z"));

        noSeEnviaNada();
    }

    @Test
    void alCompletarAlgo_laAusenciaSiguienteVuelveAEmpezar() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        // Completó el miércoles 25, después de las dos vueltas de la ausencia anterior.
        ultimaActividad.put(1, LocalDate.of(2026, 11, 25));
        conAvisos(ana,
                aviso(ana, TipoAviso.VUELTA_2, 2, Instant.parse("2026-11-24T18:00:00Z")),
                aviso(ana, TipoAviso.VUELTA_1, 2, VIERNES_19_MADRID));

        // Lunes 30: cuatro días en blanco desde el 25 → la primera otra vez.
        scheduler.enviarAvisos(Instant.parse("2026-11-30T18:00:00Z"));

        assertEquals(TipoAviso.VUELTA_1, avisoGuardado().getTipo());
    }

    @Test
    void laVueltaSoloSaleALas19Locales() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);

        scheduler.enviarAvisos(Instant.parse("2026-11-20T17:00:00Z")); // 18:00 en Madrid
        scheduler.enviarAvisos(Instant.parse("2026-11-20T19:00:00Z")); // 20:00 en Madrid

        noSeEnviaNada();
    }

    // ---------- el momento débil ----------

    /** Semanal de 3 veces con una hecha el lunes: faltan dos. */
    private Habito semanalConDosPorHacer(Usuario dueno, int id, String nombre) {
        Habito habito = semanal(dueno, id, nombre, 3);
        laSemanaTiene(hecho(habito, LUNES, 1));
        return habito;
    }

    @Test
    void momentoDebil_elViernesALas18_conUnSemanalQueAunCabe_loNombra() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(semanalConDosPorHacer(ana, 10, "Yoga"));

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        seEnvia("token-1", "push.aviso.momentodebil.uno.2.titulo");
        verify(textosService).texto("push.aviso.momentodebil.uno.2.titulo", ana, "Yoga");
        assertEquals(TipoAviso.MOMENTO_DEBIL, avisoGuardado().getTipo());
    }

    @Test
    void momentoDebil_otroDiaOHora_noSale() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(semanalConDosPorHacer(ana, 10, "Yoga"));

        scheduler.enviarAvisos(Instant.parse("2026-11-19T17:00:00Z")); // jueves 18:00
        scheduler.enviarAvisos(Instant.parse("2026-11-21T17:00:00Z")); // sábado 18:00
        scheduler.enviarAvisos(Instant.parse("2026-11-20T16:00:00Z")); // viernes 17:00

        noSeEnviaNada();
    }

    @Test
    void momentoDebil_conVarios_diceCuantosSon() {
        Usuario ana = usuario(1, MADRID);
        Habito yoga = semanal(ana, 10, "Yoga", 3);
        Habito llamar = semanal(ana, 11, "Llamar", 2);
        losHabitosSon(yoga, llamar);
        laSemanaTiene(hecho(yoga, LUNES, 1));

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        verify(textosService).texto("push.aviso.momentodebil.varios.2.titulo", ana, 2);
        verify(notificacionService).enviarNotificacion(eq("token-1"), anyString(), anyString());
        // Un solo aviso para los dos.
        verify(avisoAppDAO).save(any(AvisoApp.class));
    }

    @Test
    void momentoDebil_unSemanalConRecordatorio_nuncaCuenta() {
        Usuario ana = usuario(1, MADRID);
        Habito yoga = semanalConDosPorHacer(ana, 10, "Yoga");
        yoga.setRecordatorioActivo(true);
        yoga.setRecordatorioHora(java.time.LocalTime.of(9, 0));
        losHabitosSon(yoga);

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        noSeEnviaNada();
    }

    @Test
    void momentoDebil_recordatorioActivoPeroSinHora_noRecibeSuRecordatorioAsiQueCuenta() {
        Usuario ana = usuario(1, MADRID);
        Habito yoga = semanalConDosPorHacer(ana, 10, "Yoga");
        yoga.setRecordatorioActivo(true);
        yoga.setRecordatorioHora(null);
        losHabitosSon(yoga);

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        seEnvia("token-1", "push.aviso.momentodebil.uno.2.titulo");
    }

    @Test
    void momentoDebil_unDiarioNoCuenta() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        laSemanaTiene();

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        noSeEnviaNada();
    }

    @Test
    void momentoDebil_soloSiLoQueFaltaCabeEnLosTresDiasQueQuedan() {
        Usuario ana = usuario(1, MADRID);
        // Faltan 4 de 5: no cabe. Faltan 0 de 1: ya está. Faltan 3 de 3: cabe justo.
        Habito noCabe = semanal(ana, 10, "Correr", 5);
        Habito yaEsta = semanal(ana, 11, "Llamar", 1);
        losHabitosSon(noCabe, yaEsta);
        laSemanaTiene(hecho(noCabe, LUNES, 1), hecho(yaEsta, LUNES, 1));

        scheduler.enviarAvisos(VIERNES_18_MADRID);
        noSeEnviaNada();

        Habito justo = semanal(ana, 12, "Yoga", 3);
        losHabitosSon(noCabe, yaEsta, justo);
        scheduler.enviarAvisos(VIERNES_18_MADRID);
        verify(textosService).texto("push.aviso.momentodebil.uno.2.titulo", ana, "Yoga");
    }

    @Test
    void momentoDebil_unSemanalCreadoEstaSemana_noCuenta() {
        Usuario ana = usuario(1, MADRID);
        Habito nuevo = semanalConDosPorHacer(ana, 10, "Yoga");
        nuevo.setFechaInicio(LUNES); // creado el lunes de esta semana: aún no es «antes del lunes»
        losHabitosSon(nuevo);

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        noSeEnviaNada();
    }

    @Test
    void momentoDebil_unSemanalDeMinutos_cuentaDiasHechos_noMinutos() {
        Usuario ana = usuario(1, MADRID);
        Habito correr = semanal(ana, 10, "Correr", 3);
        correr.setUnidad(Unidad.MINUTOS);
        correr.setMinutosObjetivo(30);
        losHabitosSon(correr);
        // 100 minutos en un solo día es un día hecho: faltan 2 días, no «cero minutos».
        laSemanaTiene(hecho(correr, LUNES, 100));

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        verify(textosService).texto("push.aviso.momentodebil.uno.2.titulo", ana, "Correr");
    }

    @Test
    void momentoDebil_unDiaDeMinutosSinLlegarASuObjetivo_noEsDiaHecho() {
        Usuario ana = usuario(1, MADRID);
        Habito correr = semanal(ana, 10, "Correr", 4);
        correr.setUnidad(Unidad.MINUTOS);
        correr.setMinutosObjetivo(30);
        losHabitosSon(correr);
        // Dos días de 10 minutos no llegan a 30: lleva 0 días y faltan 4, no cabe en 3.
        laSemanaTiene(hecho(correr, LUNES, 10), hecho(correr, LUNES.plusDays(1), 10));

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        noSeEnviaNada();
    }

    @Test
    void momentoDebil_usaElObjetivoVigenteEstaSemana() {
        Usuario ana = usuario(1, MADRID);
        // La copia del hábito dice «diario», pero esta semana su objetivo vigente es un semanal de 3.
        Habito habito = diario(ana, 10);
        habito.setNombre("Yoga");
        HabitoObjetivo semanal = new HabitoObjetivo(habito, LUNES, new Objetivo(Frecuencia.SEMANAL, 3, null));
        when(objetivoService.historiales(anyList()))
                .thenReturn(Map.of(10, HistorialObjetivos.de(habito, List.of(semanal))));
        losHabitosSon(habito);
        laSemanaTiene(hecho(habito, LUNES, 1));

        scheduler.enviarAvisos(VIERNES_18_MADRID);

        verify(textosService).texto("push.aviso.momentodebil.uno.2.titulo", ana, "Yoga");
    }

    @Test
    void momentoDebil_comoMuchoUnoPorSemana_ElViernesNoSeRepiteSiElBarridoCorreDosVeces() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(semanalConDosPorHacer(ana, 10, "Yoga"));
        // Ya salió esta misma hora (un reinicio del servicio repite el barrido): el viernes es uno solo.
        conAvisos(ana, aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-20T17:00:30Z")));

        scheduler.enviarAvisos(Instant.parse("2026-11-20T17:30:00Z"));

        noSeEnviaNada();
    }

    // ---------- los límites ----------

    @Test
    void comoMuchoDosPorSemanaIso() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        // Lunes 16 y martes 17: ya van dos esta semana.
        conAvisos(ana,
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-17T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-16T17:00:00Z")));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
    }

    @Test
    void conUnoSoloEstaSemana_aunSale_yLaSemanaSiguienteVuelveAEmpezar() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        // Un aviso el martes 17 y otro el domingo 15 (semana anterior).
        conAvisos(ana,
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-17T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-15T17:00:00Z")));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verify(avisoAppDAO).save(any(AvisoApp.class));
    }

    @Test
    void nuncaDosElMismoDia_yElDiaEsElLocalDelUsuario() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        // 00:30 del viernes en Madrid, pero jueves a las 23:30 en UTC: para ella es hoy.
        conAvisos(ana, aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-19T23:30:00Z")));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
    }

    @Test
    void laSemanaIsoEsLaLocalDelUsuario() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LocalDate.of(2026, 11, 23));
        // Domingo 22 a las 23:30 UTC es lunes 23 a las 00:30 en Madrid: ya es de la semana nueva.
        // Con otro el martes 24 son dos en su semana: el sábado 28, que ya tocaría
        // la vuelta (cuatro días en blanco desde el 23), no sale. Contado por el
        // día UTC, el del domingo caería en la semana anterior y sí saldría.
        conAvisos(ana,
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-24T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-22T23:30:00Z")));

        scheduler.enviarAvisos(Instant.parse("2026-11-28T18:00:00Z"));

        noSeEnviaNada();
    }

    /**
     * Tres avisos de momento débil (viernes 13, 20 y 27) y la última actividad el 12:
     * en cuanto la vuelta toca, solo el tope y la retirada deciden.
     */
    private Usuario retirableConTresAvisos() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LocalDate.of(2026, 11, 12));
        conAvisos(ana,
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-27T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-20T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-13T17:00:00Z")));
        return ana;
    }

    @Test
    void retiradaTras3AvisosSinEfecto_callaDurante30Dias() {
        retirableConTresAvisos();

        // Martes 1 de diciembre: la ventana del último (hasta el 29) está cerrada y no hubo nada.
        scheduler.enviarAvisos(Instant.parse("2026-12-01T18:00:00Z"));
        // Lunes 28 de diciembre: sigue dentro. La ventana del último acaba el 29 de
        // noviembre y el silencio dura 30 días más, hasta el 29 de diciembre.
        scheduler.enviarAvisos(Instant.parse("2026-12-28T18:00:00Z"));

        noSeEnviaNada();
    }

    @Test
    void retirada_pasados30Dias_unAvisoDePrueba() {
        retirableConTresAvisos();

        scheduler.enviarAvisos(Instant.parse("2026-12-30T18:00:00Z"));

        verify(avisoAppDAO).save(any(AvisoApp.class));
    }

    @Test
    void retirada_unRegistroEnLas48hDeUnAviso_laEvita() {
        retirableConTresAvisos();
        // Completó algo el sábado 28 por la mañana: dentro de las 48 h del aviso del viernes 27.
        completados.put(1, List.of(LocalDateTime.of(2026, 11, 28, 10, 0)));

        scheduler.enviarAvisos(Instant.parse("2026-12-01T18:00:00Z"));

        verify(avisoAppDAO).save(any(AvisoApp.class));
    }

    @Test
    void retirada_unRegistroFueraDeLas48h_noLaEvita() {
        retirableConTresAvisos();
        // Pasadas las 48 h del aviso del 27 (hasta el 29 a las 17:00 UTC), y lejos de los otros dos.
        completados.put(1, List.of(LocalDateTime.of(2026, 11, 29, 17, 30)));

        scheduler.enviarAvisos(Instant.parse("2026-12-01T18:00:00Z"));

        noSeEnviaNada();
    }

    @Test
    void retirada_conSoloDosAvisos_noSeRetira() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LocalDate.of(2026, 11, 12));
        conAvisos(ana,
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 1, Instant.parse("2026-11-27T17:00:00Z")),
                aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-20T17:00:00Z")));

        scheduler.enviarAvisos(Instant.parse("2026-12-01T18:00:00Z"));

        verify(avisoAppDAO).save(any(AvisoApp.class));
    }

    @Test
    void retirada_unAvisoSinEfectoAunConVentanaAbierta_cuentaComoSinEfecto() {
        retirableConTresAvisos();

        // Sábado 28 a las 19:00: han pasado 25 horas del último y aún puede servir,
        // pero no sale otro encima mientras se sabe.
        scheduler.enviarAvisos(Instant.parse("2026-11-28T18:00:00Z"));

        noSeEnviaNada();
    }

    @Test
    void retirada_noSeMiranLosRegistrosSiNoHaceFalta() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verify(registroDAO, never()).creadosCompletadosDesde(anyInt(), any(LocalDateTime.class));
    }

    // ---------- a quién ----------

    @Test
    void avisosAppApagado_noRecibeNada() {
        Usuario ana = usuario(1, MADRID);
        ana.setAvisosApp(false);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
        verify(avisoAppDAO, never()).save(any(AvisoApp.class));
    }

    @Test
    void sinHabitosActivos_noSeConsultaNada() {
        when(habitoDAO.findTodosActivos()).thenReturn(List.of());

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verifyNoInteractions(notificacionService, dispositivoService, avisoAppDAO);
    }

    @Test
    void sinTokenDeHabits_noSeEnviaNada_ni_seUsaElDeEnfoque() {
        Usuario ana = usuario(1, MADRID);
        tokensDeHabits.remove(1); // solo tiene el de Focus
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        noSeEnviaNada();
        verify(dispositivoService, never()).tokensDe(anyCollection(), eq(AppNorday.ENFOQUE));
    }

    @Test
    void elAvisoVaAlTokenDeHabits_nuncaAlDeOtraApp() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verify(notificacionService).enviarNotificacion(eq("token-1"), anyString(), anyString());
        verify(notificacionService, never()).enviarNotificacion(eq("token-enfoque"), anyString(), anyString());
    }

    @Test
    void tokenDadoDeBajaEnFcm_seOlvida_yElAvisoNoCuenta() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        when(notificacionService.enviarNotificacion(eq("token-1"), anyString(), anyString())).thenReturn(true);

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verify(dispositivoService).olvidarToken(1, AppNorday.HABITOS, "token-1");
        verify(avisoAppDAO, never()).save(any(AvisoApp.class));
    }

    @Test
    void unUsuarioQueFalla_noDejaSinAvisoAlSiguiente() {
        Usuario ana = usuario(1, MADRID);
        Usuario beto = usuario(2, MADRID);
        losHabitosSon(diario(ana, 10), diario(beto, 20));
        ultimaActividad.put(1, LUNES);
        ultimaActividad.put(2, LUNES);
        when(notificacionService.enviarNotificacion(eq("token-1"), anyString(), anyString()))
                .thenThrow(new IllegalStateException("FCM caído"));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        verify(notificacionService).enviarNotificacion(eq("token-2"), anyString(), anyString());
    }

    // ---------- la hora es la del usuario ----------

    @Test
    void laHoraEsLaLocalDeCadaUsuario_dosZonas() {
        Usuario ana = usuario(1, MADRID);
        Usuario bia = usuario(2, SAO_PAULO);
        losHabitosSon(diario(ana, 10), diario(bia, 20));
        ultimaActividad.put(1, LUNES);
        ultimaActividad.put(2, LUNES);

        // 18:00 UTC: 19:00 en Madrid, 15:00 en Sao Paulo.
        scheduler.enviarAvisos(VIERNES_19_MADRID);
        verify(notificacionService).enviarNotificacion(eq("token-1"), anyString(), anyString());
        verify(notificacionService, never()).enviarNotificacion(eq("token-2"), anyString(), anyString());

        // 22:00 UTC: 23:00 en Madrid, 19:00 en Sao Paulo.
        scheduler.enviarAvisos(Instant.parse("2026-11-20T22:00:00Z"));
        verify(notificacionService).enviarNotificacion(eq("token-2"), anyString(), anyString());
        verify(notificacionService).enviarNotificacion(eq("token-1"), anyString(), anyString()); // sigue siendo una sola vez
    }

    @Test
    void elMomentoDebilTambienEsALas18Locales_deCadaZona() {
        Usuario ana = usuario(1, MADRID);
        Usuario bia = usuario(2, SAO_PAULO);
        Habito yoga = semanal(ana, 10, "Yoga", 3);
        Habito correr = semanal(bia, 20, "Correr", 3);
        losHabitosSon(yoga, correr);
        laSemanaTiene(hecho(yoga, LUNES, 1), hecho(correr, LUNES, 1));

        // 17:00 UTC: 18:00 en Madrid, 14:00 en Sao Paulo.
        scheduler.enviarAvisos(VIERNES_18_MADRID);
        verify(notificacionService).enviarNotificacion(eq("token-1"), anyString(), anyString());
        verify(notificacionService, never()).enviarNotificacion(eq("token-2"), anyString(), anyString());

        // 21:00 UTC: 18:00 en Sao Paulo.
        scheduler.enviarAvisos(Instant.parse("2026-11-20T21:00:00Z"));
        verify(notificacionService).enviarNotificacion(eq("token-2"), anyString(), anyString());
    }

    // ---------- las variantes ----------

    @Test
    void nuncaLaMismaVarianteDosVecesSeguidas() {
        for (int anterior = 1; anterior <= 3; anterior++) {
            org.mockito.Mockito.clearInvocations(avisoAppDAO);
            avisos.clear();
            Usuario ana = usuario(1, MADRID);
            when(habitoDAO.findTodosActivos()).thenReturn(List.of(diario(ana, 10)));
            ultimaActividad.put(1, LocalDate.of(2026, 11, 25));
            // Su vuelta de una ausencia anterior (3 semanas antes) con esa variante.
            conAvisos(ana, aviso(ana, TipoAviso.VUELTA_1, anterior, Instant.parse("2026-11-04T18:00:00Z")));

            scheduler.enviarAvisos(Instant.parse("2026-11-30T18:00:00Z"));

            AvisoApp nuevo = avisoGuardado();
            assertNotEquals(anterior, nuevo.getVariante(), "repitió la variante " + anterior);
            assertEquals(anterior % 3 + 1, nuevo.getVariante());
        }
    }

    @Test
    void laVarianteSeCompara_soloConLasDelMismoTipo() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        // Su último aviso fue un momento débil de variante 2: no condiciona la de la vuelta.
        conAvisos(ana, aviso(ana, TipoAviso.MOMENTO_DEBIL, 2, Instant.parse("2026-11-16T17:00:00Z")));

        scheduler.enviarAvisos(VIERNES_19_MADRID);

        assertEquals(2, avisoGuardado().getVariante()); // 1 + 1 % 3, la de quien no tiene anterior
    }

    // ---------- el reloj ----------

    @Test
    void elRelojSeInyecta_yElBarridoProgramadoLoUsa() {
        Usuario ana = usuario(1, MADRID);
        losHabitosSon(diario(ana, 10));
        ultimaActividad.put(1, LUNES);
        scheduler.setReloj(Clock.fixed(VIERNES_19_MADRID, ZoneOffset.UTC));

        scheduler.enviarAvisos();

        seEnvia("token-1", "push.aviso.vuelta1.2.titulo");
    }
}
