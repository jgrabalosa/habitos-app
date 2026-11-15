# Norday Habits — Cálculos y Lógica de Negocio

*Última actualización: noviembre de 2026*

> Este documento describe la lógica funcional actualmente implementada. Los valores indicados aquí se basan en el código actual del backend y no deben interpretarse como promesas de producto distintas de lo que esté disponible en la aplicación.

---

## Ritmo

El ritmo es la medida de constancia que la aplicación muestra a cada hábito. Va de 0 a 1 y se convierte en un nivel de 1 a 5. Su modelo (el ancla que se guarda en la base de datos) está descrito en el documento de modelo de datos; aquí va el cálculo.

### Cálculo de un periodo

Un periodo es un día en un hábito diario y una semana natural (de lunes a domingo) en un hábito semanal.

- **Crédito de un periodo**: lo que se hizo del objetivo, de 0 a 1. Es `min(1, hechos / meta)`. Lo parcial cuenta en proporción (3 de 4 vale 0,75) y pasarse del objetivo no suma más. Un objetivo menor que 1 se trata como 1. En un hábito de minutos, cada día aporta `min(1, minutos del día / minutos objetivo)` (ver «Hábitos en minutos»).
- **Cierre de un periodo**: el ritmo se acerca al crédito del periodo. La distancia que recorre depende de los días del periodo (7 en un semanal, 1 en un diario) y de la semivida: `ritmo + (1 − 0,5^(días / semivida)) × (crédito − ritmo)`.
- **Semivida**: corta cuando el periodo sube el ritmo (crédito igual o mayor que el ritmo) y larga cuando lo baja. Así el ritmo sube deprisa y baja despacio.
- **Periodo abierto**: el periodo que contiene hoy no mueve el ritmo. Solo se cierran los periodos que ya han terminado.
- **Primer periodo**: el periodo que contiene la fecha de inicio del hábito solo puede subir el ritmo. Un hábito casi nunca nace al empezar un periodo, y no había tiempo de cumplirlo entero.

Los periodos terminados se cierran en orden, del más antiguo al más reciente, empezando por el ancla.

### Ancla y cuándo avanza

El ritmo de hoy no se guarda: se calcula al leer, repasando los registros desde el inicio del periodo del ancla. El ancla avanza en tres casos:

- al empezar una semana nueva, hasta el lunes de la semana en curso como máximo; lo anterior ya no puede cambiar;
- al cambiar la frecuencia o el objetivo del hábito;
- al pausar o reanudar el hábito.

Al mover el ancla se guarda también la anterior, para calcular el ritmo de hace siete días.

### Ritmo general

El ritmo general es la media de los ritmos de los hábitos activos, ponderada por el peso de cada hábito. El peso sube desde cero el día que se crea el hábito, un catorceavo por día, hasta contar entero a los 14 días. Si ningún hábito pesa todavía, se hace la media simple: tiene que haber algo que enseñar desde el primer día. Sin hábitos activos no hay ritmo general.

### Niveles y tendencia

Nivel de un ritmo:

- nivel 1: menos de 0,40;
- nivel 2: desde 0,40;
- nivel 3: desde 0,60;
- nivel 4: desde 0,80;
- nivel 5: desde 0,95.

La tendencia compara el ritmo de hoy con el de hace siete días:

- sube si ha cambiado en 0,01 o más;
- baja si ha cambiado en −0,045 o menos;
- en otro caso se mantiene.

Los umbrales de bajada y subida no son simétricos a propósito: cerca del máximo, un único fallo resta hasta 0,032 y no se recupera en la semana. Con un umbral simétrico, un día suelto diría «baja».

### Parámetros

Los valores están en un solo sitio y son provisionales: se ajustan con uso real.

| Parámetro | Valor |
|---|---|
| Ritmo con el que empieza un hábito | 0,5 |
| Semivida al subir | 7 días |
| Semivida al bajar | 21 días |
| Días de entrada en el ritmo general | 14 días |
| Subida mínima para decir «sube» | 0,01 en siete días |
| Bajada mínima para decir «baja» | −0,045 en siete días |
| Hábitos para el logro «Todos a una» | 3 |

### Cambio de frecuencia u objetivo

El ritmo se ancla el día en que el cambio entra en vigor (ver «Historial de objetivos y cambios»), con el hábito tal como estaba, calculado con las reglas antiguas. Así lo ya cerrado no se vuelve a juzgar con las reglas nuevas. El ancla pasa a ese día y el periodo que lo contiene solo puede subir: un cambio nunca perjudica.

- **Cambio que se aplica ya**: se ancla antes de guardar, al empezar el periodo de hoy con las reglas nuevas y sin pasar de la fecha de efecto. Un semanal que empieza el lunes de esta semana se ancla ese lunes: no se cierra nada de esta semana con las reglas antiguas y la semana entera es su primer periodo, que solo puede subir. Un diario (meta de un diario, o de semanal a diario) se ancla hoy, para no cerrar como días sin hacer los de esta semana anteriores al cambio. Si el ancla ya estaba más allá (un cambio anterior de esta misma semana, o el día en que se creó el hábito), se queda donde está y el cambio pasa a su día.
- **Cambio programado** (mañana o el lunes siguiente): se ancla al aplicarlo, en su fecha de efecto aunque se lea días después. Se aplica antes de cualquier lectura del ritmo, así que el ancla nunca ha pasado de esa fecha; si la hubiera pasado, no se toca.
- Cuentan la frecuencia, la meta, la unidad y los minutos por día (`Objetivo.cambiaElCreditoRespectoA`): cambiar los días fijos sin cambiar cuántos son no ancla el ritmo.

La racha no se toca en ninguno de los dos cambios.

### Pausa

Un hábito en pausa no se pone al día: su ritmo se queda donde se pausó. Al reanudarlo, el ancla pasa al día de la vuelta con el mismo valor, y los días de pausa no se juzgan. Ver la sección «Pausa».

---

## Racha heredada

