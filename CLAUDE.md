# Norday — Contexto del proyecto (Backend)

Este backend (Spring Boot + PostgreSQL) es el primero de un ecosistema de
apps Norday. Todas las apps futuras compartirán este mismo backend, la
misma base de datos y la misma cuenta de usuario/monedas. Cada decisión
de arquitectura se toma pensando en esa reutilización.

## Regla de arquitectura obligatoria: Motor vs Disparadores

- **Motor** = genérico, reutilizable por cualquier app futura del
  ecosistema: auth/JWT, usuarios, sistema de puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota, email, FCM.
- **Disparadores** = específico del dominio "hábitos": Habito, Registro,
  Frecuencia, Categoria, sus schedulers y controllers.

**Ningún servicio genérico puede conocer conceptos de dominio como
"hábito", "registro" o "categoría".** El motor recibe códigos/eventos
genéricos (ej. `ganarExperiencia(usuarioId, cantidad)`, no
`ganarExperienciaPorHabito(...)`). Si una tarea toca código del motor,
comprueba primero si de verdad pertenece ahí o si en realidad es lógica
específica de hábitos que se ha colado.

## Estructura de paquetes (Nivel 1, ya aplicada)

El código está reorganizado por dominio bajo `com.norday`. Cada paquete
tiene dentro su propia estructura por capas (`config`, `controller`,
`model`, `model.dto`, `repository`, `repository.impl`, `service`):

- **`com.norday`** — solo `NordayApplication`. Vive en la raíz, fuera de
  los tres paquetes, para que el component scan los alcance a todos.
- **`com.norday.core`** — motor de cuenta: auth/JWT y security, usuarios,
  recuperación de contraseña, email, FCM, Firebase.
- **`com.norday.gamificacion`** — motor de juego: puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota.
- **`com.norday.habitos`** — dominio: Habito, Registro, Racha, Categoria,
  Frecuencia, sus schedulers, controllers y logros de dominio.

### Regla de dependencias (obligatoria)

- `habitos` → puede importar de `core` y `gamificacion`.
- `gamificacion` ↔ `core` → permitido en ambos sentidos. Los dos son
  *motor*, y en el Nivel 2 (multi-módulo Maven) serán un único módulo
  `norday-motor`; por eso entre ellos no hay muro.
- `core` → `habitos` : **PROHIBIDO**.
- `gamificacion` → `habitos` : **PROHIBIDO**.

Ningún archivo de `core` ni de `gamificacion` puede contener las palabras
`Habito`, `Registro`, `Racha` o `Categoria` — ni siquiera en comentarios o
texto de logs. Se verifica con dos greps que deben dar cero resultados:

```bash
grep -rn "com\.norday\.habitos" src/main/java/com/norday/core src/main/java/com/norday/gamificacion
grep -rnE "\b(Habito|Registro|Racha|Categoria)\w*" src/main/java/com/norday/core src/main/java/com/norday/gamificacion
```

## Borrado de cuenta: patrón LimpiadorDatosUsuario

`UsuarioService.eliminarCuenta()` **no** conoce las tablas de cada módulo.
Declara la interfaz `LimpiadorDatosUsuario` (en `core.service`), Spring
recolecta todas sus implementaciones en un `List<LimpiadorDatosUsuario>`,
se recorren, y solo después se borra el `Usuario`.

Implementaciones actuales: `LimpiadorHabitos` (registros y rachas, luego
hábitos, luego categorías propias) y `LimpiadorGamificacion` (logros,
monedas, productos, mascota).

**Cada módulo nuevo que guarde datos colgando de `Usuario` debe aportar su
propio limpiador.** Esto sustituye a la regla frágil de "acordarse de
añadir cada tabla nueva a `eliminarCuenta`": ese método ya no se toca.
El orden entre limpiadores es indiferente (son árboles de FK
independientes que solo apuntan a `Usuario`); el orden *dentro* de cada
limpiador sí importa.

## Siembra de catálogos

Cada módulo siembra lo suyo con su propio `CommandLineRunner`:
`CategoriasInitializer` y `LogrosHabitosInitializer` (habitos),
`CatalogoGamificacionInitializer` (gamificacion).

La comprobación de idempotencia de logros y productos es **por código,
fila a fila** (`findByCodigo(...) != null` → saltar), no una guarda
todo-o-nada sobre la tabla entera: el catálogo de logros lo siembran dos
módulos distintos, y una guarda global haría que el segundo en arrancar no
sembrara nada.

## Catálogos compartidos

Los catálogos de logros y productos deben poder distinguir a qué app
pertenecen (campo `app`) cuando se construya/amplíe la tienda. El saldo
de puntos (UsuarioMoneda) es único y compartido entre todas las apps del
ecosistema — no crear muros entre apps.

## Zona horaria y locale

Ningún cálculo de "hoy" de cara al usuario puede usar `LocalDate.now()` sin
zona. La zona sale siempre del usuario, vía `ZonaUsuarioService`. Los sellos
de auditoría (`fechaRegistro`, `UsuarioLogro`, `UsuarioMoneda`,
`UsuarioProducto`) van en UTC explícito: no son días de usuario. Las
caducidades absolutas (código de recuperación) son instantes, no días, y no
se tocan.

La JVM **no** fija zona por defecto — se eliminó el `TimeZone.setDefault`.
La fija el contenedor a UTC (`ENV TZ=UTC` en el `Dockerfile`).

La racha no depende de ningún cron: `Racha` guarda `periodoMetaAlcanzada`
(el inicio del periodo en que se cumplió la meta) y el sello se autocaduca
al cambiar de periodo. La rotura es perezosa y se normaliza al leer, en
`RachaService.rachaActualVigente`. Ningún barrido decide sobre rachas; el
scheduler solo avisa.

## Textos

El motor no contiene texto de cara al usuario específico de una app. Cada
módulo aporta su propio bundle en `src/main/resources/mensajes/` y lo
registra implementando `ProveedorMensajes`; `MensajesConfig` los recolecta.
`EmailService` solo recibe claves, nunca frases.

El fichero base (sin sufijo de idioma) va en español, que es la caída
acordada. Idiomas soportados: `es`, `en`, `pt`.

Los catálogos viajan por `codigo`; el nombre en BD es solo caída para el
cliente, que traduce por código.

## Estilo de trabajo con el usuario

- Un paso a la vez, confirmar que compila antes de seguir.
- Si algo admite varios diseños o no está claro, preguntar antes de
  decidir — no asumir.
- Nunca hacer push ni tocar sistemas externos (Railway/Oracle/DB de
  producción) sin confirmación explícita.
