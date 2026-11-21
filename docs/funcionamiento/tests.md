# Tests: base de datos de test, aislamiento y concurrencia

Movido desde `CLAUDE.md` (T-005a). El `CLAUDE.md` conserva el resumen:
`./mvnw clean test`, la BD `habitos_db_test` y el aviso de `create-drop`.

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
- **El pool de conexiones de test es pequeño y se encoge** (5 como mucho, 1
  ociosa, 10 s). Spring guarda un contexto por cada combinación de
  `@MockitoSpyBean`/`@TestPropertySource`, y cada uno conserva su pool: con el
  de por defecto (10 fijas) la suite agotaba las 100 conexiones de Postgres
  («remaining connection slots are reserved»).
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

Los tests de concurrencia (`*ConcurrenteTest`, `ConcesionesEnUnCompletadoTest`,
`EscriturasConcurrentesTest`) fuerzan la intercalación con dos hilos y
transacciones confirmadas de verdad, sin `@Transactional`. El hilo en pausa
espera con `EsperaDeBloqueo` a que el otro termine o se quede esperando un
bloqueo de su transacción (`pg_blocking_pids`), nunca un tiempo fijo, y los
hilos se cierran en `@AfterEach`.

Qué cubre la suite y qué no: los tests unitarios de `norday-motor`,
`habitos`, `conocimiento` y `enfoque` mockean los DAOs, así que no ven
proxies de Hibernate ni validan JPQL. Los de `norday-server` (51 clases con
`@SpringBootTest` a 20-nov-2026: los de concurrencia y los de integración) sí
tocan Postgres de verdad, en `habitos_db_test`, y validan las consultas por las que
pasan. Una consulta que ninguno de ellos recorre no está validada porque la
suite esté verde.
