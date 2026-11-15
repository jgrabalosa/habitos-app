# Despliegue

> Este repositorio es **público**. Aquí no van ni la IP, ni el nombre de la
> máquina, ni la ruta de la clave SSH, ni ninguna credencial. Los datos de
> acceso viven fuera del repo.

## Dos despliegues en la misma máquina

| | Producción | Staging |
|---|---|---|
| Ruta | `/opt/norday-backend` | `/opt/norday-backend-staging` |
| Servicio | `norday-backend` | `norday-backend-staging` |
| Base de datos | `habitos_db` | `habitos_db_staging` |
| Usuario de Postgres | `habitos_app` | `norday_staging` |
| Perfil Spring | ninguno activo | `staging` |
| Configuración | empaquetada en el jar | `config/application.properties`, fuera del jar |

Los dos son clones de este repositorio que se compilan en el propio servidor.
El `application.properties` de producción está en `.gitignore` —es la única
ruta que el `.gitignore` excluye— y sólo existe en la máquina: hay que
editarlo allí y el `git pull` nunca lo trae. El
`application-staging.properties`, en cambio, **sí** está versionado: viaja en
el repositorio y llega a la máquina con el `git pull`, así que se edita aquí y
no allí.

Existe además una tercera base de datos, `habitos_db_test`, contra la que corren
los `@SpringBootTest`. La apunta
`norday-server/src/test/resources/application.properties`, que **sí** está
versionado. De él depende que compilar no escriba en producción.

## Las credenciales de staging viven fuera del jar

Desde el 12-sep-2026 staging tiene su propio usuario de Postgres,
`norday_staging`, dueño de `habitos_db_staging`, de su esquema `public` y de
sus tablas y secuencias. Antes compartía el usuario de producción: las bases
estaban separadas, las llaves no.

Sus credenciales están en `/opt/norday-backend-staging/config/application.properties`,
que Spring lee por ser `./config/` respecto al `WorkingDirectory` del
servicio. Ese fichero:

- no está en el repositorio, y está excluido en `.git/info/exclude` del clon
  de la máquina para que un `git add -A` no lo capture;
- tiene permisos `600` y sobrevive a los `git pull` y a las recompilaciones;
- repite la URL de la base a propósito, para que staging no pueda acabar
  apuntando a producción si la precedencia entre ficheros cambiara.

**`application-staging.properties` está versionado y este repositorio es
público: ahí no va ninguna credencial, nunca.**

Si algún día se clona staging en otra máquina, ese fichero hay que recrearlo
a mano. Sin él, el servicio arranca leyendo el `application.properties`
empaquetado, que es el de producción.

## El procedimiento

Staging primero, siempre. Tres reglas sin excepción:

- **El código se trae antes que `mvn`**: el commit que aísla los tests puede
  ser justo el que todavía no está en la máquina.
- **El servicio se para antes de empaquetar.** `mvn clean package` con el
  servicio vivo deja el apagado a medias (`clean` borra el jar que está
  corriendo), y los tests necesitan conexiones libres en Postgres (ver
  abajo).
- **Se anota el commit** que se va a compilar, con `git --no-pager log -1`.

Staging compila una rama concreta sin rama local, con el HEAD suelto en el
commit del remoto (hoy, `wip-enfoque`):

```bash
cd /opt/norday-backend-staging
git fetch origin
git switch --detach origin/<rama>
git --no-pager log -1 --oneline
systemctl stop norday-backend-staging
mvn clean package
systemctl start norday-backend-staging
```

Y después producción, siempre desde `main` y con backup por delante:

```bash
/root/backup_bd.sh
cd /opt/norday-backend
git pull
git --no-pager log -1 --oneline
systemctl stop norday-backend
mvn clean package
systemctl start norday-backend
```

Producción queda parada lo que tarde el `package`, suite incluida: se elige
una hora de poco uso. En el VPS se usa `mvn`, no `./mvnw`.

## `mvn clean package` va SIN `-DskipTests`

No es un descuido: es la regla. Cinco tests estuvieron rotos tres sesiones sin
que nadie se enterara porque el despliegue se los saltaba.

Los tests **no** son solo mocks: los `@SpringBootTest` de `norday-server`
(los de concurrencia y los de integración) levantan el contexto contra
Postgres de verdad, en `habitos_db_test`. Para que el `package` pase en el
VPS hacen falta dos cosas:

- **`habitos_db_test`**, con `norday_test` como dueño (ver `CLAUDE.md`,
  «Tests»).