La racha ya no es la medida que se muestra al usuario: la sustituye el ritmo. Lo que sigue pasando con la racha es esto, y nada más.

- **Qué se escribe**: nada. Desde el 21-oct-2026 ni completar un hábito (tampoco una fecha pasada) ni cambiar su frecuencia tocan la racha: los valores guardados quedan congelados.
- **Rotura perezosa**: no hay ningún proceso que ponga rachas a cero. Una racha está viva si la meta se alcanzó en el periodo actual o en el anterior. Cuando se lee una racha muerta, `RachaService.rachaActualVigente` pone `rachaActual` a cero, lo guarda y devuelve cero. `rachaMaxima` no cambia al romperse. Con la racha congelada, es lo único que ya la modifica.
- **Qué no hace**: no da puntos, no cuenta para ningún logro y no genera ningún hito. Los logros de constancia van por días hechos (ver «Catálogo de logros»).
- **Qué sigue enviando el backend**: `rachaActual` y `rachaMaxima` en el detalle del hábito, porque la aplicación ya instalada las lee. Como ya no se actualiza, `rachaActual` acaba en cero y `rachaMaxima` no cambia.
- **Reversión**: deshacer un completado restaura el estado previo de la racha, que no ha cambiado (ver «Reversión de un completado»).

---

## Sistema de puntos

Los puntos se almacenan como movimientos en `UsuarioMoneda`. El saldo es la suma de todos los movimientos del usuario (`UsuarioMonedaDAO.calcularSaldo`).

Las operaciones de gamificación registran movimientos en lugar de mantener un saldo independiente como única fuente de verdad. Los movimientos que genera el dominio de hábitos son:

- `HABITO_COMPLETADO`: la parte de puntos de cada paso;
- `EXTRA_SEMANA_COMPLETA`: el extra de completar la semana en un hábito semanal;
- `HITO_DIAS_HECHOS`: los puntos de un hito (ver «Hitos por días hechos»);
- `DESHACER_HABITO`: la compensación de un completado deshecho (ver «Reversión de un completado»).

El saldo puede quedar en negativo después de deshacer.

## Completar un hábito

### Puntos y experiencia por paso

Cada completado cobra la parte que le toca en el momento. Un día de hábito bien hecho vale **25 puntos** y **5 XP**, sea diario o semanal. Esas cifras están en `ParametrosPremio`.

- **Hábito diario**: los 25 puntos y las 5 XP del día se reparten entre las veces que hay que hacerlo, de forma que la suma de todos los pasos es siempre el total. Con meta 3, los puntos son 8, 8 y 9 (y la XP 1, 2 y 2). Pasarse de la meta no da nada: el código lo rechaza.
- **Hábito semanal**: cada sesión vale un día entero, 25 puntos y 5 XP. La sesión que completa la semana (la que llega a la meta) añade además un 20 % de lo ganado esa semana con ese hábito, en puntos y en XP. Con meta 3, esa sesión suma 15 puntos y 3 XP extra. Las sesiones por encima de la meta se registran, pero no dan nada.

- **Hábito de minutos**: cada paso cobra en proporción a sus minutos dentro del objetivo del día (ver «Hábitos en minutos»).

La experiencia va a la mascota en el mismo paso. Si un paso no da XP (el primero de un diario de meta 6, por ejemplo), la mascota no se llama.

### Reglas del completado

- Un hábito semanal de veces admite como mucho un completado por día, y uno diario de veces no deja pasar de su meta del día. Los de minutos admiten varias sesiones el mismo día (ver «Hábitos en minutos»).
- No se puede completar una fecha futura, ni una fecha anterior al lunes de la semana en curso, ni un día anterior al alta del hábito (`fechaInicio`): uno creado el viernes no acepta de lunes a jueves. Los tres responden con un conflicto (409); el último, traducido (`registro.fecha.antes.alta`). Deshacer no lleva esta regla: un registro así que quedara de antes se puede quitar.
- Un hábito en pausa no se puede completar: responde con un conflicto («El hábito está en pausa»).
- **Semanal sin días fijos** (sin `diasSemana`): cuenta como un hábito flexible. Toca cualquier día y su meta es el número de sesiones de la semana.
- **Semanal con días fijos**: al guardarlo, la meta pasa a ser el número de días marcados. Esos días son los que la aplicación propone en Hoy; el completado no se limita a ellos, porque el código no lo comprueba.

---

## Historial de objetivos y cambios

Cada hábito guarda las versiones de su objetivo en la tabla `habito_objetivo`: unidad (`VECES` o `MINUTOS`), frecuencia, meta, minutos por día, días fijos y desde qué día vale cada una.

- **Versión vigente** en una fecha: la de mayor `desde` que no la pasa. Una fecha anterior a la primera versión se juzga con la primera; un hábito sin versiones, con el objetivo que guarda `habito`.
- **Qué se juzga con ella**: los días hechos (hitos y logros de constancia), el completado (la meta del periodo, el límite de un registro por día del semanal, el tope del diario, los puntos del paso, si es una vuelta y cuándo sale la valoración), Hoy (cada fila en la fecha que se mira y cada día de la tira), el día completo, el ánimo de la mascota, los recordatorios y la semana de la app ya instalada (`/semana`). En un completado retroactivo de esta semana, la meta es la de su fecha.
- **La copia en `habito`**: `unidad`, `frecuencia`, `meta`, `minutos_objetivo` y `dias_semana` son el objetivo vigente hoy, para las lecturas rápidas y para la app ya instalada. Cuando llega la fecha de un cambio programado, la copia se pone al día al leer (Hoy, el detalle, las listas, el hábito suelto, el completado, pausar y reanudar), igual que la racha: no hay proceso nocturno. Ponerla al día escribe solo esas cinco columnas (un `UPDATE` de ellas, `IHabitoDAO.actualizarObjetivo`) y después relee el hábito: las lecturas no bloquean y pueden traer una copia vieja del hábito, y guardarlo entero devolvía activo un hábito pausado entretanto y le ponía el nombre de antes. El ritmo sigue leyendo la copia (ver «Cambio de frecuencia u objetivo»).
- **Al crear** un hábito se guarda su primera versión, desde la fecha de inicio. V22 dio una a cada hábito existente, con su objetivo de entonces.

