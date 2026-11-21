# Norday — Contexto del proyecto (Backend)

Este backend (Spring Boot + PostgreSQL) es el primero de un ecosistema de
apps Norday. Todas las apps futuras compartirán este mismo backend, la
misma base de datos y la misma cuenta de usuario/monedas. Cada decisión
de arquitectura se toma pensando en esa reutilización.

## Regla de arquitectura obligatoria: Motor vs Disparadores

- **Motor** = genérico, reutilizable por cualquier app futura del
  ecosistema: auth/JWT, usuarios, sistema de puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota, email, FCM.
- **Disparadores** = lo específico de cada dominio, cada uno en su
  módulo: `habitos` (Habito, Registro, Frecuencia, Categoria, sus
  schedulers y controllers), `conocimiento` (píldoras) y `enfoque`
  (sesiones de Norday Focus).

**Ningún servicio genérico puede conocer conceptos de dominio como
"hábito", "registro" o "categoría".** El motor recibe códigos/eventos
genéricos (ej. `ganarExperiencia(usuarioId, cantidad)`, no
`ganarExperienciaPorHabito(...)`). Si una tarea toca código del motor,
comprueba primero si de verdad pertenece ahí o si en realidad es lógica
específica de hábitos que se ha colado.

## Paquetes, dependencias y módulos (resumen)

Código por dominio bajo `com.norday`: `core` y `gamificacion` (motor, mismo
módulo Maven `norday-motor`), `habitos`, `conocimiento` y `enfoque`
(dominios, un módulo cada uno) y `norday-server` (el único jar arrancable).
`NordayApplication` vive en la raíz `com.norday`.

- `habitos`, `conocimiento` y `enfoque` → pueden importar de `core` y
  `gamificacion`.
- `core` ↔ `gamificacion` → permitido en ambos sentidos.
- `core` o `gamificacion` → `habitos`, `conocimiento` o `enfoque`: **PROHIBIDO**.
- Entre `habitos`, `conocimiento` y `enfoque`: **PROHIBIDO** en los dos
  sentidos. Si uno necesita algo de otro, el motor declara un puerto y el
  otro lo implementa (`DestinoMinutos`, `CumplimientoDiarioPort`).

Maven lo impone (una importación prohibida no compila). Ningún archivo de
`core` ni de `gamificacion` puede contener `Habito`, `Registro`, `Racha` o
`Categoria`, ni en comentarios ni en logs. Dos greps, cero resultados:

```bash
grep -rn "com\.norday\.habitos" norday-motor/src/main/java
grep -rnE "\b(Habito|Registro|Racha|Categoria)\w*" norday-motor/src/main/java
```

## Compilar y probar

- **`./mvnw clean test`, no sólo `test`**, después de traer cambios del
  remoto: si no, se ejecuta contra clases compiladas viejas.
- El `package` va sin `-DskipTests`: toda máquina que compile necesita la BD
  `habitos_db_test` (dueño `norday_test`; creación en `docs/funcionamiento/tests.md`).
- Los `@SpringBootTest` leen `norday-server/src/test/resources/application.properties`,
  que tapa por completo al de `src/main/resources` (que apunta a la BD real).
  Toda propiedad obligatoria nueva de producción va también ahí.
- **`create-drop`:** si el `spring.datasource.url` de test apunta a una BD con
  datos, la vacía al arrancar. El nombre real de la base es `habitos_db`.
- Tests en verde **no** prueban el aislamiento de la BD de test; ver
  `docs/funcionamiento/tests.md`.
- Los unitarios mockean los DAOs: no validan JPQL. Una consulta que ningún
  `@SpringBootTest` recorre no está validada porque la suite esté verde.
- `mvnw package` con el servicio vivo deja el apagado a medias: parar el
  servicio antes de empaquetar.

## Reglas cortas que valen siempre

- **Datos del usuario:** `eliminarCuenta` y `exportar` no conocen tablas. Un
  módulo que guarde datos colgando de `Usuario` aporta su `LimpiadorDatosUsuario`
  **y** su `ExportadorDatosUsuario`.