- **Conexiones libres en Postgres.** Cada contexto de test guarda su pool
  (hasta 5) y la suite levanta varios; con el servicio que se despliega
  todavía vivo, sus conexiones también cuentan. Si salta «remaining connection
  slots are reserved», no se reintenta con `-DskipTests`: se mira qué tiene
  conexiones abiertas (`SELECT datname, count(*) FROM pg_stat_activity GROUP
  BY datname;`).

## Cómo se comprueba que arrancó

Mirando la **hora** de la línea `Started NordayApplication` en el log:

```bash
journalctl -u norday-backend -n 50 --no-pager | grep "Started NordayApplication"
```

`systemctl is-active` no vale: sólo dice que el proceso existe, no que Spring
haya levantado el contexto. El arranque tarda entre 17 y 70 segundos, así que un
`sleep` corto tampoco demuestra nada.

## Línea base de contrato

Una línea base sólo se puede capturar **antes** de desplegar. Después ya no
hay con qué comparar.

El procedimiento: capturar las respuestas de los endpoints afectados, hacer
el despliegue, capturarlas otra vez y comparar con `diff -r`. Normalizar las
dos con el mismo `json.dump(..., sort_keys=True, ensure_ascii=False)`.
`/semana` se pide con un `desde` fijo; `/dashboard` depende de "hoy", así que
el antes y el después tienen que ser el mismo día.

Hace falta un token, y se firma con el `jwt.secret` que viaja dentro del jar
del servicio, en `BOOT-INF/classes/application.properties`. Se extrae con
`python3` y `zipfile`, porque no hay `unzip`.

- **Extraer siempre a `/root`, nunca a `/tmp`**: ese fichero lleva el secreto
  y las contraseñas en claro. Se borra con `shred -u` al terminar.
- Claims: `sub` es el email del usuario, más `usuarioId`.
- **El algoritmo lo elige la biblioteca por el tamaño de la clave**, porque
  `JwtUtil` usa `Keys.hmacShaKeyFor(secreto.getBytes())` y un `signWith` sin
  algoritmo explícito. En producción el secreto son 64 bytes y sale
  **HS512**; en staging eran 46 y salía HS256. No se da por supuesto: se mide.
- Sin sesión válida (sin token, caducado o mal firmado) se devuelve **401**
  desde el 25-sep-2026; antes era 403. El 403 queda para pedir datos de otro
  usuario.

## Despliegue conjunto: hábitos en minutos + Norday Focus

Decidido por Quim: los hábitos en minutos y Norday Focus salen **juntos**, en
la misma versión, cuando estén los arreglos pendientes. La línea de
publicación es `wip-enfoque`, en este repo y en `habitos_app_mobile`; el core
sale por su tag `v0.55.0` (desde su `wip`) y `enfoque_app_mobile`, desde su
`wip`. Hasta entonces no se publica nada.

Lo que entra en el backend: seis migraciones, de V21 a V26 (producción está
en V20, que es la última de `main`):

| Migración | Qué hace |
|---|---|
| V21 | `hito_pagado`, el libro de hitos, con relleno desde los registros |
| V22 | `habito_objetivo`, el historial de objetivos, con una versión por hábito existente |
| V23 | `habito.unidad` y `minutos_objetivo`; `registro.cantidad`, `origen` y `clave_cliente` (todas con valor por defecto o nulas) |
| V24 | `usuario_dispositivo`, un token por usuario y app, rellena con `usuario.fcm_token` |
| V25 | la fila `enfoque` en `version_app`, a 1 |
| V26 | `sesion_enfoque`, las sesiones de Norday Focus |

### 0. Antes, una sola vez

- **Repo de Focus en GitHub**: hecho el 15-nov-2026
  (`jgrabalosa/enfoque_app_mobile`, rama `wip`).
- **Firebase** (`habitos-app-5a054`): `com.norday.enfoque.prueba` está dado
  de alta desde el 15-nov-2026, con la SHA-1 de depuración de la máquina de
  Quim. Falta `com.norday.enfoque`, con las SHA-1 de depuración y de subida,
  y volver a bajar el `google-services.json` con las dos (no se versiona).
- **Google Cloud**: un cliente OAuth Android por paquete y huella (Firebase
  sola no basta para Google Sign-In). Cuando Play genere la clave de firma
  de apps de `com.norday.enfoque`, añadir también su SHA-1.
