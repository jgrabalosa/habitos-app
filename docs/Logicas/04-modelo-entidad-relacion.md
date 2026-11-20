# Norday Habits — Modelo Entidad-Relación

*Última actualización: noviembre de 2026*

> Este documento describe el modelo funcional y las principales relaciones de datos actualmente implementadas en el backend. No debe interpretarse como especificación de funcionalidades futuras.

---

## 1. Arquitectura de datos

El backend de Norday está organizado en módulos:

- `norday-motor`: cuentas, autenticación, preferencias, gamificación, versión mínima por aplicación y funcionalidades compartidas del ecosistema.
- `habitos`: hábitos, registros, ritmo, pausas, racha heredada, logros específicos de hábitos y notificaciones relacionadas.
- `conocimiento`: ramas (categorías), temarios, píldoras con sus tarjetas y retos, fuentes, preferencias, valoraciones, lecturas, repaso espaciado y respuestas.
- `enfoque`: sesiones de concentración de Norday Focus.
- `norday-server`: aplicación Spring Boot que integra y expone los módulos.

La cuenta de usuario y la gamificación son compartidas por las aplicaciones del ecosistema.

Este documento cubre el motor, el dominio de Norday Habits y las sesiones de Norday Focus (`enfoque`). De Norday Conocimiento describe las tablas del producto mínimo (V37-V39, apartado «Norday Conocimiento»); las de V4 y V5 (`Categoria`, `Pildora`, `PildoraCategoria`, `UsuarioPildora`, `UsuarioCategoriaPreferencia` y `ValoracionPildora`) se describen en su propio módulo, en `conocimiento/src/main/java/com/norday/conocimiento/model/`.

---

## 2. Entidades principales

### Usuario

Representa la cuenta común del ecosistema.

Entre sus datos y relaciones se encuentran:

- identidad de usuario y credenciales;
- preferencias;
- idioma y zona horaria;
- `avisos_app` (V32): la preferencia «avisos de la app», `BOOLEAN NOT NULL DEFAULT TRUE`. Encendida para todos, incluidos los usuarios que ya existían. Es genérica (los avisos que manda la propia app, aparte de los recordatorios que el usuario pone a algo suyo) y solo cambia por `PUT /api/usuarios/{id}/preferencias` (ver «Preferencias de cuenta» en la sección 6);
- tokens de notificaciones, uno por app (`UsuarioDispositivo`);
- productos/equipamiento;
- moneda y movimientos de gamificación;
- logros;
- mascota;
- datos de autenticación y recuperación de contraseña.

El backend es la fuente de verdad para el estado persistente del usuario.

### CodigoRecuperacion

Código temporal para restablecer la contraseña. Va ligado al email, no al usuario, y tiene una fecha de caducidad. Es parte del motor de cuenta.

### Hábito

Representa un hábito creado por el usuario.

Está relacionado con:

- usuario propietario;
- categoría;
- registros de cumplimiento;
- historial de objetivos (ver HabitoObjetivo);
- ritmo (una fila por hábito; ver Ritmo);
- racha heredada;
- logros relacionados con hábitos.

El hábito contiene la `meta` que determina cuándo se considera cumplido el periodo correspondiente. `unidad`, `frecuencia`, `meta`, `minutos_objetivo` y `dias_semana` son una copia del objetivo vigente hoy; cada día se juzga con la versión de su fecha en `habito_objetivo`.

- `unidad`: obligatorio, por defecto `VECES` (V23). `VECES` o `MINUTOS` (ver «Hábitos en minutos» en `03-calculos-y-logica.md`).
- `minutos_objetivo`: admite nulo (V23); los minutos por día de un hábito de minutos, de 1 a 600. Nulo en los de veces.
- `icono`: admite nulo, hasta 40 caracteres (V31). El código del icono que dibuja la app; el backend no sabe cuáles existen. Va solo en `habito`: no es parte del objetivo, no hay columna en `habito_objetivo` y cambiarlo no crea versión.

Su campo `activo` dice si el hábito está en pausa: un hábito con `activo = false` está en pausa (ver Pausa).

### Categoría

Agrupa hábitos.

Las categorías pueden ser del catálogo existente o creadas por el usuario. La creación y uso de categorías interviene también en los logros relacionados con variedad.

### Registro

Representa el cumplimiento de un hábito en una fecha determinada.

Un registro se relaciona con:

- un hábito;
- una fecha;
- el estado de completado;
- información necesaria para revertir los efectos de un completado cuando corresponde.

Columnas de los hábitos en minutos (V23):

- `cantidad`: obligatorio, por defecto 1. Lo que aporta el registro a su día: los minutos (de 1 a 600) en un hábito de minutos, 1 en uno de veces. Los «hechos» de un día son la suma de `cantidad`; en veces coincide con el número de registros.
- `origen`: obligatorio, por defecto `MANUAL`. `MANUAL` o `ENFOQUE`; hoy el backend solo lo guarda.
- `clave_cliente`: admite nulo; tipo UUID. La clave que manda la app con cada completado para que un reintento no cree otro registro. Restricción única `UK_registro_clave_cliente`. No sale en ninguna respuesta.

