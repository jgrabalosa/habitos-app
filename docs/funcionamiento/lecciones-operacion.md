# Lecciones aprendidas: compilar, desplegar y método de trabajo

Copia íntegra de las secciones «Compilar y desplegar» y «Método de trabajo»
del `CLAUDE.md` anterior (T-005a). El `CLAUDE.md` mantiene las que valen en
cada sesión (compilar, git, un agente por repo); aquí están todas, para
consulta al desplegar o al trabajar con otros agentes.

## Compilar y desplegar

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

## Método de trabajo (vale para los cinco repos)

- **La primera línea de un prompt se comprueba, no se recuerda.** Cuatro
  repos están en `C:\Dev\Norday\` (`habitos-app`, `habitos_app_mobile`,
  `norday_flutter_core`, `enfoque_app_mobile`) y `conocimiento_app_mobile`
  está en `C:\Dev\Conocimiento\`.
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
