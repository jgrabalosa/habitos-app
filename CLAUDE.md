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
  los demás paquetes, para que el component scan los alcance a todos.
- **`com.norday.core`** — motor de cuenta: auth/JWT y security, usuarios,
  recuperación de contraseña, email, FCM, Firebase.
- **`com.norday.gamificacion`** — motor de juego: puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota.
- **`com.norday.habitos`** — dominio: Habito, Registro, Racha, Categoria,
  Frecuencia, sus schedulers, controllers y logros de dominio.
- **`com.norday.conocimiento`** — dominio de la segunda app: píldoras de
  microaprendizaje, preferencias de categoría y valoraciones. Hermano de
  `habitos`, no descendiente suyo.

### Regla de dependencias (obligatoria)

- `habitos` → puede importar de `core` y `gamificacion`.
- `conocimiento` → puede importar de `core` y `gamificacion`.
- `gamificacion` ↔ `core` → permitido en ambos sentidos. Los dos son
  *motor* y viven en el mismo módulo Maven `norday-motor`; por eso entre
  ellos no hay muro.
- `core` → `habitos` o `conocimiento` : **PROHIBIDO**.
- `gamificacion` → `habitos` o `conocimiento` : **PROHIBIDO**.
- `habitos` ↔ `conocimiento` : **PROHIBIDO** en los dos sentidos. Son
  hermanos y no se conocen: ninguno declara al otro en su `pom.xml`, así
  que una importación cruzada tampoco compila.

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
`spring-boot-starter-parent` y declara cuatro módulos:

- **`norday-motor/`** — el motor: `com.norday.core` y
  `com.norday.gamificacion`. Es una librería (sin
  `spring-boot-maven-plugin`) y concentra **todas** las dependencias
  técnicas: JPA, web, security, JWT, validación, bucket4j, mail, OAuth2,
  Google/Firebase, Flyway, PostgreSQL. Es el módulo que se extraerá tal
  cual cuando exista la segunda app del ecosistema.
- **`habitos/`** — el dominio de Norday Habits: `com.norday.habitos`.
  Librería también. Su única dependencia de producción es `norday-motor`,
  y las hereda todas por transitividad.
- **`conocimiento/`** — el dominio de Norday Conocimiento:
  `com.norday.conocimiento`. Igual que `habitos`: librería, y su única
  dependencia de producción es `norday-motor`.
- **`norday-server/`** — el ensamblado ejecutable: `NordayApplication`
  y `src/main/resources` (properties, `mensajes/`, `static/`,
  `db/migration/`). Depende de `habitos` **y** de `conocimiento`, y lleva
  el `spring-boot-maven-plugin` con
  `mainClass com.norday.NordayApplication`. Es el único módulo que
  produce un jar arrancable.

La cadena **no** es lineal, es una Y: `norday-server` cuelga de los dos
módulos de dominio, y cada uno de ellos cuelga de `norday-motor`. Una app
futura del ecosistema añadiría su propio módulo de dominio junto a
`habitos` y `conocimiento`, colgando igualmente de `norday-motor`.

## Borrado de cuenta: patrón LimpiadorDatosUsuario

`UsuarioService.eliminarCuenta()` **no** conoce las tablas de cada módulo.
Declara la interfaz `LimpiadorDatosUsuario` (en `core.service`), Spring
recolecta todas sus implementaciones en un `List<LimpiadorDatosUsuario>`,
se recorren, y solo después se borra el `Usuario`.

Implementaciones actuales, cuatro:

- `LimpiadorHabitos` — registros y rachas, luego hábitos, luego categorías
  propias.
- `LimpiadorGamificacion` — logros, monedas, productos, mascota.
- `LimpiadorRecuperacion` — códigos de recuperación de contraseña, que
  cuelgan del email y no del id.
- `LimpiadorConocimiento` — píldoras vistas, preferencias de categoría y
  valoraciones.

