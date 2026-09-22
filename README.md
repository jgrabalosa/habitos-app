# habitos-app — backend de Norday

Backend del ecosistema de apps **Norday**. Da servicio a **Norday Habits**, una
app Android de hábitos con puntos, logros, una mascota que crece (Nori) e
identidades visuales, y al dominio de **Norday Conocimiento**. Todas las apps
del ecosistema comparten este backend, la base de datos y la cuenta del
usuario, incluido el saldo de puntos.

También sirve la landing de Norday y las dos páginas legales (política de
privacidad y eliminación de cuenta), desde
`norday-server/src/main/resources/static/`.

## Stack

Java 21 · Spring Boot 3.4.5 · PostgreSQL · Flyway · Maven multimódulo.
Autenticación con JWT, registro con email o con Google, notificaciones con
Firebase Cloud Messaging. Textos en español, inglés y portugués.

## Módulos

| Módulo | Qué contiene |
|---|---|
| `norday-motor/` | El motor común: cuenta, auth, email, notificaciones (`com.norday.core`) y puntos, logros, tienda y mascota (`com.norday.gamificacion`) |
| `habitos/` | El dominio de Norday Habits: hábitos, registros, rachas, categorías y sus logros |
| `conocimiento/` | El dominio de Norday Conocimiento: píldoras, preferencias y valoraciones |
| `norday-server/` | El ejecutable: arranque, configuración, textos, estáticos y migraciones |

`habitos` y `conocimiento` dependen del motor y no se conocen entre sí. El
motor no conoce ningún dominio: Maven lo impone, porque no declara
dependencias hacia ellos.

## Repos relacionados

- [`habitos_app_mobile`](https://github.com/jgrabalosa/habitos_app_mobile) — la app Android de Norday Habits (Flutter)
- [`norday_flutter_core`](https://github.com/jgrabalosa/norday_flutter_core) — el paquete Flutter común a las apps de Norday

## Compilar

1. Copiar `norday-server/src/main/resources/application.properties.example`
   a `application.properties` en la misma carpeta y rellenarlo. No se
   versiona.
2. Crear la base de datos de los tests. El build **no** se salta los tests,
   así que sin ella no compila:

```bash
   psql -U postgres -c "CREATE USER norday_test WITH PASSWORD 'norday_test';"
   psql -U postgres -c "CREATE DATABASE habitos_db_test OWNER norday_test;"
```

3. `./mvnw clean package` (en Windows, `mvnw.cmd clean package`).

## Documentación

- [`CLAUDE.md`](CLAUDE.md) — arquitectura, reglas obligatorias y lecciones aprendidas
- [`docs/Logicas/03-calculos-y-logica.md`](docs/Logicas/03-calculos-y-logica.md) — rachas, puntos, experiencia, logros y catálogo de la tienda
- [`docs/Logicas/04-modelo-entidad-relacion.md`](docs/Logicas/04-modelo-entidad-relacion.md) — modelo de datos
- [`docs/Logicas/05-diagramas-uml.md`](docs/Logicas/05-diagramas-uml.md) — diagramas de clases y flujos
- [`docs/despliegue.md`](docs/despliegue.md) — producción y staging
- [`docs/backups.md`](docs/backups.md) — copias de la base de datos y restauración
- [`docs/ordenador-servidor.md`](docs/ordenador-servidor.md) — las máquinas de trabajo y el agente Hermes