- **Play Console**: crear `com.norday.enfoque` con su ficha, la política de
  privacidad y la URL de borrado de cuenta: las mismas de Habits,
  `https://norday.app/privacidad.html` y `https://norday.app/eliminar-cuenta.html`.
  En `wip-enfoque` ya hablan de las dos apps (15-nov-2026), así que solo
  valen para Focus una vez desplegado este backend en producción. Al
  desplegar, poner en «Última actualización» la fecha real de publicación. El
  borrador de los textos de la ficha está en el doc «Norday Focus — ficha de
  Google Play (borrador)». Comprobar si la cuenta de
  desarrollador exige prueba cerrada antes de Producción para una app nueva.
- **Primer build de Focus**: hecho el 15-nov-2026, en debug y con el flavor
  `prueba`. En el móvil ya funcionan el login, Inicio y el enlace desde
  Habits. Faltan los avisos de gracia y de fin y una build de release (R8).

### 1. El core: tag `v0.55.0`

En `norday_flutter_core`, Quim mergea `wip` en `main`, comprueba con
`git --no-pager log -1` que está en el commit del merge, pone el tag
`v0.55.0` y lo sube. Anotar el commit: `git rev-parse v0.55.0^{commit}`.

### 2. Las dos apps con el tag

- **Norday Habits** (`wip-enfoque`): quitar `pubspec_overrides.yaml`,
  `flutter pub upgrade norday_flutter_core`, comprobar que el `resolved-ref`
  del `pubspec.lock` es el commit del paso 1, `flutter analyze` y
  `flutter test`, y commitear el lock. Subir `version:` en el `pubspec.yaml`:
  hoy es `1.1.0+5` y el versionCode 5 ya se usó en Play el 22-oct-2026, así
  que el nuevo es **6 o más**.
- **Norday Focus** (`wip`): lo mismo, y además quitar la línea `pubspec.lock`
  (y su comentario) del `.gitignore` antes de commitear el lock.

### 3. Staging

1. Anotar en qué commit está staging y en qué versión está su Flyway, antes
   de tocar nada:

   ```bash
   git -C /opt/norday-backend-staging --no-pager log -1 --oneline
   psql -U postgres -d habitos_db_staging -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
   ```

2. Comprobar que existe el dump de esa madrugada, o hacer uno con
   `/root/backup_bd.sh` (ver [`backups.md`](backups.md)).
3. Desplegar `wip-enfoque` con el procedimiento de staging (arriba).
4. Comprobar:
   - la línea `Started NordayApplication` con la hora de ahora;
   - Flyway en la 26 con `success = t` (la consulta de arriba);
   - ningún hábito sin historial (debe dar 0):
     `SELECT count(*) FROM habito h WHERE NOT EXISTS (SELECT 1 FROM habito_objetivo o WHERE o.habito_ref = h.habito_id);`
   - `version_app` con `habitos` y `enfoque`:
     `SELECT app_id, version_minima FROM version_app ORDER BY app_id;`
   - la versión mínima de Focus, sin sesión:
     `curl -s https://staging-api.norday.app/api/version/enfoque` →
     `{"versionMinima":1}`.

### 4. Prueba en staging con las dos apps de prueba

Con «Norday PRUEBA» y «Norday Focus PRUEBA» en el mismo móvil y la misma
cuenta:

- **Habits**: un hábito de veces se comporta como antes; crear uno de
  minutos, apuntar minutos desde el panel, deshacer, cambiar el objetivo
  (y anular un cambio programado); pausar y reanudar; «Empezar sesión» abre Focus de prueba con el hábito.
- **Focus**: una sesión de 10 minutos o más con destino suma sus minutos al
  hábito (y paga el hábito, no Focus); una sin destino; una perdida (sale
  `RECHAZADO` y no suma); una en modo avión, que se queda en la cola y se
  envía al volver la red sin sumar dos veces.
- **Los dos tokens**: `SELECT app_id FROM usuario_dispositivo WHERE
  usuario_ref = <id>;` da dos filas, `habitos` y `enfoque`. En la prueba del
  15-nov-2026 dio **0 filas** para la cuenta de prueba (usuario 9) aunque un
  aviso push sí llegó al móvil: puede que saliera de la reserva
  `usuario.fcm_token` y no de la tabla. Mirarlo antes de publicar: si la app
  ya había enviado ese token antes de V24, la caché del móvil
  (`fcm_token_enviado_<id>`) impide reenviarlo.
- **La APK de la versión 5** (la 1.1.0 que está en Play): los hábitos de
  veces siguen igual, y completar uno de minutos da **409** con el texto
  «Actualiza Norday Habits» (`registro.minutos.actualizar.app`), nunca un
  minuto por defecto. La APK de Play va contra producción, así que aquí hace
  falta una build de ese commit con el flavor `prueba`. *A comprobar* que
  ese commit ya tenía el flavor; si no, esta prueba se hace en producción
  después del paso 5, con la APK de Play y un hábito de minutos creado desde
  la versión nueva.