**Cada módulo nuevo que guarde datos colgando de `Usuario` debe aportar su
propio limpiador.** Esto sustituye a la regla frágil de "acordarse de
añadir cada tabla nueva a `eliminarCuenta`": ese método ya no se toca.
El orden entre limpiadores es indiferente (son árboles de FK
independientes que solo apuntan a `Usuario`); el orden *dentro* de cada
limpiador sí importa.

## Exportación de datos: el mismo patrón

`ExportacionDatosService.exportar(id)` tampoco conoce las tablas de cada
módulo. La interfaz `ExportadorDatosUsuario` vive en `core.service` y
Spring recolecta sus implementaciones igual que con los limpiadores:
`ExportadorGamificacion`, `ExportadorHabitos` y `ExportadorConocimiento`.

Lo consume `GET /api/usuarios/{id}/exportar`, que comprueba que el
autenticado es el dueño y devuelve el JSON con `Content-Disposition`. Es la
portabilidad del RGPD (art. 20) y está prometida en la política de
privacidad.

**Un módulo nuevo que guarde datos del usuario aporta dos cosas: su
limpiador y su exportador.** Si solo aporta el limpiador, la exportación
mentirá por omisión.

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

Los catálogos de logros y productos distinguen a qué app pertenecen con el
campo `origenApp` de `Logro` y `Producto`. Ya no es un plan a futuro: está
en producción y es carga estructural.

⚠️ **Invariante crítico.** `CatalogoGamificacionInitializer` recorre en cada
arranque todo `logroDAO.findAll()` y retira los que tengan
`origenApp = null`, sigan activos y no aparezcan en su propia lista
`LOGROS`. Es decir: **sembrar un logro genérico y registrarlo en esa lista
son la misma acción.** Un initializer aparte que cree logros con
`origenApp = null` es una bomba de relojería, porque el siguiente arranque
los dará por retirados. Vale igual para productos.

Hay un tope de seguridad: si los candidatos a retirada superan en número a
los códigos vigentes, no se toca nada y se avisa por log. Está pensado
justo para cazar a otro módulo sembrando sin `origenApp`.

El saldo de puntos (UsuarioMoneda) es único y compartido entre todas las
apps del ecosistema — no crear muros entre apps.

## Lo retirado del catálogo

Retirado no es borrado: si la fila ya existía, queda en la BD con
`activo = false` y el cliente deja de recibirla. Cada retirada está
comentada en su initializer con fecha y motivo.

| Código | Retirado | Motivo |
|---|---|---|
| `ESCUDO_RACHA` | 24-ago-2026 | Se vendía sin estar implementado (migración V6) |
| `INTERACCION_RESENA` | 24-ago-2026 | Premiar la reseña va contra la política de Google Play (V7). El diálogo de reseña sigue en la app; sólo se quita la recompensa |
| `TEMA_ALBA` | 6-sep-2026 | Se sale a Google Play con tres identidades: Profundidad, Neotokyo+ y Dulce |
| Los diez `AVATAR_*` | 15-sep-2026 | No encajaban con la app. El esqueleto sigue en `norday_flutter_core` |
| `IDENTIDAD_ALBA` | 17-sep-2026 | Inalcanzable sin `TEMA_ALBA` |

Para retirar un logro o producto genérico basta con sacarlo del array de
`CatalogoGamificacionInitializer`: el siguiente arranque lo desactiva.

**Reactivar no es simétrico.** Descomentar la fila no basta si ya existe en
la BD con `activo = false`: `findByCodigo(...) != null` salta la creación y
hace falta un `UPDATE` manual o una migración nueva. `ESCUDO_RACHA` e
`INTERACCION_RESENA` los desactivó una migración (V6 y V7): nunca editar
una migración ya aplicada, porque Flyway rechaza el cambio de checksum.
Antes de reactivar, comprobar en la BD si la fila existe. A 24-sep-2026 no
existe ninguna de las retiradas, ni en producción ni en staging: las BD se
reiniciaron después de retirarlas, así que hoy bastaría con descomentar.

## Elección de identidad en el onboarding

