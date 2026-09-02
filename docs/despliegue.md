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
| Perfil Spring | ninguno activo | `staging` |

Los dos son clones de este repositorio que se compilan en el propio servidor.
El `application.properties` de producción está en `.gitignore` y sólo existe en
la máquina; `application-staging.properties` tampoco está versionado.

Existe además una tercera base de datos, `habitos_db_test`, contra la que corren
los `@SpringBootTest`. La apunta
`norday-server/src/test/resources/application.properties`, que **sí** está
versionado. De él depende que compilar no escriba en producción.

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

## Backups

`/root/backup_bd.sh` guarda en `/root/backups/` **y copia a Drive con rclone**,
así que sí hay copia fuera de la máquina. Cron diario a las 3:00. El log queda
en `/root/backups/backup.log`.

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