- **Push:** el token se pide con `DispositivoService.tokenDe(usuarioId, appId)`,
  nunca con `usuario.getFcmToken()`. Una app nueva se añade a `AppNorday` antes
  de publicarse.
- **Catálogos:** sembrar un logro o producto genérico (`origenApp = null`) y
  registrarlo en el array de `CatalogoGamificacionInitializer` son la misma
  acción: si no, el siguiente arranque lo retira. Idempotencia por `codigo`,
  fila a fila. Retirado no es borrado; reactivar no es simétrico.
- **Identidad:** no reutilizar `otorgarProducto` para elegir identidad.
  `asegurarIdentidad` se llama desde el controlador, nunca desde un servicio
  `@Transactional`.
- **Hora:** nunca `LocalDate.now()` sin zona; la zona sale del usuario
  (`ZonaUsuarioService`). Los sellos de auditoría van en UTC explícito. La JVM
  no fija zona por defecto.
- **Completar y deshacer:** todo efecto nuevo de un completado debe quedar en
  `ReversionRegistro`. Las monedas se compensan, no se borran.
- **Bloqueos:** siempre usuario y después hábito (`HabitoService.bloquear`).
  Todo lo que concede logros, monedas o XP bloquea al usuario como primera
  escritura. Toda espera de bloqueo tiene tope y acaba en 503 traducido.
- **Textos:** el motor no lleva texto específico de una app; cada módulo
  aporta su bundle (`ProveedorMensajes`). Base en español; idiomas `es`, `en`, `pt`.
- **Cliente:** el único cliente es la app móvil, incluida la APK ya instalada.
  Un cambio de DTO de entrada la rompe; un `null` es «sin cambio».
- **La web antigua no se toca:** en `static/` sólo la landing y las páginas
  legales. Sin rutas nuevas en `permitAll`, sin JavaScript en línea (CSP).

## Antes de tocar…, lee…

Las reglas detalladas viven en `docs/`. Antes de tocar la parte del código
indicada, lee el fichero (la carpeta `docs/funcionamiento/` agrupa un tema
por fichero):

- Antes de añadir un paquete o módulo, o de importar entre dominios, lee
  `docs/funcionamiento/paquetes-y-modulos.md`.
- Antes de tocar `UsuarioService.eliminarCuenta`, `ExportacionDatosService` o
  cualquier `Limpiador*` / `Exportador*`, lee `docs/funcionamiento/datos-de-usuario.md`.
- Antes de tocar `DispositivoService`, `AppNorday`, `fcm-token` o cualquier envío
  push, lee `docs/funcionamiento/notificaciones.md`.
- Antes de tocar `enfoque`, `SesionEnfoqueService`, `DestinoMinutos*`,
  `PremioEnfoqueService` o `CumplimientoCombinado`, lee
  `docs/funcionamiento/focus-destino-minutos.md`.
- Antes de tocar un `*Initializer`, `Logro`/`Producto`, o la identidad del
  onboarding (`otorgarIdentidadElegida`, `asegurarIdentidad`), lee
  `docs/funcionamiento/catalogos.md`.
- Antes de tocar cálculos de «hoy», `ZonaUsuarioService`, `Racha` o
  `RachaService`, lee `docs/funcionamiento/zona-horaria-y-rachas.md`.
- Antes de tocar `RegistroService`, `ReversionRegistro` o `ReversionLogro`, lee
  `docs/funcionamiento/deshacer-completado.md`.
- Antes de tocar `ProveedorMensajes`, `mensajes/*.properties`,
  `BundlesMensajesTest`, `EmailService`, `CorreoBienvenidaService` o la
  plantilla `correo/`, lee `docs/funcionamiento/textos.md`.
- Antes de tocar `norday-server/src/test/resources`, un test de concurrencia o la
  BD de test, lee `docs/funcionamiento/tests.md`.