### 5. Producción

1. Quim mergea `wip-enfoque` en `main`.
2. **Línea base de contrato antes de desplegar** (ver «Línea base de
   contrato»): al menos `/api/habitos/usuario/{id}/resumen`, `/dashboard` y
   `/semana` (con un `desde` fijo), `/api/habitos/{id}`,
   `/api/habitos/{id}/detalle` y `/api/registros/habito/{id}`.
3. El procedimiento de producción (arriba): backup, `git pull`, parar,
   `mvn clean package`, arrancar.
4. Comprobar el arranque y Flyway: de la 20 a la 26, las seis con
   `success = t`. Las mismas consultas del paso 3, contra `habitos_db`.
5. Capturar otra vez la línea base y comparar: **solo campos añadidos** (por
   ejemplo `cantidad` y `origen` en los registros, `unidad` y
   `minutosObjetivo` en los hábitos). Un campo que cambia de valor o
   desaparece es un fallo de contrato para la APK instalada.
6. **Prueba de restauración** con el primer dump posterior al despliegue:
   está vencida desde el 24-oct-2026 y es obligatoria tras este despliegue
   (ver [`backups.md`](backups.md)).

### 6. Google Play

- Las builds de release salen de `main` limpio, en los dos repos de apps.
- **Habits con «Empezar sesión» no llega a Producción antes de que Focus sea
  visible en Producción.** El botón abre `market://details?id=com.norday.enfoque`
  a usuarios de producción, y una ficha que solo está en prueba cerrada no
  la ven: llegarían a una ficha que no existe. Lo ideal es publicar las dos
  a la vez, con Habits en lanzamiento por fases.
- Comprobar desde un móvil **que no sea tester** que la ficha de Focus se ve
  antes de soltar Habits.
- **Pendiente de probar en cuanto exista la ficha**: «Empezar sesión» en un
  móvil **sin** Focus instalada tiene que abrir esa ficha de Play. En la
  prueba en staging del 15-nov-2026 no llevaba a Play, pero entonces la ficha
  no existía y no se investigó. Con Focus PRUEBA instalada, el enlace abre
  Focus con el hábito elegido y funciona.

### 7. Versión mínima, solo cuando la nueva se vea en Play

- `enfoque` se queda en 1 (V25): no hay builds de Focus anteriores.
- `habitos`: opcional, lo decide Quim. La APK 5 no puede completar hábitos
  de minutos y recibe 409; subir la mínima la obliga a actualizar. Nunca
  antes de que la versión nueva se vea en Play:

  ```sql
  UPDATE version_app SET version_minima = <versionCode nuevo> WHERE app_id = 'habitos';
  ```

### 8. Después

- `CHANGELOG`/`README` de Habits con lo que sale.
- Quitar «Pendiente con el core 0.55.0» de los `CLAUDE.md` de Habits y de
  Focus, y confirmar que no queda ningún `pubspec_overrides.yaml`.
- En el `CLAUDE.md` del core, «Consumidores hoy» con Norday Focus.

### Vuelta atrás

**Apps.** Play no deja bajar el versionCode: o se para el lanzamiento por
fases (quien ya la tiene se queda con ella), o se publica una build nueva con
un versionCode mayor. La cola de Focus guarda las sesiones hasta 7 días: un
backend caído o un 404 no las pierde (las reintenta), y las de más de 7 días
las descarta la propia app porque el backend las rechazaría.

**Backend.** Lo comprobado en el código:

- **Flyway del jar viejo arranca sobre la base en la 26.** El jar de `main`
  trae migraciones hasta la V20 y Flyway 10.20.1 (el de Spring Boot 3.4.5).
  Su `ignoreMigrationPatterns` por defecto es `*:future`: las migraciones
  aplicadas que el jar no conoce se ignoran al validar, en vez de fallar.
  `application-staging.properties` no cambia ese valor. *A comprobar*: que el
  `application.properties` de producción (no versionado) tampoco lo cambie
  (`grep -i flyway` en la máquina), y el arranque real en staging.
- **Hibernate `validate` no debería quejarse**: las entidades del jar viejo
  usan un subconjunto de las columnas, y las columnas nuevas de V23 tienen
  valor por defecto o admiten nulo, así que sus `INSERT` siguen valiendo.
  *A comprobar en staging.*