### Regla de cambios

Un cambio nunca reinterpreta un periodo en el que ya hay registros. «Con registros» es que el hábito tenga algún registro en ese periodo.

| Qué cambia | Periodo | Sin registros en el periodo | Con registros en el periodo |
|---|---|---|---|
| Meta de un diario que sigue diario (veces al día) | el día | se aplica desde hoy | se aplica desde mañana |
| Días por semana o días fijos de un semanal | la semana (lunes a domingo) | se aplica desde el lunes de esta semana | se aplica desde el lunes siguiente |
| Frecuencia (diario ↔ semanal) | la semana | se aplica desde el lunes de esta semana | se aplica desde el lunes siguiente |
| Minutos por día (subir o bajar) | el día | se aplica desde hoy | se aplica desde mañana |
| De veces a minutos | el día | se aplica desde hoy | se aplica desde mañana |
| De minutos a veces | — | no se permite (409) | no se permite (409) |

- Un cambio semanal sin registros esta semana empieza el lunes para que la semana entera se juzgue con una sola regla: un completado retroactivo de esta semana usa ya el objetivo nuevo. Nada empieza antes de la fecha de inicio del hábito: uno creado a mitad de semana cambia desde ese día. Las versiones que hubiera entre ese lunes y hoy desaparecen (solo cubrían días sin registros), y una que ya empezara ese lunes se reescribe.
- Un cambio que mezcla las dos cosas va entero a una sola fecha, la de la semana. Por ejemplo, pasar un semanal de veces de 3 días a uno de minutos de 4 días × 60 min con registros esta semana va entero al lunes siguiente; pasarlo a 3 días × 60 min (sin tocar los días) va a mañana.
- Un hábito sin ningún registro aplica cualquier cambio al momento: la meta de un diario desde hoy, lo semanal desde el lunes de esta semana (o desde su fecha de inicio).
- Con fecha futura, el objetivo nuevo queda programado y `habito` conserva el de hoy hasta entonces. El nombre, la descripción, la categoría y el recordatorio se guardan al momento.
- **Nunca hay más de una versión futura**: si llega otro cambio antes de su fecha, la reemplaza. Un formulario que trae el objetivo vigente hoy no es un cambio y no crea versión. Si además trae `unidad` (la app nueva siempre la manda), vuelve atrás: anula el cambio programado. Sin `unidad` (la app ya instalada, que nunca la manda y cuyo formulario da siempre el objetivo de hoy porque lo ausente no cambia nada) no anula nada, para que cambiar solo el nombre desde la app vieja no borre un cambio programado desde la nueva.
- La regla está en `CalculoObjetivo`. Dos objetivos se comparan normalizados (`Objetivo.equals`): los días fijos solo cuentan en un semanal y como conjunto (`"4,2"` es `"2,4"`), los minutos solo en un hábito de minutos y, en un diario de minutos, la meta es siempre 1. Así editar solo el nombre de un hábito con datos heredados escritos de otra forma no crea una versión.
- **Dos cambios a la vez**: si dos `PUT` del mismo hábito crean a la vez una versión con la misma fecha, el segundo no la escribe (`INSERT … ON CONFLICT DO NOTHING`) y responde con un conflicto (409), en vez de un error interno.
- **Anular el cambio programado**: `DELETE /api/habitos/{id}/objetivo-programado` (solo el dueño) borra las versiones con `desde` posterior a hoy y responde 200 `{mensaje}`, haya o no cambio programado. La copia en `habito` no se toca, ni el ritmo: un cambio programado no ancla el ritmo hasta que se aplica.
- **La semana de la app ya instalada** (`/semana`): lo que es de toda la semana se juzga con el objetivo de su último día ya llegado; en una semana futura, con el de su lunes.

El `PUT /api/habitos/{id}` responde `{mensaje, aplicaDesde}`: hoy, el lunes de esta semana (un cambio semanal ya en vigor), o la fecha futura del cambio programado. El detalle (`GET /api/habitos/{id}/detalle`) trae `objetivoProgramado` (`desde`, `unidad`, `frecuencia`, `meta`, `minutosObjetivo`, `diasSemana`), o null si no hay ninguno.

---

## Hábitos en minutos

Un hábito puede medirse en **veces** (`VECES`, lo de siempre) o en **minutos** (`MINUTOS`). Uno de minutos tiene dos números: los días por periodo (la `meta`) y los minutos por día (`minutosObjetivo`, de 1 a 600). Un día está hecho cuando la **suma** de los minutos registrados ese día llega a los minutos objetivo vigentes ese día.

- **Diario de minutos**: la meta es siempre 1 (el servidor la fuerza).
- **Semanal de minutos**: «Entrenar · 3 días · 60 min» pide tres días que lleguen cada uno a 60 minutos. No son 180 minutos acumulados.
- Los hábitos de veces se comportan exactamente igual que antes; el test `PremioVecesDoradoTest` fija sus cifras.

### El registro

Cada completado de un hábito de minutos crea un registro con sus minutos en `cantidad` (en veces, `cantidad` vale 1). Los «hechos» de un día son la suma de `cantidad`.

- `POST /api/registros/completar/{id}` acepta, además de `nota` y `fecha`, `minutos`, `clave` y `origen`.
- **`minutos`**: obligatorio si la versión vigente en esa fecha es de minutos. Si falta, 409 con un texto del bundle que pide actualizar la app (nunca se registra 1 minuto por defecto). Si no es un número de 1 a 600, 400. En un día de veces se ignora.
- **Varias sesiones el mismo día** se suman, también en un semanal. El límite de un completado por día del semanal y el rechazo de meta alcanzada del diario son solo de veces.
- **Minutos de más**: se guardan y suman al total, pero no dan puntos ni XP y el crédito de ritmo del día se queda en 1.
- **`origen`**: `MANUAL` por defecto o `ENFOQUE`; otro valor, 400. Hoy solo se guarda.
- La respuesta añade `minutosDia` (los minutos del día tras el paso), `minutosObjetivo` (los del día) y `diaHecho`. `minutosDia` y `minutosObjetivo` son null en un día de veces, y `diaHecho` dice si el día cuenta como hecho con sus reglas.
- La valoración (`mostrarValoracion`) sale con el paso que hace que el día llegue a sus minutos, si es de hoy.

