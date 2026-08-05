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
  *motor* y viven en el mismo módulo Maven `norday-motor`; por eso entre
  ellos no hay muro.
- `core` → `habitos` : **PROHIBIDO**.
- `gamificacion` → `habitos` : **PROHIBIDO**.

Desde el Nivel 2 esta regla ya no depende solo de la disciplina: Maven la
impone. `norday-motor` no declara ninguna dependencia hacia `habitos`, así
que una importación prohibida ni siquiera compila.

Ningún archivo de `core` ni de `gamificacion` puede contener las palabras
`Habito`, `Registro`, `Racha` o `Categoria` — ni siquiera en comentarios o
texto de logs. Se verifica con dos greps que deben dar cero resultados:

```bash
grep -rn "com\.norday\.habitos" norday-motor/src/main/java
grep -rnE "\b(Habito|Registro|Racha|Categoria)\w*" norday-motor/src/main/java
```

## Estructura de módulos Maven (Nivel 2, ya aplicada)

El backend es un proyecto multi-módulo. El `pom.xml` de la raíz es un
agregador (`packaging=pom`, sin dependencias propias) que hereda de
`spring-boot-starter-parent` y declara tres módulos:

- **`norday-motor/`** — el motor: `com.norday.core` y
  `com.norday.gamificacion`. Es una librería (sin
  `spring-boot-maven-plugin`) y concentra **todas** las dependencias
  técnicas: JPA, web, security, JWT, validación, bucket4j, mail, OAuth2,
  Google/Firebase, Flyway, PostgreSQL. Es el módulo que se extraerá tal
  cual cuando exista la segunda app del ecosistema.
- **`habitos/`** — el dominio: `com.norday.habitos`. Librería también.
  Su única dependencia de producción es `norday-motor`, y las hereda
  todas por transitividad.
- **`norday-server/`** — el ensamblado ejecutable: `NordayApplication`
  y `src/main/resources` (properties, `mensajes/`, `static/`,
  `db/migration/`). Depende de `habitos` y lleva el
  `spring-boot-maven-plugin` con `mainClass com.norday.NordayApplication`.
  Es el único módulo que produce un jar arrancable.

La cadena es lineal: `norday-server` → `habitos` → `norday-motor`. Una
app futura del ecosistema añadiría su propio módulo de dominio junto a
`habitos`, colgando igualmente de `norday-motor`.

El `Dockerfile` copia el pom raíz, los tres poms de módulo y las tres
carpetas `src/` antes de `mvn clean package`, y toma el jar de
`norday-server/target/`.

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
módulo aporta su propio bundle y lo registra implementando
`ProveedorMensajes`; `MensajesConfig` los recolecta. `EmailService` solo
recibe claves, nunca frases.

Los ficheros `.properties` viven hoy todos juntos en
`norday-server/src/main/resources/mensajes/`. En ejecución da igual (el
jar los une en un único classpath), pero **queda pendiente decidir** si
cada bundle debe mudarse al módulo que lo aporta —`core*` a
`norday-motor`, `habitos*` a `habitos`— para que la propiedad "cada
módulo aporta lo suyo" también se cumpla en el árbol de ficheros.
Mientras estén en `norday-server`, `BundlesMensajesTest` no los ve desde
`norday-motor`.

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