- **Pero el jar viejo rompe borrados**, porque no conoce las tablas nuevas y
  sus FK no llevan `ON DELETE CASCADE`:
  - **borrar un hábito falla para todos**: V22 dio a cada hábito una fila en
    `habito_objetivo`, y `HabitoService.eliminar` de `main` no la borra;
  - **la baja de cuenta falla** para quien tenga hábitos (`habito_objetivo`)
    o token (`usuario_dispositivo`), y para quien tenga sesiones de Focus
    (`sesion_enfoque`): el `LimpiadorHabitos` de `main` no conoce las dos
    primeras y no hay `LimpiadorDispositivos` ni `LimpiadorEnfoque`. La baja
    es obligatoria para Play: una vuelta atrás así tiene que ser corta;
  - **deshacer un completado que pagó un hito falla** (FK
    `hito_pagado.registro_ref`).
- **Y deja datos que el jar nuevo lee distinto al volver a avanzar**
  (deducido del código, *a comprobar en staging*):
  - un cambio de objetivo hecho con el jar viejo en un hábito con historial
    no queda en `habito_objetivo`, y el jar nuevo pone al día la copia del
    hábito desde el historial: el cambio se pierde;
  - un hito pagado con el jar viejo no queda en el libro, y podría volver a
    pagarse si los días hechos bajan y vuelven a cruzarlo;
  - los tokens: lo de la sección siguiente (V24), con su SQL de
    resincronización.
- **Una vez publicadas las apps, el backend no vuelve atrás.** Focus
  recibiría 404 en `/api/enfoque` (sus sesiones esperarían en la cola) y
  Habits nueva mandaría minutos que el jar viejo ignora. Se arregla hacia
  delante.
- **Restaurar el dump previo** devuelve la base a la V20 de verdad, pero
  pierde todo lo escrito desde el backup: último recurso, y solo antes de
  publicar las apps.

## Volver al jar anterior a V24 (tokens de notificaciones)

Desde V24 cada app guarda su token en `usuario_dispositivo`. El jar
anterior no conoce esa tabla: lee y escribe solo `usuario.fcm_token`, que el
jar nuevo sigue escribiendo para `habitos`. Volver atrás arranca y los
recordatorios siguen saliendo, pero:

- **Solo es seguro mientras Norday Focus no esté publicada.** El jar
  anterior no distingue apps: el token de Enfoque pisaría la columna, igual
  que antes de V24.
- **Al volver a avanzar, la tabla está obsoleta** para quien cambió de token
  durante la vuelta atrás: la fila de `habitos` conserva el token viejo y
  manda sobre la columna. El jar nuevo usa la columna solo cuando no hay fila.
  Hay que resincronizar `habitos` desde la columna justo después de arrancar
  el jar nuevo:

```sql
BEGIN;
-- La columna manda: quien tiene token en usuario.fcm_token, a la tabla.
INSERT INTO usuario_dispositivo (usuario_ref, app_id, fcm_token, actualizado_en)
SELECT usuario_id, 'habitos', fcm_token, (NOW() AT TIME ZONE 'UTC')
FROM usuario
WHERE fcm_token IS NOT NULL AND BTRIM(fcm_token) <> ''
ON CONFLICT (usuario_ref, app_id) DO UPDATE
SET fcm_token = EXCLUDED.fcm_token, actualizado_en = EXCLUDED.actualizado_en
WHERE usuario_dispositivo.fcm_token IS DISTINCT FROM EXCLUDED.fcm_token;
-- Quien se quedó sin token en la columna, también sin fila de habitos.
DELETE FROM usuario_dispositivo d
USING usuario u
WHERE d.usuario_ref = u.usuario_id AND d.app_id = 'habitos'
  AND (u.fcm_token IS NULL OR BTRIM(u.fcm_token) = '');
COMMIT;
```

Las filas de otras apps no se tocan: el jar anterior no las escribe.

## Backups

Documentados aparte, en [`backups.md`](backups.md). Antes de desplegar en
staging hay que comprobar que existe el dump de esa madrugada, o hacer uno
manual: `backup_bd.sh` vuelca las dos bases.

## Antes de tocar configuración

Los cambios de configuración se prueban por línea de comandos antes de editar
ningún fichero. Por ejemplo, para validar un cambio de `ddl-auto`:

```bash
java -jar norday-server/target/*.jar --spring.jpa.hibernate.ddl-auto=validate
```

Y sólo si arranca, se edita el `application.properties` de la máquina.

## Los backends escuchan sólo en loopback

Los dos servicios llevan `server.address=127.0.0.1`, así que sólo aceptan
conexiones desde la propia máquina. Caddy les habla por loopback y no se ve
afectado.

