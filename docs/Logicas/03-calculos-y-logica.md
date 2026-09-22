# Norday Habits — Cálculos y Lógica de Negocio

*Última actualización: septiembre 2026*

> Este documento describe la lógica funcional actualmente implementada. Los valores indicados aquí se basan en el código actual del backend y no deben interpretarse como promesas de producto distintas de lo que esté disponible en la aplicación.

---

## Rachas

### Regla general

La racha representa periodos consecutivos en los que el hábito alcanza su `meta`.

- `DIARIO`: el periodo es un día.
- `SEMANAL`: el periodo es una semana.
- Un periodo se considera cumplido cuando `registros_completados_en_el_periodo >= meta`.
- La racha se incrementa una vez por periodo cumplido, aunque se realicen más registros después de alcanzar la meta.
- La zona horaria del usuario determina el día actual y el cálculo de los periodos.
- `RachaService` es la puerta de lectura de la racha actual.

### Rotura perezosa

La racha no se pone a cero mediante un proceso programado.

`RachaService.rachaActualVigente(...)` comprueba si la racha almacenada sigue viva. Si ha muerto:

1. establece `rachaActual = 0`;
2. persiste el cambio;
3. devuelve `0`.

`rachaMaxima` no se modifica al romperse una racha.

La rotura, por tanto, es **perezosa**: se materializa cuando se consulta o utiliza la racha.

### Completados retroactivos

Se permite completar una fecha anterior únicamente dentro de la semana en curso.

No se permiten:

- fechas futuras;
- fechas anteriores al lunes de la semana actual.

Cuando un registro retroactivo rellena un hueco y conecta dos tramos de racha, la racha se reconstruye a partir de los registros existentes.

### Cambio de meta

La meta forma parte del cumplimiento del periodo. El comportamiento concreto ante cambios de meta se mantiene en `HabitoService` y `RegistroService`; este documento no añade reglas que no estén implementadas.

---

## Sistema de puntos

Los puntos se almacenan como movimientos en `UsuarioMoneda`. El saldo se obtiene sumando los movimientos.

### Completar un hábito

Al alcanzar exactamente la meta del periodo correspondiente:

- **25 puntos**
- **5 XP**

Los puntos y el XP no se conceden por cada registro individual cuando todavía no se ha alcanzado la meta.

Para un hábito diario con meta 3, por ejemplo, los puntos y XP se conceden al tercer completado de ese día.

Una vez alcanzada la meta, completar registros adicionales no vuelve a conceder los puntos de ese periodo.

### Hitos de racha

Los hitos actualmente implementados son:

| Racha | Puntos |
|---:|---:|
| 3 | 50 |
| 7 | 100 |
| 30 | 300 |
| 100 | 750 |
| 365 | 2000 |

Si una reconstrucción retroactiva hace que una racha salte varios valores, se pagan todos los hitos cruzados en ese salto.

Los puntos de los hitos se registran mediante movimientos `HITO_RACHA`.

Si posteriormente la racha se rompe, esos puntos no se eliminan por el mero hecho de romperse la racha.

Si una racha se rompe y posteriormente vuelve a alcanzar un hito, ese hito puede volver a generar puntos.

### Saldo

El saldo se basa en el ledger de movimientos de `UsuarioMoneda`.

Las operaciones de gamificación registran movimientos en lugar de mantener un saldo independiente como única fuente de verdad.

### Reversión de un completado

Los completados pueden disponer de una `ReversionRegistro` que conserva el estado necesario para deshacer el efecto del completado.

La reversión puede contemplar:

- monedas otorgadas;
- XP otorgada;
- estado anterior de la racha;
- periodo de meta alcanzado;
- última fecha de la racha;
- estado relacionado con el día completo de la mascota;
- logros obtenidos como consecuencia del completado.

El sistema también conserva los logros disparados indirectamente por la mascota para que puedan revertirse correctamente.

---

## Experiencia y mascota

Al alcanzar la meta del periodo se conceden **5 XP** a la mascota.

La experiencia se gestiona mediante `MascotaService`.

Cuando la mascota cambia de fase, puede disparar logros propios del motor:

- `MASCOTA_CRIA`
- `MASCOTA_ADULTO`

Estos logros son independientes de los logros específicos del dominio de hábitos.

---

## Catálogo de logros

Los logros están divididos entre:

1. logros específicos del dominio `hábitos`;
2. logros genéricos del motor de gamificación.

El dominio de hábitos decide qué código corresponde a cada evento mediante `LogrosHabitosService`. El otorgamiento efectivo lo realiza el `LogroService` genérico.

### Logros de hábitos

Actualmente existen **33 logros** específicos de Hábitos.

#### Inicio

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `PRIMER_HABITO` | Tu primer hábito propio | Fácil | 50 | Crear el primer hábito activo |
| `PRIMERA_CATEGORIA` | Organizado desde el día 1 | Fácil | 50 | Crear la primera categoría personalizada |
| `PRIMEROS_PASOS` | Primeros pasos | Fácil | 50 | Completar el primer registro |

#### Constancia