### Clave del cliente (idempotencia)

Con cada completado la app puede mandar una `clave` (UUID; si no lo es, 400). Se guarda en `registro.clave_cliente`, que es única.

- Si la clave ya existe en ese hábito, no se crea ni se paga nada: responde **200** (en vez de 201) con el mismo formato, el `registroId` del registro original, los premios a cero, `logrosOtorgados` vacío y los minutos del día y `diaHecho` tal como están ahora.
- Si la clave existe en otro hábito, 409.
- **Completados del mismo hábito a la vez** (con claves distintas o sin clave): el completado bloquea al empezar la fila del usuario (`SELECT … FOR NO KEY UPDATE`) y después la del hábito (`SELECT … FOR UPDATE`), y las suelta al terminar, así que se hacen uno detrás de otro y el segundo ve lo que cobró el primero: la parte del día y el extra de la semana no se cobran dos veces. Tras el bloqueo la clave se vuelve a buscar. Ver «Bloqueos».
- Dos completados con la misma clave a la vez: el registro se guarda con `INSERT … ON CONFLICT (clave_cliente) DO NOTHING`. El segundo espera a que termine el primero, no escribe nada y responde como un reintento; no choca con la restricción única, así que la transacción no queda marcada para deshacer.

### Premio

Un día vale 25 puntos y 5 XP (`ParametrosPremio`), igual que en veces. El premio de un paso de minutos está en `CalculoPremio.delPasoEnMinutos`.

- **Reparto dentro del día**: lo cobrado del día al llevar k de n minutos es `25 · min(k, n) / n` en enteros (5 para la XP), y el paso cobra lo que sube eso. Es el mismo reparto que en veces: con pasos de un minuto da las mismas cifras que un diario de veces con meta n, y la suma del día es siempre 25 y 5.
- **Diario de 30 min**: 15 + 15 minutos cobran 12 y 13 puntos (2 y 3 XP); 10 + 10 + 10, 8, 8 y 9 (1, 2 y 2 XP); 45 de una vez, 25 y 5; los minutos de más, 0. Con objetivo 600, un paso de 1 minuto no da nada.
- **Semanal**: cada día vale un día entero repartido igual dentro del día, y la semana no cobra más que `meta` días enteros. El paso que hace que los días hechos de la semana lleguen a la meta añade el extra del 20 % de `meta` días, como en veces (con meta 3, 15 puntos y 3 XP). Con 3 días × 60 min: lunes 60 → 25; martes 30 → 12; miércoles 60 → 25; jueves 60 → 13 (la semana llega al tope de 75) más el extra de 15; terminar después el martes → 0.
- **Semana del paso de veces a minutos**: si algún día de la semana es de minutos, un paso de veces de esa semana también va por el cálculo con tope (su día vale un día entero), para que la semana no cobre más que `meta` días. Ejemplo con 3 días: lunes de veces (25), miércoles y jueves de minutos con 59 de 60 (24 cada uno); la sesión de veces del martes cobra 2 puntos y 2 XP, sin extra.
- **Días a medias**: con 59 de 60 minutos los siete días de un semanal de 3 días, los pasos cobran 24, 24, 24, 3, 0, 0 y 0 (75 en total, sin extra): unos días a medias no pueden cobrar más que la semana cumplida.
- Hitos, logros y deshacer funcionan igual que en veces: el hito y los logros de constancia cuentan días hechos, y la reversión guarda lo que dio cada paso (monedas, XP, logros e hito), así que deshacer un paso de minutos devuelve exactamente lo suyo.

### Crédito, días hechos y Hoy

- **Crédito de ritmo**: un día de minutos aporta `min(1, minutos / objetivo)`. En un diario, ese es el crédito del periodo; en un semanal, `min(1, suma de créditos diarios / meta)`. En veces, igual que antes. Cada día se convierte con el objetivo vigente ese día: en la semana del paso de veces a minutos, los días de veces aportan sus sesiones (1 cada una) y los de minutos su proporción.
- **Días hechos** (hitos, logros de constancia): los días que llegan a sus minutos, cada uno con el objetivo vigente ese día.
- **Vuelta** (`RACHA_RECUPERADA`): en un hábito de minutos llega con el paso que completa el día, mirando los días hechos anteriores; un paso a medias todavía no es volver.
- **Día completo y ánimo de la mascota**: un diario de minutos está hecho cuando llega a sus minutos. Un paso a medias cuenta como «empezado», no como «completo».
- **Hoy** (`FilaHoyDTO`): añade `unidad`, `minutosHechos` (los del día que se mira) y `minutosObjetivo` (los de ese día); los dos últimos, null en veces. `completados` y `meta` conservan su significado para la app ya instalada: en un hábito de minutos cuentan días (0 o 1 en un diario; días hechos de la semana en un semanal), y `hecho`, `completadoEnFecha` y `fechasCompletadas` miran los días hechos. La tira da crédito parcial a los diarios de minutos.
- **`/dashboard`, `/semana` y `/resumen`**: en minutos cuentan días hechos. `GET /api/registros/habito/{id}/hoy` también, y añade `minutosHoy` y `minutosObjetivo` (null en veces).
- **Detalle**: añade `unidad`, `minutosObjetivo` y `totalMinutos` (la suma de los minutos de todos sus registros de minutos, también los de más). En un hábito de veces, `minutosObjetivo` y `totalMinutos` son null. En el mapa de calor cada celda se juzga con la versión vigente ese día: la de un día de minutos está completada si el día llegó a los minutos objetivo de ese día y lleva sus `minutos` y su `minutosObjetivo`; la de un día de veces (por ejemplo, anterior al paso a minutos) se marca como siempre, con `minutos` y `minutosObjetivo` a null; `totalCompletados` y `completadosMesActual` cuentan días hechos en los días de minutos. Los últimos registros llevan `cantidad` y `origen`.
- `HabitoDTO` (listas, resumen, `GET /api/habitos/{id}`) añade `unidad` y `minutosObjetivo`. `GET /api/registros/habito/{id}` devuelve en cada registro `cantidad` y `origen` (nunca la clave del cliente).