- **Staging**: en `/opt/norday-backend-staging/config/application.properties`,
  el fichero externo. Sobrevive a `git pull` y a las recompilaciones.
- **Producción**: en
  `norday-server/src/main/resources/application.properties`, junto al
  `server.port`. **Ese fichero se empaqueta en el jar al compilar**, así que
  el cambio sólo surte efecto tras `mvn clean package`: un `systemctl restart`
  a secas arrancaría el jar anterior. Y como ese fichero sólo existe en la
  máquina, la línea se perdería sin ningún aviso si se restaurara desde otra
  copia.

Se verifica en dos sitios. Dónde escucha el proceso, desde el VPS:

    ss -ltnp | grep -E '8080|8081'

Debe mostrar `[::ffff:127.0.0.1]:8080` y `[::ffff:127.0.0.1]:8081`, no `*:8080`
ni `*:8081`. Esa notación es IPv6 mapeando una dirección IPv4 y equivale a
`127.0.0.1`.

Y qué ve internet, **desde fuera del VPS**, nunca desde dentro:

    Test-NetConnection -ComputerName api.norday.app -Port 8080
    Test-NetConnection -ComputerName api.norday.app -Port 443

El 8080 debe dar `TcpTestSucceeded : False` y el 443 `True`.

## Cuidado con `application-default.properties`

Ese fichero **sí** está versionado y Spring lo carga cuando no hay perfil
activo, que es el caso de producción. Lo cargan también los tests, y **por
encima** de su propia configuración, porque las propiedades de perfil ganan a
las que no lo son. No poner ahí nunca datasource, credenciales ni nada que
dependa del entorno: rompería el aislamiento de la base de datos de test.

## Cabeceras y CSP (Caddy)

Desde el 14-sep-2026 Caddy proxya los dos: producción en
`api.norday.app` → 8080, y staging en `staging-api.norday.app` → 8081.
Los dos bloques llevan las mismas cabeceras y el mismo límite de cuerpo.
Ver la sección de staging al final.

`/etc/caddy/Caddyfile` tiene un bloque `header *` con HSTS
(`includeSubDomains`), `Referrer-Policy` y el borrado de `Server` y `Via`,
más `request_body max_size 1MB`, el log de acceso y el `reverse_proxy`.

La `Content-Security-Policy` va en un bloque aparte con matcher
(`@estaticos`), no en el `header *`: se aplica sólo a `/`,
`/index.html`, `/privacidad.html` y `/eliminar-cuenta.html`. En las
respuestas JSON de la API no pinta nada. Está en los tres bloques
(`api.norday.app`, `staging-api.norday.app` y, desde el 3-nov-2026,
`norday.app`), porque los tres sirven los mismos estáticos.

**Estuvo documentada desde el 12-sep-2026 pero no aplicada.** El cambio
de aquel día no llegó a guardarse: `Caddyfile.antes-csp` y
`Caddyfile.antes-staging` eran idénticos byte a byte, y
`curl -sI https://api.norday.app/` no devolvía la cabecera. Se aplicó de
verdad el 14-sep. Lección: una cabecera se verifica con `curl` contra el
servidor, nunca leyendo el fichero de configuración ni dando por hecho
que un cambio descrito se aplicó.

El valor exacto que se sirve hoy (desde el 3-nov-2026):

    default-src 'none'; script-src 'self'; style-src 'self'
    'unsafe-inline'; font-src 'self'; img-src 'self'; base-uri 'none';
    form-action 'none'

(en el Caddyfile va todo en una sola línea).

`script-src 'self'` va limpio y sin hashes: no queda JavaScript en línea
en ningún estático ni ningún atributo `onclick`. El de la landing vive en
`js/landing.js` y el de las páginas legales en `js/legal.js`.

`'unsafe-inline'` sobrevive sólo en `style-src`, por el bloque `<style>`
de cada una de las tres páginas. Ya no queda ningún atributo `style=`; el
JavaScript cambia estilos con `element.style`, que la CSP no bloquea.

Las letras se sirven desde `img/fuentes/` (`@font-face` en cada página),
sin Google Fonts. Por eso `font-src 'self'` y `style-src` ya no incluye
`fonts.googleapis.com`. **Si alguna página vuelve a cargar una fuente de
otro origen, la tipografía se rompe en silencio**, que es el riesgo real
de `default-src 'none'`. Las directivas no se deducen de la prosa: se
derivan de lo que las páginas cargan de verdad, mirando los `src=`,
`href=` y `url(` de cada fichero.

