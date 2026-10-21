# Norday Habits — Cálculos y Lógica de Negocio

*Última actualización: octubre de 2026*

> Este documento describe la lógica funcional actualmente implementada. Los valores indicados aquí se basan en el código actual del backend y no deben interpretarse como promesas de producto distintas de lo que esté disponible en la aplicación.

---

## Ritmo

El ritmo es la medida de constancia que la aplicación muestra a cada hábito. Va de 0 a 1 y se convierte en un nivel de 1 a 5. Su modelo (el ancla que se guarda en la base de datos) está descrito en el documento de modelo de datos; aquí va el cálculo.

### Cálculo de un periodo

Un periodo es un día en un hábito diario y una semana natural (de lunes a domingo) en un hábito semanal.

- **Crédito de un periodo**: lo que se hizo del objetivo, de 0 a 1. Es `min(1, hechos / meta)`. Lo parcial cuenta en proporción (3 de 4 vale 0,75) y pasarse del objetivo no suma más. Un objetivo menor que 1 se trata como 1.
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

Antes de guardar el cambio, el ritmo se ancla con el hábito tal como estaba, calculado con las reglas antiguas. Así lo ya cerrado no se vuelve a juzgar con las reglas nuevas. El ancla pasa a hoy y el periodo en curso solo puede subir: un cambio nunca perjudica.

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

La experiencia va a la mascota en el mismo paso. Si un paso no da XP (el primero de un diario de meta 6, por ejemplo), la mascota no se llama.

### Reglas del completado

- Un hábito semanal admite como mucho un completado por día.
- No se puede completar una fecha futura, ni una fecha anterior al lunes de la semana en curso.
- Un hábito en pausa no se puede completar: responde con un conflicto («El hábito está en pausa»).
- **Semanal sin días fijos** (sin `diasSemana`): cuenta como un hábito flexible. Toca cualquier día y su meta es el número de sesiones de la semana.
- **Semanal con días fijos**: al guardarlo, la meta pasa a ser el número de días marcados. Esos días son los que la aplicación propone en Hoy; el completado no se limita a ellos, porque el código no lo comprueba.

---

## Hitos por días hechos

Los hitos premian la constancia acumulada de un hábito, no la racha. Se miden en **días hechos**: en un diario, los días en que se alcanzó la meta; en un semanal, los días distintos con alguna sesión.

| Días hechos | Puntos |
|---:|---:|
| 3 | 50 |
| 7 | 100 |
| 30 | 300 |
| 100 | 750 |
| 365 | 2000 |

Los puntos se pagan con el movimiento `HITO_DIAS_HECHOS` en el completado que hace cruzar el umbral.

- **Una vez por hábito**: el contador de días hechos solo sube al completar. Un hito se paga al cruzarlo, y no se vuelve a pagar mientras el contador no baje. Solo baja al deshacer un completado, y entonces la reversión devuelve esos puntos. Si después se vuelve a completar, el hito se paga de nuevo.
- **Varios hitos a la vez**: el código paga todos los umbrales que cruza un salto, comparando intervalos y no valores exactos. En la práctica nunca salta más de uno: cada completado añade como mucho un día hecho.
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

---

## Experiencia y mascota

La XP de cada completado se reparte como se describe en «Completar un hábito». La mascota suma la XP de cada paso, y sube de nivel y de fase según sus umbrales (en `MascotaService`).

Cuando la mascota cambia de fase, puede disparar logros propios del motor:

- `MASCOTA_CRIA`
- `MASCOTA_ADULTO`

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
| `PRIMER_HABITO` | Tu primer hábito propio | Fácil | 50 | Al crear un hábito, es el único activo |
| `PRIMERA_CATEGORIA` | Organizado desde el día 1 | Fácil | 50 | Al crear la primera categoría propia |
| `PRIMEROS_PASOS` | Primeros pasos | Fácil | 50 | Al completar el primer registro del usuario |

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
| `HABITOS_ACTIVOS_3` | Coleccionista de hábitos | Fácil | 50 | Al crear un hábito, quedan 3 activos |
| `HABITOS_ACTIVOS_5` | Vida equilibrada | Medio | 100 | Al crear un hábito, quedan 5 activos |
| `REGISTROS_100` | Cien no es nada | Medio | 100 | Al completar, el usuario llega a 100 registros |
| `REGISTROS_500` | Quinientos y contando | Difícil | 250 | Al completar, el usuario llega a 500 registros |
| `REGISTROS_1000` | Mil pasos | Difícil | 250 | Al completar, el usuario llega a 1000 registros |

Los de hábitos activos y categorías se comprueban al crear un hábito, con el número exacto. Los de registros, al completar, también con el número exacto. El número de registros cuenta todas las filas del usuario, que son todas completados: el código solo crea registros completados, y deshacer borra la fila.

#### Variedad

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `CATEGORIAS_3` | Explorador de categorías | Fácil | 50 | Al crear un hábito, hay 3 categorías distintas entre los activos |
| `CATEGORIAS_5` | Todoterreno | Medio | 100 | Al crear un hábito, hay 5 categorías distintas entre los activos |

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
| `MASCOTA_CRIA` | Ha salido del cascarón | Medio | 100 | Tu mascota evoluciona de huevo a cría |
| `MASCOTA_ADULTO` | Nori ha crecido | Medio | 100 | Tu mascota evoluciona de cría a adulto |

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
- **Si toca recordar**: el hábito no está hecho en su día (en un diario) ni cubierto en su semana (en un semanal), y la semana no está ya cumplida.
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