### Crear y editar

`POST /api/habitos` y `PUT /api/habitos/{id}` aceptan `unidad` y `minutosObjetivo`.

- **Ausente = sin cambio** en el `PUT`: la app ya instalada no los manda, y un null nunca borra nada; se conserva lo que tenía el hábito. En el `POST`, sin `unidad` el hábito es de veces y los minutos se ignoran.
- De minutos exige `minutosObjetivo` de 1 a 600 (si no, 400). En un diario, la meta pasa a 1; en un semanal, la meta son los días por semana (o el número de días fijos, como siempre).
- Cambiar los minutos por día o pasar de veces a minutos se aplica por días (ver la tabla de «Regla de cambios»); de minutos a veces responde 409 con un texto del bundle.

---

## Hitos por días hechos

Los hitos premian la constancia acumulada de un hábito, no la racha. Se miden en **días hechos**: en un diario, los días en que se alcanzó la meta; en un semanal, los días distintos con alguna sesión; en un hábito de minutos, los días cuya suma de minutos llegó a los minutos objetivo de ese día (los días a medias no cuentan).

| Días hechos | Puntos |
|---:|---:|
| 3 | 50 |
| 7 | 100 |
| 30 | 300 |
| 100 | 750 |
| 365 | 2000 |

Los puntos se pagan con el movimiento `HITO_DIAS_HECHOS` en el completado que hace cruzar el umbral.

- **Cada día con su objetivo**: un día hecho se juzga con el objetivo vigente ese día (ver «Historial de objetivos y cambios»), así que subir la meta no hace que dejen de contar los días que llegaron a la de entonces. Vale igual para los logros de constancia.
- **Una vez por hábito, con libro**: un hito se paga si el completado cruza el umbral (antes < umbral ≤ después) **y** el hábito no lo tiene ya en la tabla `hito_pagado`. En el mismo completado se escribe una fila por umbral pagado, ligada a su registro. El libro es quien garantiza que un umbral no se paga dos veces. La regla está en `CalculoPremio.hitosAPagar`.
- **Deshacer** el completado que pagó un hito borra su fila del libro, y los puntos vuelven con el resto de las monedas de la reversión. Si después se vuelve a cruzar, el hito se paga de nuevo.
- **Varios hitos a la vez**: se pagan todos los umbrales cruzados que falten en el libro, comparando intervalos y no valores exactos. En la práctica nunca salta más de uno: cada completado añade como mucho un día hecho.
- **Relleno inicial (V21)**: los umbrales que cada hábito ya había cruzado según sus días hechos con la meta de entonces se dieron por pagados, cada uno ligado al registro que lo cruzó (el que cerró ese día hecho, en el orden en que se marcaron), así que deshacerlo también borra su fila.
- **Romper una racha** no quita ni repite ningún hito, porque los hitos no dependen de la racha.

`HITO_RACHA` ya no se genera: el bloque que lo creaba está comentado en `RegistroService`.

---

## Pausa

La pausa es un producto del catálogo de hábitos (`PAUSA_HABITO`, nombre «Pausa», categoría «Protección», tipo CONSUMIBLE, precio 300) que se gasta al pausar un hábito. Se puede comprar en la tienda tantas veces como se quiera. El precio es provisional.

**Pausar** (`PausaService.pausar`):

- gasta una unidad de «Pausa» del inventario del usuario;
- ancla el ritmo en hoy con el valor que tiene;
- deja el hábito con `activo = false`.

Todo o nada: si no quedan unidades, responde con un conflicto («No te quedan unidades de PAUSA_HABITO») y no cambia nada. Si el hábito ya está en pausa, también responde con un conflicto («El hábito ya está en pausa»).

**Mientras está en pausa**:

- sale de Hoy, que solo muestra los hábitos activos;
- no entra en el ritmo general, que solo cuenta los activos;
- no recibe recordatorios;
- su ritmo se queda congelado donde se pausó;
- no se puede completar ni deshacer: los dos responden con un conflicto («El hábito está en pausa»).

Pausar y reanudar bloquean al usuario y al hábito, como completar, y deciden con el hábito releído tras el bloqueo (ver «Bloqueos»). Dos pausas a la vez del mismo usuario van una detrás de otra: la segunda del mismo hábito lo ve ya en pausa (409, sin gastar nada), y la de otro hábito lee la cantidad que dejó la primera (409 si no queda ninguna). Antes, las dos podían leer la misma cantidad y salir bien gastando una sola unidad. Un completado o una edición que ya habían leído el hábito activo ven la pausa.

**Reanudar** (`PausaService.reanudar`): es gratis. Si el hábito ya estaba activo, no hace nada. Si estaba en pausa, pasa a activo y su ritmo pasa al día de la vuelta con el valor que tenía al pausar. La vuelta cuenta como cambio, así que el periodo en curso solo puede subir, y la tendencia no compara con lo de antes de la pausa.

---

## Reversión de un completado

Cada completado guarda una `ReversionRegistro` con lo necesario para deshacerlo: las monedas que dio, la XP que dio a la mascota, el estado previo de la racha (`rachaActual`, `rachaMaxima`, periodo de meta alcanzada y última fecha), el estado previo del día completo de la mascota, y cada logro que desbloqueó (`ReversionLogro`), incluidos los que dispara la mascota.

Deshacer (`RegistroService.deshacerRegistro`):