- Antes de tocar `norday-server/src/main/resources/static/` o `SecurityConfig`
  (`permitAll`), lee `docs/funcionamiento/web-estatica.md`.
- Antes de escribir código de persistencia, bloqueos, entidades o servicios de
  reglas de negocio, o de cambiar un DTO, lee
  `docs/funcionamiento/lecciones-persistencia-y-servicios.md`.
- Antes de desplegar, verificar contra staging o trabajar con otros agentes, lee
  `docs/funcionamiento/lecciones-operacion.md`.

Otros documentos:

- [`README.md`](README.md) — qué es este repo, módulos y cómo compilar
- [`docs/Logicas/03-calculos-y-logica.md`](docs/Logicas/03-calculos-y-logica.md) — rachas, puntos, experiencia, logros, catálogo de la tienda y «Bloqueos»
- [`docs/Logicas/04-modelo-entidad-relacion.md`](docs/Logicas/04-modelo-entidad-relacion.md) — modelo de datos
- [`docs/Logicas/05-diagramas-uml.md`](docs/Logicas/05-diagramas-uml.md) — diagramas de clases y flujos
- [`docs/despliegue.md`](docs/despliegue.md) — producción y staging
- [`docs/backups.md`](docs/backups.md) — copias de la base de datos y restauración
- [`docs/ordenador-servidor.md`](docs/ordenador-servidor.md) — las máquinas de trabajo y el agente Hermes
- `docs/decisiones/` — decisiones con alternativas descartadas

## Estilo de trabajo con el usuario

- Un paso a la vez, confirmar que compila antes de seguir.
- Si algo admite varios diseños o no está claro, preguntar antes de
  decidir — no asumir.
- Nunca hacer push ni tocar sistemas externos —el VPS, la base de datos de
  producción, el repositorio remoto— sin confirmación explícita.

## Lecciones aprendidas

Errores que ya se cometieron una vez. No se vuelven a cometer. Las de
persistencia, servicios y contrato están en
`docs/funcionamiento/lecciones-persistencia-y-servicios.md`; las de compilar,
desplegar y método de trabajo, completas, en `docs/funcionamiento/lecciones-operacion.md`.
Las que valen en cada sesión:

- **Test en rojo antes del arreglo.** Si el test nuevo pasa contra el código
  sin arreglar, no demuestra nada: parar.
- **Una aserción sobre el objeto que ha construido el propio test no prueba
  nada.** Hace falta `ArgumentCaptor` sobre el `save` del DAO.
- **Nombres de columna, del repositorio, nunca de memoria.** La FK de
  `registro` es `habito_ref`, no `habito_id`.
- **Un test no compara el texto de un error de PostgreSQL: mira su SQLSTATE**
  (`23505`, `40P01`).
- **Staging puede estar por detrás de lo que se da por hecho.** Comprobar en
  qué commit está desplegado.
- **Un solo agente por repo a la vez.** Todo lo que haga otro agente se revisa
  en el remoto antes de mergear.
- **La primera línea de un prompt se comprueba, no se recuerda** (repos en
  `C:\Dev\Norday\`, salvo `conocimiento_app_mobile` en `C:\Dev\Conocimiento\`).
- **Las cifras de verificación se cuentan contra el repositorio**, nunca se
  copian del roadmap. Cifras exactas, no adjetivos.
- **Un filtro que no encuentra nada no es un resultado.** Ante una salida
  vacía, mirar el log completo antes de concluir.
- **Al sustituir un bloque, incluir el comentario de encima.**
- **No escribir en el código el término cuya ausencia se va a verificar.**
- **Mirar dónde se pega cada bloque** (local o VPS): uno llegó a `git push`.
- **`git diff` y `git log` abren paginador**: `git --no-pager`.
- **`git diff HEAD~1` compara con el directorio de trabajo**: para ver sólo el
  commit, `git diff HEAD~1 HEAD` o el remoto.
- **`dubious ownership`**: no añadir `safe.directory`; traer los commits con
  `git fetch "<ruta>" rama:rama` desde el repo propio.