El registro es la fuente de verdad: nunca se reescribe al cambiar el objetivo.

La lógica de registros es responsable de aplicar los efectos de gamificación derivados del cumplimiento.

Un hábito en pausa no se puede completar: `completarHabito` responde con un conflicto («El hábito está en pausa»).

### ReversionRegistro y ReversionLogro

Permiten deshacer un completado con exactitud. Cada completado guarda una `ReversionRegistro` ligada a su `Registro` con:

- las monedas que otorgó ese completado (el propio completado, el hito de días hechos y los logros) y la experiencia que dio a la mascota;
- el estado previo de la racha: `rachaActual`, `rachaMaxima`, periodo de meta alcanzada y última fecha;
- el día completo previo de la mascota.

Las monedas y la experiencia se guardan como lo que dio el completado, no como una instantánea: al deshacer se restan, para no borrar lo ganado después. Los campos de estado previo admiten nulo cuando no hay nada que restaurar (un hábito sin racha, un usuario sin mascota).

Cada logro desbloqueado por ese completado, incluidos los que dispara indirectamente la mascota, queda en una `ReversionLogro` que cuelga de la reversión, para poder retirarlo si el completado se deshace.

### HitoPagado

El libro de hitos (tabla `hito_pagado`): una fila por cada hito de días hechos que un hábito ya ha cobrado, para no pagarlo dos veces (ver «Hitos por días hechos» en `03-calculos-y-logica.md`).

- `id`: clave primaria.
- `habito_ref`: obligatorio; clave ajena `FK_hito_pagado_habito` a `habito`.
- `umbral`: obligatorio. Los días hechos del hito (3, 7, 30, 100 o 365).
- `registro_ref`: admite nulo; clave ajena `FK_hito_pagado_registro` a `registro`. El completado que lo pagó: deshacerlo borra la fila. El relleno de V21 lo liga al registro que cerró el día hecho del umbral.
- `pagado_en`: obligatorio, en UTC.

Restricción única `UK_hito_pagado_habito_umbral` sobre (`habito_ref`, `umbral`).

### HabitoObjetivo

El historial de objetivos (tabla `habito_objetivo`): una fila por cada versión del objetivo de un hábito, vigente desde `desde` hasta la víspera de la siguiente (ver «Historial de objetivos y cambios» en `03-calculos-y-logica.md`). Como mucho una tiene `desde` futura: el cambio programado.

- `id`: clave primaria.
- `habito_ref`: obligatorio; clave ajena `FK_habito_objetivo_habito` a `habito`.
- `desde`: obligatorio.
- `unidad`: obligatorio, por defecto `VECES`; `VECES` o `MINUTOS`.
- `frecuencia`: obligatorio (`DIARIO` o `SEMANAL`).
- `meta`: obligatorio.
- `minutos_objetivo`: admite nulo; nulo en los hábitos de veces, de 1 a 600 en los de minutos.
- `dias_semana`: admite nulo; mismo formato que en `habito`.
- `creado_en`: obligatorio, en UTC.

Restricción única `UK_habito_objetivo_habito_desde` sobre (`habito_ref`, `desde`). El relleno de V22 dio a cada hábito existente una versión desde su `fecha_inicio`, con su objetivo de entonces.

### Racha (heredada)

La tabla `racha` sigue existiendo, pero ya no se escribe: desde el 21-oct-2026 `RegistroService` no la actualiza al completar un hábito y `HabitoService.actualizar` no la reinicia al cambiar la frecuencia. Los valores guardados quedan congelados.

Es una racha heredada:

- ya no da puntos ni cuenta para los logros de constancia: el hito de racha ya no se genera (el bloque que lo creaba está comentado), y los hitos y logros van por días hechos del hábito;
- el backend sigue enviándola en el detalle del hábito (`rachaActual` y `rachaMaxima` en `HabitoDetalleDTO`), porque la app ya instalada la lee; como ya no se actualiza, `rachaActual` acaba en cero por la rotura perezosa y `rachaMaxima` no cambia; la app nueva lee el ritmo;
- su rotura sigue siendo perezosa: `Racha.sigueViva` y `RachaService.rachaActualVigente` ponen `rachaActual` a cero cuando se lee una racha rota;
- el aviso de racha en peligro (`NotificadorRachaEnPeligro`) existe, pero su programación está comentada: hoy no se envía.

Al deshacer un completado, la racha vuelve a su estado previo a partir de la `ReversionRegistro`; como la racha ya no cambia, restaura los mismos valores.

### Ritmo

Es la medida que sustituye a la racha de cara al usuario: un valor de 0 a 1 por hábito, y su nivel de 1 a 5.

Se guarda como un ancla, con una fila por hábito (`habito_ref` es único):

- `ritmo_id`: clave primaria.
- `habito_ref`: obligatorio y único; clave ajena `FK_ritmo_habito` a `habito`.
- `valor`: obligatorio. El ritmo, de 0 a 1, al empezar el día `desde`.
- `desde`: obligatorio. El día del ancla.
- `valor_previo`: admite nulo. El ritmo del ancla anterior, para calcular el de hace siete días. Es nulo mientras el ancla no haya avanzado nunca, y al reanudar una pausa vuelve a serlo.
- `desde_previo`: admite nulo. El día de esa ancla anterior.
- `cambio`: admite nulo. El día del último cambio de frecuencia u objetivo, y también el de la última pausa o reanudación.