- solo deshace el último completado del hábito, y solo si es de la semana en curso;
- no funciona en un hábito en pausa;
- si el completado no tiene reversión guardada (es anterior a ese mecanismo), responde con un conflicto;
- retira los logros de la reversión, para que puedan volver a concederse;
- devuelve las monedas con un movimiento `DESHACER_HABITO` de signo contrario. El libro no se borra, y el saldo puede quedar en negativo;
- restaura la racha a su estado previo, si la reversión tenía uno;
- resta la XP que dio ese completado a la mascota;
- borra la reversión y el registro;
- restaura el día completo de la mascota solo si el registro era de hoy y el día deja de estar completo.

Las monedas y los logros que dio un completado se miden como «saldo después − saldo antes» y «logros después − logros antes». Para que esa cuenta sea solo suya, el completado bloquea al usuario desde antes de leer el «antes»: otro completado o deshacer del mismo usuario, aunque sea de otro hábito, espera. Por lo mismo, todo lo demás que escribe en su saldo o en sus logros también bloquea al usuario antes que nada (ver «Bloqueos»): si no, lo concedido se colaría en esa cuenta y deshacer el completado lo devolvería (una compra hecha en medio salía gratis).

### Bloqueos

Todo lo que cambia un hábito pasa por `HabitoService.bloquear`: completar, deshacer, editar (`PUT`), pausar, reanudar, borrar y anular un cambio programado. Bloquea, hasta el final de la transacción, primero la fila del usuario (`IUsuarioDAO.bloquear`, `FOR NO KEY UPDATE`) y después la del hábito (`FOR UPDATE`), y devuelve el hábito releído con el bloqueo: el que traía quien llama se leyó antes de esperar (con open-in-view, en el controlador) y puede ser viejo. Si el usuario o el hábito ya no existen (`IUsuarioDAO.bloquear` devuelve `false` si la fila se borró mientras se esperaba), 404.

Lo que concede logros, monedas o XP sin cambiar un hábito bloquea solo al usuario, como primera escritura de su transacción:

| Dónde | Qué concede |
|---|---|
| `HoyService.obtenerHoy` (`HabitoService.bloquearUsuario`, antes de poner al día objetivos y ritmos) | logros de ritmo |
| `HabitoService.crearHabito` | `PRIMER_HABITO`, activos, categorías |
| `CategoriaService.crearCategoria` | `PRIMERA_CATEGORIA` |
| `RegistroService.actualizarNota` (transaccional) | `PRIMERA_NOTA` |
| `ProductoService`: comprar, usar, gastar una unidad, otorgar, otorgar la identidad elegida, asegurar la identidad y equipar | gasto, regalos, XP de la comida, logros de identidad |
| `UsuarioService.actualizarPerfil` (transaccional) y `loginConGoogle` con un usuario que ya existe | `BIENVENIDO`, `LOGIN_GOOGLE` |

No lo necesitan el alta (con Google o sin él: el usuario se acaba de crear en esa misma transacción y nadie más lo ve) ni `MascotaService.ganarExperiencia` y `restarExperiencia`, que solo llaman completar, deshacer y usar un producto, ya bloqueados. El bloqueo no va dentro de `UsuarioMonedaService.registrarMovimiento`: ahí llegaría después de leer el saldo y fuera de orden.

- **Un solo orden, usuario → hábito.** Es el único sitio que bloquea un hábito, así que dos operaciones no pueden pedirlos al revés. Cuidado también con los bloqueos implícitos: borrar o actualizar filas las bloquea. La baja de la cuenta (`UsuarioService.eliminarCuenta`) bloquea al usuario antes de los limpiadores, y borrar un hábito lo bloquea antes de borrar sus hitos, registros y demás: sin eso, los dos borraban las filas hijas antes que el usuario o el hábito, el orden contrario, y con un completado o un deshacer a medias Postgres abortaba una de las dos. Dos bajas a la vez: la segunda espera y da 404.
- **`FOR NO KEY UPDATE` y no `FOR UPDATE` en el usuario**: no frena a quien inserta filas que apuntan al usuario (la comprobación de la clave ajena toma `FOR KEY SHARE`, compatible); sí a quien pide el mismo bloqueo, modifica la fila o la borra.
- **Editar un objetivo** bloquea antes de leer los registros con los que decide la fecha del cambio: un paso confirmado en medio ya no puede quedar fuera (el día se juzgaría con el objetivo nuevo y cobraría dos veces, 25 + 13).

---

## Experiencia y mascota

La XP de cada completado se reparte como se describe en «Completar un hábito». La mascota suma la XP de cada paso, y sube de nivel y de fase según sus umbrales (en `MascotaService`).

La mascota tiene dos logros propios del motor, que se tienen con esa fase o una superior:

- `MASCOTA_CRIA`: en cría o adulta;
- `MASCOTA_ADULTO`: adulta.

Se miran cada vez que la mascota gana XP, no solo cuando cambia de fase, y una sola vez (`otorgarSiNoTiene`). Un salto de huevo a adulta de una vez da los dos. Van en la reversión del completado que los dio: deshacerlo los retira aunque la XP de otros pasos mantenga la fase, y lo siguiente que dé XP (un paso o una comida) los devuelve. Al perder XP no se miran.

Poner nombre a la mascota y elegir la fase que se ve escriben solo su columna (`IMascotaDAO.actualizarNombre`, `actualizarFaseElegida`): no bloquean al usuario, y guardar la mascota entera pisaba la XP que se sumaba a la vez.

Estos logros son independientes de los logros específicos del dominio de hábitos.

---

## Catálogo de logros

Los logros están divididos entre:

1. logros específicos del dominio `habitos`;
2. logros genéricos del motor de gamificación.

El dominio de hábitos decide qué código corresponde a cada evento mediante `LogrosHabitosService`. El otorgamiento efectivo lo realiza el `LogroService` genérico, que no concede dos veces el mismo logro.

### Logros de hábitos