El usuario elige identidad (tema visual) la primera vez que entra:
`POST /api/gamificacion/identidad/elegir/{usuarioId}/{productoId}` →
`otorgarIdentidadElegida`, que otorga **y** equipa en un solo paso.

**No reutilizar `otorgarProducto` para esto.** Su guarda solo impide repetir
el *mismo* producto: llamándola cuatro veces con códigos distintos se
conseguían las cuatro identidades gratis (4000 monedas). La guarda correcta
es "¿ya tiene *alguna* de categoría Tema?", y para eso está
`poseeAlgunoDeCategoria` en el DAO.

`asegurarIdentidad` es la red de seguridad: si un usuario llega sin ninguna
identidad, le otorga `TEMA_PROFUNDIDAD`.

**Se llama desde el controlador, nunca desde el servicio.** Dentro de
`loginConGoogle`, que es `@Transactional`, una excepción marcaría la
transacción como rollback-only y el commit reventaría después con
`UnexpectedRollbackException`: el usuario recibiría un 401 de Google por un
fallo de identidad. Cualquier red de seguridad que se añada en el futuro
tiene el mismo problema y la misma solución.

Tiene una **ventana de gracia de 24h** desde el alta, para que no dispare
durante el onboarding y le robe la elección al usuario.

## Zona horaria y locale

Ningún cálculo de "hoy" de cara al usuario puede usar `LocalDate.now()` sin
zona. La zona sale siempre del usuario, vía `ZonaUsuarioService`. Los sellos
de auditoría (`fechaRegistro`, `UsuarioLogro`, `UsuarioMoneda`,
`UsuarioProducto`) van en UTC explícito: no son días de usuario. Las
caducidades absolutas (código de recuperación) son instantes, no días, y no
se tocan.

La JVM **no** fija zona por defecto — se eliminó el `TimeZone.setDefault`.
Toma la del sistema operativo: en el VPS es `Europe/Madrid`. Nada del
código debe depender de ese valor. Lo que necesite UTC lo pide explícito,
como hacen los sellos de auditoría del párrafo anterior.

La racha no depende de ningún cron: `Racha` guarda `periodoMetaAlcanzada`
(el inicio del periodo en que se cumplió la meta) y el sello se autocaduca
al cambiar de periodo. La rotura es perezosa y se normaliza al leer, en
`RachaService.rachaActualVigente`. Ningún barrido decide sobre rachas; el
scheduler solo avisa.

## Deshacer un completado: ReversionRegistro

Deshacer un completado revierte todos sus efectos. Al completar,
`RegistroService` guarda una `ReversionRegistro` ligada al registro: las
monedas y la XP que dio ese completado, el estado previo de la racha y del
día completo de la mascota, y, en `ReversionLogro`, cada logro que
desbloqueó, incluidos los que dispara la mascota.

Al deshacer:

- los logros se retiran, para que puedan volver a concederse;
- las monedas no se borran, se compensan con un movimiento de signo
  contrario: el libro de `UsuarioMoneda` es de sólo añadir, y se permite
  saldo negativo;
- la XP se resta en lo que dio el completado, sin volver al valor previo,
  que borraría la ganada después;
- la racha vuelve a su estado previo.

**Todo efecto nuevo que se añada a un completado tiene que quedar en la
reversión.** Si no, deshacer lo deja puesto y el usuario se queda con
puntos, XP o logros que no le tocan.

## Textos

El motor no contiene texto de cara al usuario específico de una app. Cada
módulo aporta su propio bundle y lo registra implementando
`ProveedorMensajes`; `MensajesConfig` los recolecta. `EmailService` solo
recibe claves, nunca frases.

Los ficheros `.properties` viven todos juntos en
`norday-server/src/main/resources/mensajes/`, aunque cada uno lo aporte
conceptualmente su módulo. En ejecución da igual: el jar los une en un
único classpath.