El ritmo de hoy no se guarda. Se calcula al leer, repasando los registros desde el inicio del periodo de `desde`. Por eso completar o deshacer un hábito no escribe en esta tabla. El ancla solo se mueve en tres casos: al empezar una semana nueva, avanzando como mucho hasta el lunes de la semana en curso; al cambiar la frecuencia o el objetivo; y al pausar o reanudar el hábito.

El nivel no se guarda: se deriva del valor (ver los umbrales abajo).

Parámetros del ritmo. Son provisionales, como dice el propio código: se ajustan con uso real.

- Valor inicial: 0,5.
- Semivida al subir: 7 días. Semivida al bajar: 21 días.
- Umbrales de nivel: el nivel 2 empieza en 0,40; el 3 en 0,60; el 4 en 0,80; el 5 en 0,95.
- Días de entrada en el ritmo general: 14. Un hábito nuevo va ganando peso en el ritmo general hasta contar como los demás.
- Tendencia: sube si el ritmo ha cambiado en 0,01 o más en siete días; baja si ha cambiado en −0,045 o menos.
- Hábitos necesarios para el logro «Todos a una»: 3.

El ritmo general es la media ponderada de los ritmos de los hábitos activos.

### Pausa

No tiene tabla propia. Un hábito en pausa es un hábito con `activo = false`.

Pausar gasta una unidad del producto «Pausa» del inventario (código `PAUSA_HABITO`), ancla el ritmo en hoy con el valor que tiene y deja el hábito inactivo. Si no quedan unidades, responde con un conflicto («No te quedan unidades de PAUSA_HABITO») y no cambia nada; si el hábito ya estaba en pausa, también responde con un conflicto («El hábito ya está en pausa»).

Mientras está en pausa:

- sale de Hoy, que solo lee los hábitos activos;
- sale del ritmo general, que solo cuenta los activos;
- sale de los recordatorios, que solo se envían para los hábitos activos;
- su ritmo se queda congelado donde se pausó, y no se puede completar.

Reanudar es gratis. Si el hábito ya estaba activo, no hace nada. Si estaba en pausa, el ancla pasa al día de la vuelta con el valor que tenía al pausar, el día de cambio pasa a ser ese mismo día, y se borran `valor_previo` y `desde_previo`, para que la tendencia no compare con lo de antes de la pausa. Los días de pausa no se juzgan.

### UsuarioDispositivo

Tabla `usuario_dispositivo`, del módulo `norday-motor` (paquete `core`, migración V24). Guarda el token de notificaciones (FCM) de cada usuario en cada app del ecosistema: FCM genera un token por instancia de app, así que Norday Habits y Norday Focus en el mismo móvil tienen dos tokens distintos.

- `id`: clave primaria.
- `usuario_ref`: obligatoria, clave ajena a `usuario` (`FK_usuario_dispositivo_usuario`).
- `app_id`: obligatoria, hasta 30 caracteres. El appId que la app manda en la cabecera `X-Norday-App`: `habitos`, `conocimiento` o `enfoque` (`AppNorday`).
- `fcm_token`: obligatorio, hasta 4096 caracteres.
- `actualizado_en`: obligatoria, en UTC. Cuándo registró la app ese token por última vez.

(`usuario_ref`, `app_id`) es único: una fila por usuario y app, que el registro siguiente sustituye. Registrar un token para un usuario se lo quita a cualquier otro que lo tuviera en la misma app (en `habitos`, también de su `usuario.fcm_token`): es el mismo móvil con otra sesión, y los avisos del anterior no deben llegarle. La migración copió cada `usuario.fcm_token` no vacío como `habitos`.

La columna `usuario.fcm_token` sigue existiendo como columna de transición: el backend la escribe con el token de `habitos` para poder volver al jar anterior, y la usa como reserva para `habitos` cuando el usuario no tiene fila (quien registró su token mientras corría el jar anterior). Los envíos salen de `DispositivoService`, con el token de la app a la que va el aviso.

### AvisoApp

Tabla `aviso_app`, del módulo `habitos` (migración V32). El registro de los avisos que la app ha mandado a un usuario: una fila por aviso enviado. Servirá al barrido de avisos para respetar el tope semanal y no repetir texto; esta tabla todavía no la escribe nadie en producción.

- `id`: clave primaria.
- `usuario_id`: obligatoria, clave ajena a `usuario` (`FK_aviso_app_usuario`), sin borrado en cascada.
- `tipo`: obligatorio, hasta 20 caracteres: `VUELTA_1`, `VUELTA_2` o `MOMENTO_DEBIL` (`TipoAviso`, guardado por nombre).
- `variante`: obligatoria, entero. Cuál de los textos del tipo se mandó.
- `enviado_en`: obligatoria, en UTC.

Índice `IX_aviso_app_usuario_enviado` sobre (`usuario_id`, `enviado_en`).

### SesionEnfoque