`img-src 'self'` cubre el favicon, los iconos de `/img/`, Nori
(`img/nori/*.webp`), la insignia de Google Play y el QR. No hace falta
`connect-src`: ni `js/landing.js` ni `js/legal.js` hacen `fetch` ni
`XMLHttpRequest`.

Comprobación después de cualquier cambio:

    curl -sI https://api.norday.app/privacidad.html | grep -i content-security-policy
    curl -sI https://api.norday.app/api/habitos | grep -i content-security-policy

La primera debe devolver la cabecera; la segunda, nada. Y abrir las
páginas en el navegador con la consola abierta: una directiva que falte
se ve ahí y en ningún otro sitio.

`default-src 'none'` implica que **cualquier cosa nueva que se añada a la
landing —un iframe, un vídeo, una fuente de otro origen— se bloqueará en
silencio** hasta que se actualice esta cabecera.

`X-Content-Type-Options` y `X-Frame-Options` las pone Spring Security, no
Caddy: no duplicarlas. Por eso la CSP no lleva `frame-ancestors`.

Al validar: `caddy validate` ejecutado como root puede crear el fichero de
log como root, y entonces el servicio, que corre como `caddy`, no puede
abrirlo y el `reload` falla. Comprobar el propietario del log antes de
recargar.

## La web en `norday.app` (3-nov-2026)

La landing y las dos páginas legales viven en
`norday-server/src/main/resources/static/` y las sirve el backend de
producción, empaquetadas en el jar. No hay otra copia: se despliegan con
el backend.

`norday.app` es la dirección pública de la web. Su bloque de Caddy lo
resuelve todo dentro de un `route`, en este orden:

1. Los enlaces cortos (`/ig`, `/threads`, `/web`, `/qr`, `/descargar`…):
   un `redir` 302 a la ficha de Google Play con sus UTM. `/web` es la
   insignia de la landing y `/qr`, su QR.
2. `/linkedin`: una página propia, servida desde `/var/www/norday-enlaces`.
3. La web: `reverse_proxy` al backend de producción **sólo** para `/`,
   `/index.html`, `/privacidad.html`, `/eliminar-cuenta.html`,
   `/favicon.ico`, `/img/*` y `/js/*`. La API no sale por `norday.app`.
4. Todo lo demás: `redir *` a Google Play.

`api.norday.app/` y `/index.html` redirigen con un 301 a
`https://norday.app/`. `api.norday.app/privacidad.html` y
`/eliminar-cuenta.html` se siguen sirviendo ahí, porque son las
direcciones dadas de alta en Google Play Console: no se cambian sin
cambiarlas antes en la consola. `og:url` y `og:image` de la landing
apuntan a `norday.app`.

**La web pasa al backend sin la cabecera `Origin`.** El navegador pide
siempre las letras de `@font-face` en modo CORS, con `Origin`, aunque
sean del mismo origen. Spring tiene su propia lista de orígenes CORS
(`SecurityConfig`: `http://localhost:8080` y `https://api.norday.app`) y
responde **403** a cualquier otro, sin mirar si la petición es del mismo
sitio. Por eso, en los tres bloques (`norday.app`, `api.norday.app` y
`staging-api.norday.app`), las rutas de la web van a un `handle @web`
con `reverse_proxy … { header_up -Origin }`, y el resto (la API) va por
otro `handle` sin tocar. En `api.norday.app`, además, un `handle @raiz`
hace la redirección de `/` y `/index.html`.

Si alguien quita el `header_up -Origin`, la web se ve con las letras
del sistema y la consola del navegador enseña los 403. La CSP no tiene
nada que ver. Tampoco se arregla añadiendo orígenes al CORS de Spring:
eso abriría la API, con credenciales, a peticiones desde esas webs.
Se descubrió el 3-nov-2026 al desplegar la web.

Comprobación después de cualquier cambio:

    curl -sI https://norday.app/ | grep -i -E "^HTTP|content-security-policy"
    curl -s -o /dev/null -w "%{http_code}\n" -H "Origin: https://norday.app" https://norday.app/img/fuentes/Manrope-Regular.woff2
    curl -sI https://norday.app/api/habitos | grep -i -E "^HTTP|^location"
    curl -sI https://api.norday.app/ | grep -i -E "^HTTP|^location"
    curl -sI https://api.norday.app/privacidad.html | grep -i "^HTTP"

La primera, 200 con la CSP; la de la letra, 200 (un 403 es que falta el
`header_up -Origin`); la de `/api/habitos`, 302 a Google Play (la API no
sale por `norday.app`); la de `api.norday.app/`, 301 a
`https://norday.app/`; y la de `privacidad.html`, 200.

