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

Staging primero, siempre. Y `git pull` **antes** que `mvn`, sin excepción: el
commit que aísla los tests puede ser justo el que todavía no está en la máquina.

```bash
cd /opt/norday-backend-staging
git pull
mvn clean package
systemctl restart norday-backend-staging
```

Y después producción, con backup por delante:

```bash
/root/backup_bd.sh
cd /opt/norday-backend
git pull
mvn clean package
systemctl restart norday-backend
```

## `mvn clean package` va SIN `-DskipTests`

No es un descuido: es la regla. Cinco tests estuvieron rotos tres sesiones sin
que nadie se enterara porque el despliegue se los saltaba. En el servidor los
tests usan mocks y no necesitan nada especial.

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
- Un token mal firmado devuelve **403**, no 401.

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

## Cuidado con `application-default.properties`

Ese fichero **sí** está versionado y Spring lo carga cuando no hay perfil
activo, que es el caso de producción. Lo cargan también los tests, y **por
encima** de su propia configuración, porque las propiedades de perfil ganan a
las que no lo son. No poner ahí nunca datasource, credenciales ni nada que
dependa del entorno: rompería el aislamiento de la base de datos de test.

## Cabeceras y CSP (Caddy)

Caddy proxya sólo producción; staging escucha en el 8081 y no pasa por él,
así que **las cabeceras no se pueden probar en staging**.

`/etc/caddy/Caddyfile` tiene un bloque `header *` con HSTS
(`includeSubDomains`), `Referrer-Policy` y el borrado de `Server` y `Via`,
más `request_body max_size 1MB`, el log de acceso y el `reverse_proxy`.

La `Content-Security-Policy` va en un bloque aparte con matcher, no en el
`header *`: se aplica sólo a `/`, `/index.html`, `/privacidad.html` y
`/eliminar-cuenta.html`. En las respuestas JSON de la API no pinta nada.

`script-src 'self'` va limpio y sin hashes porque desde el 12-sep-2026 no
queda JavaScript en línea en ningún estático: el de la landing vive en
`js/nori.js`. `'unsafe-inline'` sobrevive sólo en `style-src`, por los
atributos `style=` de la landing, que no se pueden hashear.

`default-src 'none'` implica que **cualquier cosa nueva que se añada a la
landing —un iframe, un vídeo, una fuente de otro origen— se bloqueará en
silencio** hasta que se actualice esta cabecera.

`X-Content-Type-Options` y `X-Frame-Options` las pone Spring Security, no
Caddy: no duplicarlas. Por eso la CSP no lleva `frame-ancestors`.

Al validar: `caddy validate` ejecutado como root puede crear el fichero de
log como root, y entonces el servicio, que corre como `caddy`, no puede
abrirlo y el `reload` falla. Comprobar el propietario del log antes de
recargar.