Tabla `sesion_enfoque`, del módulo `enfoque` (migración V26). Una fila por sesión de concentración de Norday Focus.

- `id`: clave primaria.
- `usuario_ref`: obligatoria, clave ajena a `usuario` (`FK_sesion_enfoque_usuario`).
- `clave`: obligatoria, UUID. La genera la app al empezar la sesión; única (`UK_sesion_enfoque_clave`). Es también la `clave_cliente` del registro que crea en el hábito. No se exporta.
- `inicio_utc`: obligatoria, instante de inicio en UTC.
- `duracion_seg`: obligatoria, segundos que duró (de 0 a 7260).
- `duracion_objetivo_min`: obligatoria, minutos elegidos al empezar (10–120, de 5 en 5).
- `estado`: obligatorio, hasta 12 caracteres: `COMPLETADA`, `TERMINADA` o `PERDIDA`.
- `dia`: obligatorio. El día de inicio en la zona del usuario: el día al que van los minutos.
- `destino_tipo`: admite nulo, hasta 20 caracteres. A dónde iban los minutos (`habito`); nulo, sin destino.
- `destino_ref`: admite nulo, BIGINT. El id del destino; opaco y **sin clave ajena**: el módulo `enfoque` no conoce las tablas del destino, y borrar el hábito no borra la sesión.
- `aporte_estado`: obligatorio, hasta 12 caracteres: `SIN_DESTINO`, `PENDIENTE`, `ACEPTADO`, `REPETIDO` o `RECHAZADO`.
- `aporte_motivo`: admite nulo, hasta 40 caracteres. En un rechazo, el código del motivo (una clave de los textos).
- `minutos_aportados`: obligatorio, por defecto 0. Los minutos que están en el destino por esta sesión.
- `monedas_aportadas` y `xp_aportada`: obligatorios, por defecto 0. Lo que pagó el destino al recibir los minutos; un reintento lo devuelve tal cual.
- `logros_aportados`: admite nulo, hasta 1000 caracteres. Los códigos de los logros que desbloqueó el aporte, separados por comas; nulo si ninguno.
- `creado_en`: obligatoria, en UTC.

Índices: (`usuario_ref`, `dia`) para el resumen y la racha; (`usuario_ref`, `inicio_utc`) para el historial y el solape.

El resultado del aporte se apunta con precedencia: `PENDIENTE` lo acepta todo; después `ACEPTADO` gana a `REPETIDO` y a `RECHAZADO`, y `REPETIDO` gana a `RECHAZADO`. Si dos reintentos simultáneos responden distinto, queda el que pagó.

Deshacer en Norday Habits el registro que creó una sesión no toca la sesión: sus columnas `*_aportad*` se quedan como estaban (limitación conocida, ver 03).

### Norday Conocimiento (V37-V39)

Tablas del módulo `conocimiento` para el producto mínimo. El contenido (`temario`, `fuente`, `pildora_tarjeta`, `reto`, `reto_opcion` y las columnas nuevas de `pildora`) llega del repo `norday_conocimiento_contenido` por un importador que actualiza por `codigo`, nunca por id, y que **retira, nunca borra**: lo que sale del repo pasa a `activo`/`activa = false`, porque las filas de los usuarios siguen apuntando a ello. Las tablas por usuario (`lectura_pildora`, `usuario_reto` y `respuesta_reto`) cuelgan de `usuario` con FK `NOT NULL` y sin cascada.

**`temario`** (V37). Un recorrido ordenado dentro de una rama.

- `temario_id`: clave primaria. `codigo`: obligatorio, hasta 100 caracteres, único (`UK_temario_codigo`).
- `categoria_id`: obligatoria, clave ajena a `categoria_conocimiento` (`FK_temario_categoria`), que es la rama.
- `titulo` (200, obligatorio), `descripcion` (500), `orden` (obligatorio) y `activo` (por defecto verdadero).

**`fuente`** (V37). El libro del «Para saber más» y las fuentes citables; la procedencia afirmación por afirmación se queda en el repo.

- `fuente_id`: clave primaria. `codigo`: obligatorio, hasta 100 caracteres, único (`UK_fuente_codigo`).
- `tipo`: obligatorio, `LIBRO`, `PAPER` o `DOMINIO_PUBLICO`. `titulo` (300, obligatorio), `autor` (200), `anio`, `url` (500) y `licencia` (30).

**Columnas nuevas de `pildora`** (V37).

- `codigo`: hasta 100 caracteres, único (`UK_pildora_codigo`). Admite nulo solo porque se añadió a una tabla existente; toda píldora importada lo trae.
- `temario_id` (FK `FK_pildora_temario`) y `orden_temario`: nulos en las píldoras sueltas, que solo salen en el match. Índice (`temario_id`, `orden_temario`). El par no es único a propósito: al reordenar un temario se incumpliría a mitad del cambio; lo comprueba `validar.py`.
- `nivel` y `version`: obligatorios, por defecto 1. `version` sube con cada cambio de texto en el repo; no es un `@Version` de JPA.
- `fuente_id`: admite nulo, FK `FK_pildora_fuente`. `licencia`: hasta 30 caracteres, sin valor por defecto.
- `preview_corto` pasa a ser el gancho y `contenido_completo` guarda las tarjetas unidas. `libro_origen` queda obsoleto.

