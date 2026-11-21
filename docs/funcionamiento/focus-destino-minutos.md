# Norday Focus: el puerto DestinoMinutos

Movido desde `CLAUDE.md` (T-005a).

Una sesión de Norday Focus puede llevar sus minutos a un hábito de minutos.
`enfoque` no conoce los hábitos ni `habitos` conoce las sesiones: el motor
declara el puerto `DestinoMinutos` (`gamificacion.service`, con los records
`DestinoDisponible`, `ResultadoAporte` y `ProgresoDestino` en
`gamificacion.model`) y `habitos`
lo implementa en `DestinoMinutosHabitos` (tipo `habito`). `enfoque` recibe
todos los destinos como `List<DestinoMinutos>` y elige por `tipo()`.

- **El destino nunca lanza por sus reglas.** `DestinoMinutosHabitos.aportar`
  valida antes de completar (dueño, que exista, que no esté en pausa, que el
  día sea de minutos en la versión vigente ese día, semana en curso, no
  futuro y no anterior al alta del hábito, de 1 a 600) y responde `RECHAZADO` con un código de motivo, que es
  una clave de `mensajes/habitos`. Si completar lanza igualmente una
  `ConflictoException` (una carrera), se traduce a `RECHAZADO`. Un fallo
  técnico, o un `IllegalArgumentException` inesperado, sí sale: la sesión
  queda `PENDIENTE`, `EnfoqueController` responde 503 y la app reintenta con
  la misma clave (5xx o sin respuesta = reintentar; 400 y 409 = definitivo).
  Una espera de bloqueo agotada también es 503, con el texto del resto de la
  API (`error.bloqueo.reintentar`): el manejador de `EnfoqueController` va
  antes que el global para todo `DataAccessException` y lo distingue. Si la
  cuenta se borra mientras el alta espera el bloqueo del usuario, 404.
- **Registrar una sesión son tres transacciones, no una**
  (`SesionEnfoqueService.registrar`, sin `@Transactional`): guardar la sesión
  (idempotente por clave, aporte `PENDIENTE` si hay destino), aportar en la
  transacción propia del destino, apuntar el resultado. Es la lección de
  `asegurarIdentidad`: con una transacción compartida, la excepción de
  completar la marcaría para deshacer y la sesión se perdería con
  `UnexpectedRollbackException`. Hay un test que lo demuestra
  (`SesionEnfoqueTest.rechazoDentroDeCompletar_noSeLlevaPorDelanteLaSesion`).
- **El adaptador corre en `REQUIRES_NEW` y captura fuera de la plantilla.**
  Capturar dentro no sirve: completar ya habría marcado la transacción.
  Sin transacción exterior, `REQUIRES_NEW` no pide una segunda conexión.
- **Quien llama a `aportar` no puede tener una transacción abierta.**
  Completar bloquea al usuario en su transacción, con otra conexión; si la
  de fuera ya lo tuviera bloqueado (el alta de la sesión lo bloquea), las
  dos se esperarían hasta agotar el tope, y Postgres no lo ve como abrazo
  (son dos conexiones del mismo hilo). `DestinoMinutosHabitos.aportar`
  lanza `IllegalStateException` si al entrar hay una transacción activa.
- **La clave de la sesión es la clave del registro.** Un reintento no suma
  ni paga dos veces, ni en `sesion_enfoque` ni en `registro`; una sesión que
  quedó `PENDIENTE` (el proceso murió entre aportar y apuntar) se completa
  al reintentar, con `REPETIDO`.
- **El reintento devuelve el resultado original.** La sesión guarda lo que
  pagó el hábito (`monedas_aportadas`, `xp_aportada`, `logros_aportados`) y
  el 200 lo devuelve; el progreso del día se lee en el momento con
  `DestinoMinutos.progreso`, que solo lee. Nunca se vuelve a llamar a
  `aportar` para una sesión cerrada.
- **El resultado se apunta con precedencia** (en el propio `UPDATE`):
  `PENDIENTE` lo acepta todo, `ACEPTADO` gana a `REPETIDO` y `RECHAZADO`,
  `REPETIDO` gana a `RECHAZADO`.
- **Limitación conocida:** deshacer en Norday Habits un registro que vino de
  una sesión no toca la sesión (sus `minutos_aportados` se quedan), y si la
  sesión estaba `PENDIENTE`, un reintento posterior volvería a sumar.
- **Enfoque paga lo suyo, aparte del hábito** (desde el 16-nov-2026,
  `docs/decisiones/2026-11-16-focus-paga-lo-suyo.md`): cada sesión que cuenta
  paga monedas y XP (`PremioEnfoqueService`, cifras en
  `ParametrosPremioEnfoque`, tope de 120 monedas al día) y los logros de
  Focus (`LogrosEnfoqueService`), tenga hábito o no. **Se paga en el paso 1**,
  dentro de la transacción del alta y con el usuario ya bloqueado, y se guarda
  en la sesión (`premio_*`, V27): el reintento devuelve el guardado. Si lleva
  hábito, el hábito paga además lo suyo, con sus reglas, igual que el panel
  de minutos, y la respuesta lo separa: `aporte` (hábito) y `premio` (sesión).
- **El ánimo de Nori junta las apps** (`CumplimientoCombinado`, motor): cada
  app implementa `CumplimientoDiarioPort` con `enUso`, y solo cuentan las que
  el usuario usa. Con una sola, el ánimo es el suyo, sin cambios. Una app
  nueva que mueva el ánimo aporta su implementación.
