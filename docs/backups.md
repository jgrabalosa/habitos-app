# Backups y restauración

> Este repositorio es **público**. Aquí no van ni la IP, ni el nombre de la
> máquina, ni credenciales.

Hay dos piezas: el VPS genera las copias, y una máquina Windows se las trae.
Ninguna de las dos demuestra nada por sí sola.

## En el VPS

`/root/backup_bd.sh`, cron `0 3 * * *`. Vuelca **las dos bases**, producción
y staging, a `/root/backups/`, con retención real de **ocho o nueve días**. El
log es `/root/backups/backup.log`, pero lo escribe la redirección del cron
(`>> backup.log 2>&1`), no el script: en una ejecución manual los mensajes
salen por pantalla y no quedan en el log.

**No son siete.** `find -mtime +7` borra lo que supera estrictamente ocho
períodos de 24 h contados desde el instante en que corre el `find`, y ese
instante depende de cuánto tarden los dos `pg_dump` de esa madrugada. El
script está bien: lo que estaba mal era la cifra escrita aquí.

Cada dump se escribe a `.tmp` y sólo se renombra si `pg_dump` termina bien.
Sin eso, un fichero a medias con nombre de dump válido se copia y se da por
bueno.

El volcado lo hace el superusuario, así que el cambio de dueño de los objetos
de staging no le afecta.

**El `find` de retención sólo borra `habitos_db_*.dump`.** Un volcado manual
con otro nombre (`.sql`, `pre_*`) no se limpia nunca. O va a `/root/backups/`
con nombre de dump, o se borra al terminar.

La copia a Google Drive con `rclone` se retiró el 24-sep-2026: se quitó del
script, se borró el remoto, se desinstaló `rclone` del VPS y se revocó el
acceso en la cuenta de Google. **Desde entonces, la única copia fuera del VPS
es la de Windows**, con la limitación que se explica abajo: no es diaria.

## En Windows

Tarea programada `\Norday\Descargar backups BD`. El script y su log están en
`C:\Dev\Norday\backups-bd\`.

Por cada copia: la descarga, verifica el hash y comprueba que `pg_restore`
puede leerla. Si no puede, borra la copia y lo registra como error.

Detalles que costaron encontrarse:

- **La tarea corre "al iniciar sesión" con retraso, no "al iniciar el
  equipo".** Al arrancar correría como SYSTEM y no vería la clave SSH del
  usuario.
- `ssh` lanzado desde una tarea programada puede quedarse colgado con la
  sesión remota ya terminada. Va con `-n`, la entrada redirigida a un fichero
  vacío y un límite de tiempo que lo mate con sus hijos (`taskkill /T /F`:
  `scp` lanza su propio `ssh`).
- **`267009` en `LastTaskResult` es "la tarea sigue en ejecución"**
  (`0x41301`), no un error.
- PowerShell 5.1 lee los `.ps1` sin BOM como ANSI: el script va sin acentos,
  o guardado con BOM.
- El patrón de fecha tolera `_` y `-` entre fecha y hora, así que los dumps
  manuales conviven con los del cron sin romper la retención.

**Consecuencia: la copia externa no es diaria.** Si el equipo no se enciende,
ese día no hay copia. El 13-sep-2026 no hubo ninguna ejecución. Mientras la
tarea dependa del inicio de sesión, los dumps del VPS son la única copia
garantizada, y la de Windows es un refuerzo, no un segundo respaldo fiable.

## Qué demuestra cada comprobación

Son tres niveles y no son intercambiables:

1. **Que existe** — el fichero está y tiene fecha de hoy. Un mensaje de éxito
   impreso por el propio script no cuenta: `backup_bd.sh`, cuando aún subía a
   Drive, llegó a escribir "(local + Drive)" aunque `pg_dump` o `rclone`
   fallaran. Se verifica contra el destino, no contra el log.
2. **Que es idéntico** — el hash coincide. Pero un dump truncado copiado bien
   tiene el mismo hash que el original truncado.
3. **Que se puede leer** — `pg_restore -l` lo lista sin error, o
   `pg_restore -f NUL` (`-f /dev/null` en Linux).

Y ninguno de los tres demuestra que se pueda **restaurar**. Eso sólo lo
demuestra una restauración real.

**Sin errores no es lo mismo que funcionando.** Si el origen deja de generar
copias, el que las descarga no ve nada nuevo y termina con "0 errores". Por
eso el script comprueba además la antigüedad de la última copia.

## Prueba de restauración

Una vez al mes, y después de cada migración de Flyway que cambie el esquema.

**Última: 24-sep-2026**, con el dump `habitos_db_2026-09-24_0300.dump` de la
copia descargada en Windows, así que prueba la cadena entera. Salió bien: 21
tablas en el índice del dump y 21 en la base restaurada, Flyway en la versión
13 con `success = t`, 6 usuarios, 10 hábitos y 11 registros. Los usuarios se
compararon por ID con producción: coinciden todos los que existían a las 03:00,
con la misma fecha de alta, y el único que falta en producción se borró
después del dump. Siguiente, hacia el 24-oct-2026 o antes si entra una
migración que cambie el esquema. Anotar aquí cada una con su fecha y sus
cifras: sin eso no se sabe si toca.

Anteriores: 11-sep-2026, `habitos_db_2026-09-11_0300.dump`, 21 tablas, 6
usuarios, 10 hábitos y 38 registros.

Contra el PostgreSQL 18 local, **puerto 5434**, sobre una base desechable —
nunca contra `habitos_db` local. `pg_restore --no-owner --no-acl
--exit-on-error`, comparar el número de tablas con lo que lista
`pg_restore -l`, comprobar que la última fila de `flyway_schema_history` es
la versión esperada con `success = t`, y borrar la base al terminar.

**Los datos se comparan por ID, no por recuento.** Contar en producción los
usuarios dados de alta antes de la hora del dump no cuadra si alguno se ha
borrado después. Se listan `usuario_id` y `fecha_registro` en las dos bases y
se comparan fila a fila.

**Un `pg_restore` más antiguo que el servidor que hizo el dump falla** con
"unsupported version in file header" aunque el dump esté bien. Hay que usar
el cliente de la misma versión mayor o superior. La versión menor no importa:
un `pg_restore` 18.4 restaura sin problema un dump de un servidor 18.6.

## Comparar producción con staging

`pg_restore -l` de los dos dumps y comparar las entradas. El dump de staging
trae una entrada `SCHEMA - public` que el de producción no tiene: en staging
se reasignó explícitamente el dueño del esquema `public` y `pg_dump` lo
registra, mientras que producción conserva el dueño por defecto. Es
inofensivo y explica una diferencia de una línea en el recuento.