De ahí que `BundlesMensajesTest` viva en `norday-server` y no en
`norday-motor`: compara el bundle de `core` contra el de `habitos`, y
`norday-server` es el único módulo que ve los dos. Un test que cruce
módulos pertenece al módulo que los reúne.

El fichero base (sin sufijo de idioma) va en español, que es la caída
acordada. Idiomas soportados: `es`, `en`, `pt`.

Los catálogos viajan por `codigo`; el nombre en BD es solo caída para el
cliente, que traduce por código.

## Tests

Los `@SpringBootTest` cargan la configuración de
`norday-server/src/test/resources/application.properties`, que apunta a una
base de datos dedicada `habitos_db_test`.

Esto no es cosmético. Mientras ese fichero no existió, cualquier test que
levantara contexto Spring cargaba `src/main/resources/application.properties`
—que está en `.gitignore` y en cada máquina apunta a la BD real— y con él
arrancaban Flyway y los `CommandLineRunner` **contra producción en cada
build**.

Reglas al tocar esa configuración:

- **`src/test/resources` tapa por completo a `src/main/resources`**, no se
  suman valores. Por eso el fichero declara también `jwt.secret`,
  `jwt.expiration`, `norday.mail.from` y `spring.mail.host`. Toda propiedad
  obligatoria que se añada en producción hay que añadirla aquí también.
- **`spring.mail.host` es obligatoria aunque no se envíe correo.**
  `MailSenderAutoConfiguration` solo crea el bean `JavaMailSender` si esa
  propiedad existe, y `EmailService` lo inyecta como obligatorio. Sin ella el
  contexto no arranca. `localhost` no abre ninguna conexión.
- **Flyway va desactivado y el esquema lo genera Hibernate (`create-drop`).**
  No existe una migración `V1__`: el histórico está *baselined* en la versión 1
  sobre un esquema creado con `ddl-auto=update`, y las siguientes son índices y
  `UPDATE`s sobre tablas que ya existen. Flyway no puede construir la BD de
  test desde cero.
- **Los initializers siguen corriendo bajo test**, y es deliberado: sobre una
  BD desechable son inofensivos y siembran el catálogo. No hay guardas
  `@Profile` y no hacen falta.
- **Usuario y contraseña salen del entorno** si existe:
  `${TEST_DB_USER:norday_test}`. Una máquina con otras credenciales las pone
  por variable, sin tocar el fichero.

Descartado, y por qué:

- **Testcontainers** — los tests corren en el VPS en cada despliegue y esa
  máquina ya carga Postgres más dos JVMs. Un demonio Docker y una imagen por
  build no compensan con un solo desarrollador y Postgres ya instalado.
- **`-Dspring.profiles.active=staging` en el build** — vive fuera del repo y
  depende de que nadie lo olvide; desde el IDE se volvería a producción.

**Prerrequisito de build.** El `package` va sin `-DskipTests`, así que toda
máquina que compile necesita la BD:

```bash
psql -U postgres -c "CREATE USER norday_test WITH PASSWORD 'norday_test';"
psql -U postgres -c "CREATE DATABASE habitos_db_test OWNER norday_test;"
```

`norday_test` tiene que ser **dueño**: en Postgres 15+ el esquema `public` no
es escribible por cualquiera y `create-drop` crea tablas en cada ejecución.

**Cuidado con `create-drop`:** si el `spring.datasource.url` de test apunta a
una BD con datos, la vacía al arrancar.

Para comprobar que el aislamiento funciona en una máquina nueva, los tests en
verde **no** son prueba: pasarían igual contra la BD real. Lo que lo demuestra
es el contador de transacciones de Postgres:

```bash
psql -U postgres -c "SELECT datname, xact_commit FROM pg_stat_database WHERE datname LIKE 'habitos_db%';"
```

Qué cubre la suite y qué no: los tests mockean los DAOs, así que no ven
proxies de Hibernate ni validan JPQL. Una consulta no está validada porque la
suite esté verde.

## Documentación