**`pildora_tarjeta`** (V38). Las 3-5 tarjetas de una píldora: `pildora_id` (FK), `orden` (desde 1) y `texto` (`TEXT`). (`pildora_id`, `orden`) es único (`UK_pildora_tarjeta_orden`): al rehacer las tarjetas hay que borrar y hacer flush antes de insertar.

**`reto` y `reto_opcion`** (V38). La pregunta de opción múltiple de una píldora (1 o 2 por píldora).

- `reto_id`: clave primaria. `codigo`: obligatorio, hasta 100 caracteres, único (`UK_reto_codigo`). `pildora_id`: obligatoria, FK `FK_reto_pildora`, con índice.
- `enunciado` (500, obligatorio), `indice_correcta` (obligatorio, desde 0 sobre las opciones), `explicacion` (1000) y `activo`.
- `reto_opcion`: las opciones en orden (2 a 4), un `@ElementCollection` con clave primaria (`reto_id`, `orden`) y `texto` (300).

**`lectura_pildora`** (V39). Una fila por lectura terminada.

- `usuario_id` y `pildora_id`: obligatorias, con FK. `clave`: UUID que genera la app, única (`UK_lectura_pildora_clave`): un reintento no crea otra fila ni paga dos veces. No se exporta.
- `dia`: el día en la zona del usuario (racha y tope diario). `leida_en`: el instante, en UTC.
- `premio_monedas`, `premio_xp` (por defecto 0), `premio_logros` (1000, códigos separados por comas) y `premio_tope`: lo que pagó la lectura, como en `sesion_enfoque`.
- Índice (`usuario_id`, `dia`).

**`usuario_reto`** (V39). El estado del repaso espaciado (FSRS) de cada reto para cada usuario; se crea al leer la píldora.

- `usuario_id` y `reto_id`: obligatorias, con FK; el par es único (`UK_usuario_reto`).
- `estabilidad` y `dificultad`: `DOUBLE PRECISION`, por defecto 0. Con `repasos = 0` (todavía sin responder) valen 0 y no significan nada.
- `repasos` y `fallos` (por defecto 0), `ultimo_repaso` (nulo si nunca) y `vence_en` (obligatorio). Fechas en UTC.
- El reto está en la bandeja si `vence_en` ya pasó y el reto sigue activo. Índice (`usuario_id`, `vence_en`).

**`respuesta_reto`** (V39). Una fila por respuesta: `usuario_id` y `reto_id` (FK), `clave` (UUID único, `UK_respuesta_reto_clave`), `opcion` (desde 0), `acierto` (se guarda porque el reto puede cambiar), `dia`, `respondida_en` (UTC) y las mismas columnas `premio_*` que la lectura. Índice (`usuario_id`, `dia`).

`usuario_pildora.estado` gana `EN_COLA` (match a la derecha) y `LEIDA` (lectura terminada); es un `VARCHAR(30)` sin CHECK (V4); V37 quita, si existe, el CHECK que lleva una tabla generada por Hibernate.

### Versión mínima (VersionApp)

Tabla `version_app`, del módulo `norday-motor` (migración V20; la recomendada, V33). Tiene tres columnas:

- `app_id`: clave primaria, texto de hasta 30 caracteres.
- `version_minima`: obligatoria. El `versionCode` de Android que la app necesita; por debajo, la app se bloquea y pide actualizar.
- `version_recomendada` (V33): `INTEGER NULL`. El `versionCode` por debajo del cual la app enseña una tarjeta que invita a actualizar, sin obligar a nada. Nula = sin aviso (todas las filas existentes arrancan así).

Una app sin fila tiene mínima 0 y recomendada nula, así que nunca se bloquea ni se avisa por un dato que falta. La migración V20 deja una fila para `habitos` con mínima 1, que no bloquea a nadie, la V25 otra igual para `enfoque` y la V40 otra para `conocimiento`.

Se cambian a mano con un `UPDATE`, sin compilar ni reiniciar. El orden es siempre el mismo: primero se publica la versión nueva y se ve en Play; después se sube la recomendada y, si hace falta obligar, la mínima. La consulta es `GET /api/version/{appId}`, que devuelve `{"versionMinima":5,"versionRecomendada":9}`; sin recomendada, la clave sale con `null` (`"versionRecomendada":null`).

### Logro

Es el catálogo de logros: código, nombre, descripción, categoría, nivel, puntos, icono y `origenApp`. Un logro del catálogo no pertenece a ningún usuario.

Existen dos grandes grupos:

1. logros genéricos del motor/ecosistema;
2. logros específicos del dominio de hábitos.

Los logros específicos de hábitos utilizan `origenApp = "habitos"`.

Los logros de constancia tienen códigos `RACHA_*`. Esos códigos no cambian, pero ya no miden rachas: `RACHA_3`, `RACHA_7`, `RACHA_10`… hasta `RACHA_365` cuentan las veces que se completa un mismo hábito. Los umbrales son 3, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 100 y 365. `RACHA_RECUPERADA` se concede al volver a un hábito después de unos días sin hacerlo.