| Código | Nombre | Nivel | Puntos |
|---|---|---|---:|
| `RACHA_3` | En racha | Fácil | 50 |
| `RACHA_7` | Buen ritmo | Medio | 100 |
| `RACHA_RECUPERADA` | Resiliencia | Medio | 100 |
| `RACHA_10` | Dos dígitos | Medio | 100 |
| `RACHA_15` | Quince firmes | Medio | 100 |
| `RACHA_20` | Veinte sin fallar | Medio | 100 |
| `RACHA_25` | Cuarto de cien | Medio | 100 |
| `RACHA_30` | Imparable | Difícil | 250 |
| `RACHA_35` | Más allá del mes | Medio | 100 |
| `RACHA_40` | Cuarenta | Medio | 100 |
| `RACHA_45` | Constancia probada | Medio | 100 |
| `RACHA_50` | Medio centenar | Difícil | 250 |
| `RACHA_55` | Sin mirar atrás | Medio | 100 |
| `RACHA_60` | Dos meses | Medio | 100 |
| `RACHA_65` | Rutina asentada | Medio | 100 |
| `RACHA_70` | Diez semanas | Medio | 100 |
| `RACHA_75` | Tres cuartos de cien | Medio | 100 |
| `RACHA_80` | Ochenta | Medio | 100 |
| `RACHA_85` | Recta final | Medio | 100 |
| `RACHA_90` | Noventa días | Difícil | 250 |
| `RACHA_100` | Maestro de la constancia | Difícil | 250 |
| `RACHA_365` | Leyenda | Difícil | 250 |

`RACHA_3` se concede cuando la racha actual es 3 y además coincide con la máxima, es decir, representa la primera racha de 3.

`RACHA_RECUPERADA` se concede cuando la racha actual vuelve a ser 3 pero la máxima anterior es superior a 3.

Los demás hitos de racha se comprueban mediante igualdad con el valor actual de la racha. Por tanto, la lógica actual no utiliza `>=` para conceder esos logros.

#### Volumen

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `HABITOS_ACTIVOS_3` | Coleccionista de hábitos | Fácil | 50 | 3 hábitos activos |
| `HABITOS_ACTIVOS_5` | Vida equilibrada | Medio | 100 | 5 hábitos activos |
| `REGISTROS_100` | Cien no es nada | Medio | 100 | 100 registros completados |
| `REGISTROS_500` | Quinientos y contando | Difícil | 250 | 500 registros completados |
| `REGISTROS_1000` | Mil pasos | Difícil | 250 | 1000 registros completados |

#### Variedad

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `CATEGORIAS_3` | Explorador de categorías | Fácil | 50 | 3 categorías distintas en hábitos activos |
| `CATEGORIAS_5` | Todoterreno | Medio | 100 | 5 categorías distintas en hábitos activos |

#### Exploración

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `PRIMERA_NOTA` | Historias que contar | Fácil | 50 | Añadir una nota a un registro |

### Logros genéricos del motor

Actualmente el catálogo genérico contiene:

| Código | Nombre | Nivel | Puntos | Condición |
|---|---|---|---:|---|
| `BIENVENIDO` | Bienvenido/a | Fácil | 50 | Personalizar el perfil |
| `LOGIN_GOOGLE` | Conectado con Google | Fácil | 50 | Iniciar sesión mediante Google |
| `IDENTIDAD_PROFUNDIDAD` | Bajo las estrellas | Fácil | 250 | Conseguir Profundidad |
| `IDENTIDAD_NEOTOKYO_PLUS` | Luces de neón | Fácil | 250 | Conseguir Neotokyo+ |
| `IDENTIDAD_DULCE` | Con cariño | Fácil | 250 | Conseguir Dulce |
| `MASCOTA_CRIA` | Ha salido del cascarón | Medio | 100 | Evolucionar de huevo a cría |
| `MASCOTA_ADULTO` | Nori ha crecido | Medio | 100 | Evolucionar de cría a adulto |

`IDENTIDAD_ALBA` está retirado.

### Total actual

- **33 logros específicos de Hábitos**
- **7 logros genéricos del motor**
- **40 logros activos en el catálogo actual**

El número de logros activos puede cambiar en futuras versiones si se añaden o retiran entradas del catálogo.

---

## Catálogo de productos

Actualmente el catálogo contiene:

| Código | Producto | Tipo | Precio | Función |
|---|---|---|---:|---|
| `TEMA_PROFUNDIDAD` | Profundidad | Equipable | 1000 | Identidad visual |
| `TEMA_NEOTOKYO_PLUS` | Neotokyo+ | Equipable | 1000 | Identidad visual |
| `TEMA_DULCE` | Dulce | Equipable | 1000 | Identidad visual |
| `COMIDA_BASICA` | Comida | Consumible | 50 | Alimentar a la mascota y ganar experiencia |

`TEMA_ALBA` está retirado.

El `Escudo de racha` está retirado porque su funcionalidad de protección no estaba implementada.

Los diez avatares del catálogo anterior también están retirados del catálogo actual.

El esqueleto técnico relacionado con avatares puede seguir existiendo en el código, pero eso no implica que los avatares estén disponibles como producto de la tienda.

### Identidades

La identidad `Profundidad` es el producto por defecto utilizado por el sistema.

Al conseguir una identidad mediante producto, el sistema deriva el logro correspondiente a partir del código del tema:

`TEMA_X` → `IDENTIDAD_X`

---

## Notificaciones de racha

La antigua lógica que reseteaba las rachas mediante un scheduler ya no existe.

Actualmente `NotificadorRachaEnPeligro` únicamente envía avisos.

- El proceso se ejecuta cada hora.
- Cada usuario se evalúa en su propia zona horaria.
- El aviso se intenta enviar cuando son las **21:00 hora local del usuario**.
- Solo se avisa si existe una racha viva que todavía no ha sido renovada en el periodo actual.
- Se utiliza el token FCM del usuario.
- Si no hay token FCM, no se envía la notificación.

El scheduler, por tanto, **no decide ni modifica el estado de la racha**. Su función es exclusivamente informativa.

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

---