## Staging en `staging-api.norday.app` (14-sep-2026)

### Qué hay montado

`norday-backend-staging.service` corre en el VPS y escucha en el 8081, junto
a `norday-backend.service` en el 8080. Los dos escuchan en loopback
(`127.0.0.1:8080`, `127.0.0.1:8081`), no en todas las interfaces.

El Caddyfile tiene dos bloques independientes, uno por dominio. **No heredan
nada el uno del otro**: cada bloque repite sus cabeceras, su
`request_body max_size 1MB` y su `log`. El de staging escribe en
`/var/log/caddy/staging-access.log`, aparte del de producción, para que el
tráfico de pruebas no se mezcle con el real.

### El DNS está en Cloudflare y va sin proxy

La zona `norday.app` la sirven `lucy.ns.cloudflare.com` y
`vicente.ns.cloudflare.com`. Los dos registros A —`api` y `staging-api`—
apuntan a la IP del VPS en modo **DNS only** (nube gris, `cf-proxied:false`
en la exportación de zona).

**Tiene que ser gris.** Con el proxy activado, Cloudflare termina el TLS por
su cuenta, Caddy no puede validar el dominio y no emite certificado. Si
`Resolve-DnsName` devuelve dos IPs que empiezan por `104.`, `172.` o `188.`,
el registro quedó proxificado: hay que apagar la nube antes de tocar Caddy.

Crear el registro antes que el bloque de Caddy, nunca al revés: si Caddy pide
certificado para un nombre que no resuelve, falla contra Let's Encrypt y esos
intentos tienen límite por dominio.

### Staging es público, y se asume

A los siete segundos de emitirse el certificado, un rastreador
(`ForestEngine`, 167.172.43.159) ya había pedido `/favicon.ico` al
subdominio. No es una filtración: cada certificado que emite Let's Encrypt se
publica en los registros de Certificate Transparency, y hay bots que los leen
en tiempo real.

Se ha decidido dejarlo público en vez de restringir por IP (la IP doméstica
cambia y el móvil por datos no entraría) o poner autenticación básica de
Caddy (la app móvil tendría que mandarla, y eso toca el cliente).

**La decisión se apoya en una condición: staging no contiene datos reales de
usuarios.** Si algún día se le vuelca un dump de producción para reproducir
un fallo, esta decisión deja de ser válida y hay que restringir el acceso
antes de hacerlo.

### El filtrado de puertos: dos barreras independientes

En el VPS no hay cortafuegos propio: `ufw` está inactivo, `iptables -S`
devuelve las tres políticas en `ACCEPT` sin una sola regla, y `nft` no tiene
ruleset. El cortafuegos del panel de Contabo cierra el 8080 y el 8081 desde
internet.

Desde el 14-sep-2026 ése ya no es el único filtro: los dos backends escuchan
sólo en loopback (ver «Los backends escuchan sólo en loopback»). Aunque
alguien abriera esos puertos en el panel de Contabo, no habría nada
escuchando en la interfaz pública. Antes de ese cambio, el panel era un punto
único: una regla mal tocada dejaba las dos APIs accesibles en HTTP en claro,
saltándose Caddy y con él el HSTS, las cabeceras y el límite de 1 MB.

Se verifica **desde fuera del VPS**, nunca desde dentro (desde dentro siempre
conecta):

    Test-NetConnection -ComputerName api.norday.app -Port 8080
    Test-NetConnection -ComputerName api.norday.app -Port 8081

Lo que importa es la línea `TcpTestSucceeded`, que debe ser `False` en ambos.
Repetir esta comprobación después de cualquier cambio en el panel de Contabo.

### Cómo se comprobó que staging responde de verdad

Un 401 en `/api/habitos` no distingue staging de producción: los dos
devuelven lo mismo sin token. Lo que lo demuestra es que la petición aparezca
en `/var/log/caddy/staging-access.log`, que sólo escribe el bloque nuevo.

    curl -s -o /dev/null -w "%{http_code}\n" https://api.norday.app/
    curl -s -o /dev/null -w "%{http_code}\n" https://staging-api.norday.app/
    curl -s -o /dev/null -w "%{http_code}\n" https://staging-api.norday.app/api/habitos
    tail -3 /var/log/caddy/staging-access.log

Esperado: 200, 200, 401 (403 antes del 25-sep-2026), y las tres peticiones
en el log de staging.
**Producción se comprueba primero**, antes de mirar el subdominio nuevo.

### Respaldo

Las copias `Caddyfile.antes-*` se borraron el 25-sep-2026, con staging
sirviendo sin incidencias desde el 14-sep.