Los logros de ritmo (`RITMO_BUENO`, `RITMO_FUERTE`, `RITMO_IMPARABLE`, `RITMO_GENERAL_FUERTE`, `RITMO_TODOS` y `RITMO_VUELTA`) se conceden al leer la pantalla de Hoy, porque el ritmo se mueve al cerrar el día o la semana y no al marcar.

El servicio de dominio determina qué logro corresponde y el servicio genérico de gamificación gestiona su concesión.

### UsuarioLogro

Representa la concesión de un logro a un usuario: qué usuario, qué logro y cuándo lo consiguió. La pareja usuario-logro es única (`UQ_usuario_logro`): un usuario no puede tener el mismo logro dos veces.

### Moneda / movimientos

La economía de puntos se registra mediante movimientos asociados al usuario (`UsuarioMoneda`).

El saldo se obtiene a partir del ledger de movimientos, evitando mantener un saldo independiente como única fuente de verdad.

Los movimientos pueden corresponder, entre otros, a:

- completados de hábitos;
- hitos de días hechos (`HITO_DIAS_HECHOS`): una vez por hábito, al llegar a 3, 7, 30, 100 y 365 días hechos en total, seguidos o no;
- operaciones de gamificación.

El movimiento `HITO_RACHA` ya no se genera: el código que lo creaba está comentado.

### Producto

Representa elementos disponibles en el catálogo de gamificación.

Los productos pueden estar relacionados con:

- temas/identidades;
- comida para la mascota;
- la pausa de hábito (`PAUSA_HABITO`, ver Pausa);
- otros elementos de equipamiento según el catálogo activo.

`coleccion` (`varchar(50)`, nula, V28): la colección de temporada (`INVIERNO_2026`, `NAVIDAD_2026`…), que el cliente traduce por código para agrupar la tienda. Nula en todo lo que no es de temporada.

`version_minima_core` (`varchar(20)`, nula, V30): desde qué versión del core de Flutter (cabecera `X-Norday-Core`) se ve y se vende el producto; nula = todos. No viaja al cliente. Las reglas, en `03`, «Versión mínima del core».

### Oferta

Un periodo en el que un producto se vende a un precio (tabla `oferta`, V28; Nori vestida, N-4):

- `oferta_id` (serial), `producto_id` (FK a `producto`, con índice), `desde` y `hasta` (`date`, días de la zona del usuario, los dos incluidos, `CHECK (desde <= hasta)`) y `precio` (entero, `CHECK (precio >= 0)`; 0 es un regalo).
- `Producto` no tiene relación JPA hacia sus ofertas: el catálogo, el inventario y la exportación devuelven la entidad tal cual, y una relación las metería en los tres (con `open-in-view` activo, una perezosa se cargaría al pasar a JSON, con una consulta por producto). El catálogo las añade con `ProductoCatalogoDTO`, en una sola consulta.
- No cuelga del usuario: no tiene limpiador ni exportador. Las reglas, en `03`, «Ofertas por periodo».

### UsuarioProducto

Relaciona al usuario con los productos adquiridos o disponibles para su cuenta y permite gestionar el equipamiento correspondiente.

### UsuarioEquipado

Lo que lleva puesto el usuario (tabla `usuario_equipado`, V29; Nori vestida, N-5): **una fila por usuario y sitio**, con clave primaria `(usuario_id, categoria)` (el sitio es la categoría del producto: `Tema`, `Cabeza`, `Cuerpo`…) y `usuario_producto_id` único, con FK a `usuario_producto`. Es la fuente de verdad de lo equipado: la base de datos impide dos cosas puestas en el mismo sitio. Cambiar de prenda es actualizar la fila del sitio (`INSERT … ON CONFLICT DO UPDATE`); quitarla, borrarla. `usuario_producto.equipado` se sigue escribiendo, coherente, porque el inventario y la exportación lo devuelven. Al borrar la cuenta, `LimpiadorGamificacion` la vacía antes que `usuario_producto`.

El backend mantiene la información de productos/equipamiento como fuente de verdad.

### Mascota

La mascota forma parte de la gamificación compartida del ecosistema.

Guarda:

- la experiencia acumulada;
- el nombre;
- la fecha de la última comida;
- la fecha del último día completo;
- la fase elegida (`faseElegida`).

El nivel no se guarda: se deriva de la experiencia, y la fase (huevo, cría, adulto) se deriva del nivel. Los umbrales viven en `MascotaService`.

`faseElegida` permite al usuario elegir qué fase de la mascota ve, entre las que ya ha desbloqueado. Sólo afecta a la imagen: no toca experiencia, evolución ni logros. Vale nulo, que significa «la que toque», al nacer y tras cada evolución, para que el cambio se vea.

La mascota puede generar logros indirectamente derivados de acciones realizadas desde hábitos.

---

## 3. Relaciones principales

De forma simplificada:

```text
Usuario
 ├── 1:N ── Hábito
 │           ├── N:1 ── Categoría
 │           ├── 1:N ── Registro ── 1:N ── ReversionRegistro ── 1:N ── ReversionLogro
 │           ├── 1:N ── HitoPagado (N:1 opcional con el Registro que lo pagó)
 │           ├── 1:N ── HabitoObjetivo
 │           ├── ────── Racha (heredada)
 │           └── 1:1 ── Ritmo
 │
 ├── 1:N ── UsuarioDispositivo (una por app)
 ├── 1:N ── SesionEnfoque (destino_ref opaco, sin FK al hábito)
 ├── 1:N ── LecturaPildora ── N:1 ── Pildora
 ├── 1:N ── UsuarioReto ── N:1 ── Reto
 ├── 1:N ── RespuestaReto ── N:1 ── Reto
 ├── 1:N ── UsuarioLogro ── N:1 ── Logro
 ├── 1:N ── UsuarioMoneda
 ├── 1:N ── UsuarioProducto ── N:1 ── Producto ── 1:N ── Oferta
 ├── 1:N ── UsuarioEquipado (una por sitio) ── 1:1 ── UsuarioProducto
 └── 1:1 ── Mascota
```

Y el contenido de Norday Conocimiento:

```text
Categoria (rama) ── 1:N ── Temario ── 1:N ── Pildora
Fuente ── 1:N ── Pildora (el «Para saber más»)
Pildora ── 1:N ── PildoraTarjeta
Pildora ── 1:N ── Reto ── 1:N ── reto_opcion
```

La relación exacta entre entidades y las tablas auxiliares se define en las clases JPA y en el esquema de base de datos desplegado.

---

## 4. Gamificación

La gamificación está separada entre el motor común y el dominio de hábitos.

### Motor común

Gestiona elementos como:

- bienvenida;
- login con Google;
- identidades;
- mascota;
- catálogo de productos;
- moneda;
- logros genéricos.

### Dominio de hábitos

Gestiona los logros derivados de:

- creación de hábitos;
- categorías;
- registros;
- días hechos de un mismo hábito (los logros `RACHA_*`);
- ritmo (los logros `RITMO_*`);
- volumen de actividad;
- primera nota.

Los logros de hábitos se identifican mediante su origen de aplicación.

---

## 5. Eliminaciones de cuenta y portabilidad

La eliminación de una cuenta sigue el patrón de limpieza por responsabilidades utilizado en el backend. Cada módulo aporta su propio limpiador.

El limpiador de hábitos (`LimpiadorHabitos`) recorre los hábitos del usuario y borra, en este orden, el libro de hitos, las reversiones, los registros, la racha, el ritmo y el historial de objetivos de cada uno. Después borra los hábitos y, por último, las categorías personalizadas del usuario. El ritmo y el historial se borran antes que su hábito porque sus claves ajenas (`FK_ritmo_habito`, `FK_habito_objetivo_habito`) no tienen borrado en cascada; el libro de hitos, antes que los registros, porque también apunta a ellos. Eliminar un hábito suelto (`HabitoService.eliminar`) sigue el mismo orden.

`LimpiadorHabitos` borra también el registro de avisos de la app (`aviso_app`), que solo cuelga del usuario, antes que nada del resto.

Los tokens de notificaciones los borra `LimpiadorDispositivos`, del motor. La tabla solo cuelga de `usuario`, así que su orden respecto a los demás limpiadores da igual.

La exportación de datos se organiza mediante los exportadores correspondientes a los dominios del sistema, con el objetivo de mantener separadas las responsabilidades y facilitar la portabilidad de los datos. El exportador de hábitos (`ExportadorHabitos`) incluye, por cada hábito, su racha (si existe), su ritmo (`valor`, `desde`, `valorPrevio`, `desdePrevio` y `cambio`), su historial de objetivos (`objetivos`: `desde`, `unidad`, `frecuencia`, `meta`, `minutosObjetivo` y `diasSemana`), los hitos cobrados (`hitosPagados`: `umbral` y `pagadoEn`) y sus registros (con `cantidad` y `origen`; la clave del cliente no se exporta), además de las categorías personalizadas y los avisos de la app enviados al usuario (`avisosApp`: `tipo`, `variante` y `enviadoEn`, del más reciente al más antiguo). La preferencia `avisos_app` no se exporta aquí: es una columna de `usuario`. Cada hábito lleva su `unidad` y sus `minutosObjetivo`. El objetivo de cada hábito (`frecuencia`, `meta`, `unidad`, `minutosObjetivo`, `diasSemana`) es el vigente hoy según su historial, también si ha llegado un cambio programado que la copia todavía no tiene; se calcula sin escribir nada, porque la exportación corre en una transacción de solo lectura.

El exportador de notificaciones (`ExportadorDispositivos`, sección `notificaciones`) dice en qué apps tiene el usuario un token registrado y desde cuándo (`app`, `actualizadoEn`), sin el token, como tampoco sale `usuario.fcm_token`.