- [`README.md`](README.md) — qué es este repo, módulos y cómo compilar
- [`docs/Logicas/03-calculos-y-logica.md`](docs/Logicas/03-calculos-y-logica.md) — rachas, puntos, experiencia, logros y catálogo de la tienda
- [`docs/Logicas/04-modelo-entidad-relacion.md`](docs/Logicas/04-modelo-entidad-relacion.md) — modelo de datos
- [`docs/Logicas/05-diagramas-uml.md`](docs/Logicas/05-diagramas-uml.md) — diagramas de clases y flujos
- [`docs/despliegue.md`](docs/despliegue.md) — producción y staging
- [`docs/backups.md`](docs/backups.md) — copias de la base de datos y restauración
- [`docs/ordenador-servidor.md`](docs/ordenador-servidor.md) — las máquinas de trabajo y el agente Hermes

## Estilo de trabajo con el usuario

- Un paso a la vez, confirmar que compila antes de seguir.
- Si algo admite varios diseños o no está claro, preguntar antes de
  decidir — no asumir.
- Nunca hacer push ni tocar sistemas externos —el VPS, la base de datos de
  producción, el repositorio remoto— sin confirmación explícita.

## La web antigua no se toca

En `norday-server/src/main/resources/static/` sólo viven la landing
(`index.html`), las dos páginas legales (`privacidad.html`,
`eliminar-cuenta.html`), `js/nori.js` y las imágenes.

El dashboard web antiguo —`app.html`, `login.html`, `habito.html`,
`habito-detalle.html`, `logros.html` y sus `js/` y `css/`— se borró el
12-sep-2026. No lo enlazaba nadie, no se tocaba desde agosto, y seguía
servido en producción: un `login.html` vivo que guardaba el token en
`localStorage`.

**Regla permanente: ahí no se trabaja.** Ni arreglos, ni refactors, ni
"aprovechar que estamos". Si algún día hace falta la aplicación en formato
web, se empieza de cero y en su sitio, no reanimando esto.

Consecuencias prácticas:

- No se añaden rutas nuevas al `permitAll` de `SecurityConfig` para servir
  páginas. Las que hay son las que hay.
- Los estáticos no llevan JavaScript en línea: lo impide la CSP de Caddy
  (`script-src 'self'`, sin hashes). Cualquier `<script>` en línea o atributo
  `onclick` nuevo se bloqueará en el navegador.
- Al cambiar un DTO de entrada, el único cliente que queda es la aplicación
  móvil, incluida **la APK ya instalada en los dispositivos**. La web ya no
  cuenta como cliente del contrato.

## Lecciones aprendidas

Errores que ya se cometieron una vez. No se vuelven a cometer.

### Persistencia y entidades

- **`LAZY` en una asociación no basta para `open-in-view=false`**: los DAO son
  `@Transactional` por clase y las entidades salen desconectadas. Hace falta
  transacción en quien lee la asociación.
- **Nombres de columna, del repositorio, nunca de memoria.** La FK de
  `registro` es `habito_ref`, no `habito_id`.
- **El nombre real de la base es `habitos_db`**, no `habitas_db`.

### Servicios y reglas de negocio

- **Una aserción sobre el objeto que ha construido el propio test no prueba
  nada.** Para comprobar lo que el servicio guarda hace falta `ArgumentCaptor`
  sobre el `save` del DAO.
- **Una guarda de nulo sobre un campo inyectado es una señal de alarma.**
  `if (dao == null) return ...` sólo existe para que los tests viejos no
  revienten, y su efecto es que el camino nuevo no se prueba.
- **Un `switch` sobre un valor exacto deja de valer en cuanto ese valor puede
  saltar.** Los hitos de racha (3, 7, 30, 100, 365) eran seguros mientras la
  racha subía de uno en uno; la comparación correcta no es `actual == umbral`.
- **Al refactorizar, la regla perezosa se pierde sin hacer ruido.** La rotura
  de racha (`sigueViva`) desapareció en un refactor y el efecto era una racha
  muerta de 6 que seguía a 7 en vez de reiniciarse a 1, repartiendo puntos de
  hito que no tocaban. Al mover lógica, enumerar qué reglas se aplicaban antes
  y comprobar una a una que siguen aplicándose.