Hay **39 logros** específicos de Hábitos. Se cuentan las entradas del array `LOGROS` de `LogrosHabitosInitializer`: 3 de Inicio, 22 de Constancia, 6 de Ritmo, 5 de Volumen, 2 de Variedad y 1 de Exploración. Las migraciones V19 (textos de constancia) coinciden con el inicializador en nombre y descripción.

#### Inicio

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `PRIMER_HABITO` | Tu primer hábito propio | Fácil | 50 | Al crear un hábito, con al menos uno activo |
| `PRIMERA_CATEGORIA` | Organizado desde el día 1 | Fácil | 50 | Al crear una categoría propia |
| `PRIMEROS_PASOS` | Primeros pasos | Fácil | 50 | Al completar, el usuario tiene 1 registro o más |

#### Constancia

Todos cuentan **días hechos de un mismo hábito**: se concede cuando el hábito llega al número indicado, y solo una vez por usuario y logro. Los códigos `RACHA_*` no cambian, pero ya no miden rachas.

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `RACHA_3` | A la tercera | Fácil | 50 | Completar un mismo hábito 3 veces |
| `RACHA_7` | Ya van siete | Medio | 100 | Completar un mismo hábito 7 veces |
| `RACHA_10` | Dos dígitos | Medio | 100 | Completar un mismo hábito 10 veces |
| `RACHA_15` | Quince firmes | Medio | 100 | Completar un mismo hábito 15 veces |
| `RACHA_20` | Veinte hechas | Medio | 100 | Completar un mismo hábito 20 veces |
| `RACHA_25` | Cuarto de cien | Medio | 100 | Completar un mismo hábito 25 veces |
| `RACHA_30` | Coge forma | Difícil | 250 | Completar un mismo hábito 30 veces |
| `RACHA_35` | Paso a paso | Medio | 100 | Completar un mismo hábito 35 veces |
| `RACHA_40` | Cuarenta | Medio | 100 | Completar un mismo hábito 40 veces |
| `RACHA_45` | Constancia probada | Medio | 100 | Completar un mismo hábito 45 veces |
| `RACHA_50` | Medio centenar | Difícil | 250 | Completar un mismo hábito 50 veces |
| `RACHA_55` | Sin mirar atrás | Medio | 100 | Completar un mismo hábito 55 veces |
| `RACHA_60` | Sesenta | Medio | 100 | Completar un mismo hábito 60 veces |
| `RACHA_65` | Rutina asentada | Medio | 100 | Completar un mismo hábito 65 veces |
| `RACHA_70` | Setenta | Medio | 100 | Completar un mismo hábito 70 veces |
| `RACHA_75` | Tres cuartos de cien | Medio | 100 | Completar un mismo hábito 75 veces |
| `RACHA_80` | Ochenta | Medio | 100 | Completar un mismo hábito 80 veces |
| `RACHA_85` | Recta final | Medio | 100 | Completar un mismo hábito 85 veces |
| `RACHA_90` | Casi cien | Difícil | 250 | Completar un mismo hábito 90 veces |
| `RACHA_100` | Maestro de la constancia | Difícil | 250 | Completar un mismo hábito 100 veces |
| `RACHA_365` | Leyenda | Difícil | 250 | Completar un mismo hábito 365 veces |
| `RACHA_RECUPERADA` | Resiliencia | Medio | 100 | Vuelve a un hábito después de unos días sin hacerlo |

`RACHA_RECUPERADA` se concede cuando un completado es una vuelta al hábito (`CalculoPremio.esUnaVuelta`): en un diario, tras al menos 3 días seguidos sin hacerlo; en un semanal, tras una semana entera sin ninguna sesión. Se gana una sola vez.

Los umbrales de constancia son 3, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 100 y 365 días hechos.

#### Ritmo

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `RITMO_BUENO` | Coge el paso | Fácil | 50 | Algún hábito activo en nivel 3 o superior |
| `RITMO_FUERTE` | A buen paso | Medio | 100 | Algún hábito activo en nivel 4 o superior |
| `RITMO_IMPARABLE` | Paso firme | Difícil | 250 | Algún hábito activo en nivel 5 |
| `RITMO_GENERAL_FUERTE` | Todo en marcha | Medio | 100 | Ritmo general en nivel 4 o superior |
| `RITMO_TODOS` | Todos a una | Difícil | 250 | Tres hábitos activos o más, todos en nivel 3 o superior |
| `RITMO_VUELTA` | De vuelta | Medio | 100 | Un hábito activo sube de nivel 1 a nivel 3 o superior en una semana |

Los logros de ritmo se conceden al leer la pantalla de Hoy (`HoyService`), porque el ritmo se mueve al cerrar el día o la semana y no al marcar. Cada logro se concede una vez por usuario. Los nombres de los niveles («Retomando», «Buen ritmo», «Gran ritmo», «Imparable») los pone la aplicación.

#### Volumen

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `HABITOS_ACTIVOS_3` | Coleccionista de hábitos | Fácil | 50 | Al crear un hábito, quedan 3 activos o más |
| `HABITOS_ACTIVOS_5` | Vida equilibrada | Medio | 100 | Al crear un hábito, quedan 5 activos o más |
| `REGISTROS_100` | Cien no es nada | Medio | 100 | Al completar, el usuario llega a 100 registros o más |
| `REGISTROS_500` | Quinientos y contando | Difícil | 250 | Al completar, el usuario llega a 500 registros o más |
| `REGISTROS_1000` | Mil pasos | Difícil | 250 | Al completar, el usuario llega a 1000 registros o más |

Los de hábitos activos y categorías se comprueban al crear un hábito; los de registros, al completar. Todos se conceden al **alcanzar o superar** su número, no al caer justo en él, y una sola vez (`otorgarSiNoTiene`): deshacer retira el logro que dio un completado, y para entonces el contador puede haber pasado de largo (completar A, que da `PRIMEROS_PASOS`; completar B; deshacer A; volver a completar A: hay dos registros y el logro vuelve). Reanudar un hábito no evalúa logros, así que los activos y sus categorías también pueden saltar un número (de 2 a 4 activos sin crear el tercero). `PRIMER_HABITO` y `PRIMERA_CATEGORIA` siguen la misma regla. El número de registros (`RegistroDAO.contarPorUsuario`) cuenta las filas del usuario, que son todas completados: el código solo crea registros completados, y deshacer borra la fila. En los días de un hábito de minutos cuenta como mucho un registro por hábito y día (un día con cinco sesiones cuenta uno); en los de veces, cada fila. Cada día se juzga con la unidad de la versión vigente en esa fecha.

