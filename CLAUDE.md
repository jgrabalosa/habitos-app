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
- **`habitos/`** — el dominio de Norday Hábitos: `com.norday.habitos`.
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

El `Dockerfile` copia el pom raíz, los cuatro poms de módulo y las cuatro
carpetas `src/`, y toma el jar de `norday-server/target/`.

⚠️ **El `Dockerfile` no es la vía de despliegue y está desfasado.**
Producción y staging son clones de este repositorio compilados en el propio
servidor y arrancados con systemd — ver `docs/despliegue.md`. El
`Dockerfile` viene de un alojamiento anterior, compila con `-DskipTests`
(que contradice la regla del proyecto) y fija `ENV TZ=UTC`, que por lo tanto
hoy no se aplica en ningún sitio.

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

## Estilo de trabajo con el usuario

- Un paso a la vez, confirmar que compila antes de seguir.
- Si algo admite varios diseños o no está claro, preguntar antes de
  decidir — no asumir.
- Nunca hacer push ni tocar sistemas externos —el VPS, la base de datos de
  producción, el repositorio remoto— sin confirmación explícita.