- **Test en rojo antes del arreglo.** Si el test nuevo pasa contra el código
  sin arreglar, no demuestra nada: parar.

### Contrato con los clientes

- **Un cambio de contrato también rompe a los clientes viejos.** Al cambiar un
  DTO de entrada, el cliente que queda es la aplicación móvil, incluida la APK
  ya instalada. Si el servidor ignora un campo desconocido, un `null` puede
  borrar datos (`HabitoService:134`).
- **Una línea base de contrato sólo se captura antes de desplegar.** Después
  ya no hay con qué comparar.

### Compilar y desplegar

- **`./mvnw clean test`, no sólo `test`**, después de traer cambios del
  remoto: si no, se ejecuta contra clases compiladas viejas.
- **`mvnw package` con el servicio vivo deja el apagado a medias.** Parar el
  servicio antes de empaquetar.
- **Staging puede estar por detrás de lo que se da por hecho.** Antes de
  verificar nada contra staging, comprobar en qué commit está desplegado, no
  suponerlo.
- **Spring lee `./application.properties` y `./config/application.properties`**
  respecto al `WorkingDirectory` del servicio. Un `application.properties` en
  `./src/main/resources/` no lo lee nadie; dos niveles más arriba habría
  pisado la configuración entera de producción.
- **`mvnw` ya es ejecutable (`100755`)**: la lección antigua de que
  «`./mvnw` no ejecuta en Linux» ya no aplica tal cual.

### Método de trabajo (vale para los cuatro repos)

- **La primera línea de un prompt se comprueba, no se recuerda.** Tres repos
  están en `C:\Dev\Norday\` (`habitos-app`, `habitos_app_mobile`,
  `norday_flutter_core`) y `conocimiento_app_mobile` está en
  `C:\Dev\Conocimiento\`.
- **Un solo agente por repo a la vez.** Codex hizo `dart format` y mergeó a
  `main` sin revisión aunque el prompt lo prohibía. Todo lo que haga otro
  agente se revisa en el remoto antes de mergear.
- **Las cifras de verificación se cuentan contra el repositorio**, nunca se
  copian del roadmap. Y son cifras exactas, no adjetivos.
- **Enumerar sin asumir el patrón**: buscar por la forma que ya has visto sólo
  encuentra lo que ya sabías.
- **Un filtro que no encuentra nada no es un resultado.** El `grep` de
  resúmenes de surefire con `$` final no casó y parecía que los tests no
  habían corrido. Ante una salida vacía, mirar el log completo antes de
  concluir.
- **Un fichero de diagnóstico no prueba nada por existir.** Abrirlo y
  comprobar que contiene el fallo antes de darlo por documentado.
- **La base de una rama `wip` envejece.** Antes de dar una cifra, comprobar de
  qué commit sale la rama.
- **Al sustituir un bloque, incluir el comentario de encima.** Si no, el
  comentario queda sobre otra declaración y describe algo que ya no es cierto.
- **No escribir en el código el término cuya ausencia se va a verificar.**
- **Mirar dónde se pega cada bloque.** Un bloque para la máquina local,
  lanzado en el VPS, llegó a `git push` y pidió credenciales.
- **`git diff` y `git log` abren paginador**: `git --no-pager`.
- **`git diff HEAD~1` compara con el directorio de trabajo**: incluye lo no
  commiteado. Para ver sólo el commit, `git diff HEAD~1 HEAD` o el remoto.
- **Git se niega a operar en un repositorio de otro propietario**
  (`dubious ownership`), como los que deja el sandbox de Codex. En vez de
  añadir `safe.directory`, traer los commits con
  `git fetch "<ruta>" rama:rama` desde el repo propio.
- **PowerShell 5.1 lee los `.ps1` sin BOM como ANSI**: scripts sin acentos, o
  guardados con BOM.
