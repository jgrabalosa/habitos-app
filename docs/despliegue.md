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

Desde el 14-sep-2026 Caddy proxya los dos: producción en
`api.norday.app` → 8080, y staging en `staging-api.norday.app` → 8081.
Los dos bloques llevan las mismas cabeceras y el mismo límite de cuerpo.
Ver la sección de staging al final.

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

## Staging en `staging-api.norday.app` (14-sep-2026)

### Qué hay montado

`norday-backend-staging.service` corre en el VPS y escucha en el 8081, junto
a `norday-backend.service` en el 8080. Los dos escuchan en todas las
interfaces (`*:8080`, `*:8081`), no en `localhost`.

El Caddyfile tiene dos bloques independientes, uno por dominio. **No heredan
nada el uno del otro**: cada bloque repite sus cabeceras, su
`request_body max_size 1MB` y su `log`. El de staging escribe en
`/var/log/caddy/staging-access.log`, aparte del de producción, para que el
tráfico de pruebas no se mezcle con el real.

### El DNS está en Cloudflare y va sin proxy

La zona `norday.app` la sirven `lucy.ns.cloudflare.com` y
`vicente.ns.cloudflare.com`. Los dos registros A —`api` y `staging-api`—
apuntan a `169.58.80.239` en modo **DNS only** (nube gris, `cf-proxied:false`
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

### El filtrado de puertos vive fuera de la máquina

En el VPS no hay cortafuegos: `ufw` está inactivo, `iptables -S` devuelve las
tres políticas en `ACCEPT` sin una sola regla, y `nft` no tiene ruleset. Lo
que impide llegar al 8080 y al 8081 desde internet es el cortafuegos del
panel de Contabo.

Funciona —comprobado desde fuera, los dos puertos dan
`TcpTestSucceeded : False` y el ping tampoco responde— pero es un punto
único: si alguien cambia una regla en ese panel, las dos APIs quedan
accesibles en HTTP en claro, saltándose Caddy y con ellas el HSTS, las
cabeceras y el límite de 1 MB. Nada en el servidor lo impediría ni lo
avisaría.

Se verifica **desde fuera del VPS**, nunca desde dentro (desde dentro siempre
conecta):

    Test-NetConnection -ComputerName api.norday.app -Port 8080
    Test-NetConnection -ComputerName api.norday.app -Port 8081

Lo que importa es la línea `TcpTestSucceeded`, que debe ser `False` en ambos.
Repetir esta comprobación después de cualquier cambio en el panel de Contabo.

### Cómo se comprobó que staging responde de verdad

Un 403 en `/api/habitos` no distingue staging de producción: los dos
devuelven lo mismo sin token. Lo que lo demuestra es que la petición aparezca
en `/var/log/caddy/staging-access.log`, que sólo escribe el bloque nuevo.

    curl -s -o /dev/null -w "%{http_code}\n" https://api.norday.app/
    curl -s -o /dev/null -w "%{http_code}\n" https://staging-api.norday.app/
    curl -s -o /dev/null -w "%{http_code}\n" https://staging-api.norday.app/api/habitos
    tail -3 /var/log/caddy/staging-access.log

Esperado: 200, 200, 403, y las tres peticiones en el log de staging.
**Producción se comprueba primero**, antes de mirar el subdominio nuevo.

### Respaldo

`/etc/caddy/Caddyfile.antes-staging`, del 14-sep. Borrarlo tras unos días de
staging sirviendo sin incidencias, igual que los dos anteriores.