Las sesiones de Norday Focus las borra `LimpiadorEnfoque` (la tabla solo cuelga de `usuario`) y las exporta `ExportadorEnfoque` (sección `enfoque`: `inicioUtc`, `duracionSeg`, `duracionObjetivoMin`, `estado`, `dia`, `minutos`, `destinoTipo`, `destinoId`, `aporteEstado`, `aporteMotivo`, `minutosAportados`, `monedasAportadas`, `xpAportada`, `logrosAportados`, `premioMonedas`, `premioXp`, `premioLogros` y `creadoEn`; sin la clave). Los minutos que una sesión llevó a un hábito son registros del hábito: los borra y exporta el módulo de hábitos.

Lo de Norday Conocimiento lo borra `LimpiadorConocimiento` (estado de píldoras, preferencias, valoraciones, lecturas, repasos y respuestas; todas solo cuelgan de `usuario` y del contenido, que no se toca) y lo exporta `ExportadorConocimiento` (sección `conocimiento`: `pildoras`, `preferenciasCategorias`, `valoraciones`, `lecturas`, `repasos` y `respuestas`; sin las claves, el contenido completo ni las opciones de los retos).

---

## 6. Zona horaria e idioma

La zona horaria del usuario forma parte de la lógica funcional del sistema y se utiliza para interpretar fechas y periodos.

El idioma y otras preferencias se sincronizan con el backend.

### Preferencias de cuenta (contrato)

`GET /api/usuarios/{id}/preferencias` devuelve:

```json
{ "idioma": "es", "zonaHoraria": "Europe/Madrid", "avisosApp": true }
```

`PUT /api/usuarios/{id}/preferencias` recibe un objeto JSON con cualquiera de esos tres campos y devuelve el mismo objeto que el `GET`. Solo el dueño de la cuenta puede llamarlos (403 si no).

- **Cada campo es opcional; ausente o `null` es «sin cambio».** La APK ya instalada manda solo `idioma` y/o `zonaHoraria` (textos): sigue valiendo y no toca `avisosApp`.
- `idioma`: texto, uno de `es`, `en`, `pt`. `zonaHoraria`: texto, una zona de IANA conocida por el JDK. Un valor que no sea texto o no sea válido da 400.
- `avisosApp`: booleano. Se aceptan `true`/`false` y también los textos `"true"`/`"false"` (sin distinguir mayúsculas); cualquier otro valor (`"quizá"`, `0`) da 400 y no cambia nada, para que un valor raro no apague los avisos sin querer. Por defecto, encendido.
- El cuerpo del `PUT` es `Map<String, Object>` en el controlador, no `Map<String, String>`, porque `avisosApp` es booleano. `PreferenciasService.texto` y `PreferenciasService.booleano` convierten cada campo.
- `avisosApp` no se lee ni se escribe por el JSON del registro ni por `PUT /api/usuarios/{id}` (el perfil): `Usuario.avisosApp` va con `@JsonIgnore`.

Esto es especialmente relevante para:

- registros;
- rachas y ritmo;
- notificaciones;
- fechas mostradas al usuario.

---

## 7. Reglas de integridad relevantes

- Un hábito pertenece a un usuario.
- Un usuario tiene como mucho un token de notificaciones por app (`usuario_ref`, `app_id` es único en `usuario_dispositivo`).
- Un registro pertenece a un hábito.
- Una clave del cliente identifica como mucho un registro (`clave_cliente` es único en `registro`).
- Una clave de sesión identifica como mucho una sesión de enfoque (`clave` es único en `sesion_enfoque`). Las sesiones de un usuario no se solapan (lo comprueba el servicio al guardarlas).
- Una clave identifica como mucho una lectura (`clave` es único en `lectura_pildora`) y como mucho una respuesta (`clave` es único en `respuesta_reto`). Un usuario tiene como mucho un repaso por reto (`usuario_id`, `reto_id` es único en `usuario_reto`).
- El contenido de Norday Conocimiento se identifica por su `codigo` (único en `temario`, `fuente`, `pildora` y `reto`) y no se borra: se retira con `activo`/`activa = false`.
- La racha heredada ya no se escribe (apagada el 21-oct-2026); deshacer un completado restaura su estado previo, que no ha cambiado.
- El ritmo de un hábito es una sola fila (`habito_ref` es único). Completar o deshacer no lo escribe: se calcula al leer desde su ancla.
- Un hito se paga una sola vez por hábito: (`habito_ref`, `umbral`) es único en `hito_pagado`. Deshacer el completado que lo pagó borra la fila.
- Un hábito tiene como mucho una versión de objetivo por día (`habito_ref`, `desde` es único en `habito_objetivo`) y como mucho una con `desde` futura.
- Un hábito en pausa no se puede completar, no se pone al día y no entra en Hoy ni en el ritmo general.
- Los efectos de gamificación se registran mediante movimientos y concesiones de logros.
- Los productos y su equipamiento son persistentes y tienen como fuente de verdad el backend.
- Las operaciones de gamificación deben respetar las relaciones y restricciones del usuario propietario.
- Las operaciones de eliminación y exportación deben mantener separadas las responsabilidades de cada dominio.

---

## 8. Notas sobre documentación

Este documento sustituye la descripción antigua del modelo que contenía elementos ya retirados o marcados como pendientes, como determinados valores de frecuencia y estructuras de gamificación que ya no representan el estado actual.

No se incluyen aquí funcionalidades futuras que no estén demostradas en el código actual.