#### Variedad

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `CATEGORIAS_3` | Explorador de categorías | Fácil | 50 | Al crear un hábito, hay 3 categorías distintas o más entre los activos |
| `CATEGORIAS_5` | Todoterreno | Medio | 100 | Al crear un hábito, hay 5 categorías distintas o más entre los activos |

#### Exploración

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `PRIMERA_NOTA` | Historias que contar | Fácil | 50 | Al actualizar la nota de un registro |

### Logros genéricos del motor

Hay **7 logros** genéricos del motor (`CatalogoGamificacionInitializer`):

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `BIENVENIDO` | Bienvenido/a | Fácil | 50 | Personaliza tu perfil de usuario |
| `LOGIN_GOOGLE` | Conectado con Google | Fácil | 50 | Inicia sesión usando tu cuenta de Google |
| `IDENTIDAD_PROFUNDIDAD` | Bajo las estrellas | Fácil | 250 | Consigue la identidad Profundidad |
| `IDENTIDAD_NEOTOKYO_PLUS` | Luces de neón | Fácil | 250 | Consigue la identidad Neotokyo+ |
| `IDENTIDAD_DULCE` | Con cariño | Fácil | 250 | Consigue la identidad Dulce |
| `MASCOTA_CRIA` | Ha salido del cascarón | Medio | 100 | Tu mascota evoluciona de huevo a cría (al ganar XP, en cría o adulta) |
| `MASCOTA_ADULTO` | Nori ha crecido | Medio | 100 | Tu mascota evoluciona de cría a adulto (al ganar XP, adulta) |

`IDENTIDAD_ALBA` está retirado.

### Total

- **39 logros específicos de Hábitos**
- **7 logros genéricos del motor**
- **46 logros activos en el catálogo actual**

Los logros genéricos que salen de la lista del inicializador se desactivan en el siguiente arranque. El número puede cambiar si se añaden o retiran entradas.

---

## Catálogo de productos

Actualmente el catálogo contiene:

| Código | Producto | Tipo | Precio | Función |
|---|---|---|---:|---|
| `TEMA_PROFUNDIDAD` | Profundidad | Equipable | 1000 | Identidad visual |
| `TEMA_NEOTOKYO_PLUS` | Neotokyo+ | Equipable | 1000 | Identidad visual |
| `TEMA_DULCE` | Dulce | Equipable | 1000 | Identidad visual |
| `COMIDA_BASICA` | Comida | Consumible | 50 | Alimentar a la mascota y ganar experiencia |
| `PAUSA_HABITO` | Pausa | Consumible | 300 | Pausar un hábito (ver «Pausa») |

Los precios de los temas son provisionales y todos son iguales. Ninguno es gratis: el usuario nuevo elige identidad en el onboarding por un mecanismo aparte, no por el catálogo.

Retirados:

- `TEMA_ALBA`, desactivado el 6-sep-2026.
- `ESCUDO_RACHA` («Escudo de racha»), desactivado el 24-ago-2026. Se vendía sin estar implementado.
- Los diez avatares `AVATAR_*`, desactivados el 15-sep-2026. El esqueleto técnico relacionado con avatares puede seguir existiendo en el código, pero eso no implica que los avatares estén disponibles como producto de la tienda.

### Identidades

La identidad `Profundidad` es el producto por defecto utilizado por el sistema.

Al conseguir una identidad mediante producto, el sistema deriva el logro correspondiente a partir del código del tema:

`TEMA_X` → `IDENTIDAD_X`

---

## Notificaciones

Hoy hay un único envío programado: los **recordatorios** de cada hábito.

- **Qué hábitos**: solo los activos (no los que están en pausa) con recordatorio activado y una hora elegida.
- **Cuándo**: cada 5 minutos. La hora del hábito se redondea hacia arriba al múltiplo de 5 minutos siguiente, y se compara con la ventana de 5 minutos en curso en la hora local del dueño del hábito.
- **Si toca recordar**: el hábito no está hecho en su día (en un diario) ni cubierto en su semana (en un semanal), y la semana no está ya cumplida. Un hábito de minutos no está hecho hasta que el día llega a sus minutos: un paso a medias no calla el recordatorio.
- **Un aviso por usuario**: si varios hábitos coinciden a la misma hora, el usuario recibe un solo aviso que los nombra.
- **Sin token**: si el usuario no tiene token FCM, no se envía nada. Si FCM da el token por inválido, se borra del usuario.

El aviso de racha en peligro (`NotificadorRachaEnPeligro`) existe, pero su programación está comentada: hoy no se envía. Su hora de referencia en el código es las 21:00 de la zona del usuario.

Ningún otro envío programado existe en el backend.

---

## Principios de implementación relevantes

### Fuente de verdad

La lógica funcional debe mantenerse alineada con el código implementado. Este documento no debe conservar reglas históricas como si fueran funcionalidad actual.

### Catálogo por código

Los logros y productos se identifican mediante códigos técnicos estables.

Los initializers comprueban los códigos individualmente para crear las entradas que falten.

Los logros genéricos retirados del catálogo pueden desactivarse automáticamente por el initializer del motor. Los logros propios de cada aplicación se gestionan desde su propio módulo.

### Reversiones

Las operaciones de gamificación relacionadas con un completado deben poder reconstruirse y revertirse a partir de la información conservada por `ReversionRegistro`.

### Estado implementado frente a estado planificado

Una característica que exista únicamente como comentario, placeholder, asset o estructura técnica no debe documentarse como funcionalidad disponible.
