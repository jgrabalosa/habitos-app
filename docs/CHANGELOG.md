# CHANGELOG — App de Hábitos

## Fase 1.5 — Revisión de entidades (27/06/2026)

### Mejoras aplicadas

#### FK con nombres propios
Se han nombrado manualmente todas las claves foráneas para evitar los nombres hash generados automáticamente por Hibernate. Mejora la legibilidad de la base de datos y facilita las consultas y el mantenimiento.

| Tabla | Columna | Nombre FK |
|-------|---------|-----------|
| categoria | creador_id | FK_categoria_usuario |
| habito | propietario_id | FK_habito_usuario |
| habito | tipo_id | FK_habito_categoria |
| registro | habito_ref | FK_registro_habito |
| racha | habito_ref | FK_racha_habito |

#### @Column(name="...") explícito en todos los campos
Se ha añadido el atributo `name` en todas las anotaciones `@Column` de todas las entidades para tener control total sobre los nombres de columna en la base de datos.

#### Campos nuevos

**Usuario:**
- `username` (String, unique, length=50) — nombre de usuario único, más amigable que el email para identificar al usuario

**Categoria:**
- `descripcion` (String, length=200) — descripción opcional de la categoría
- `orden` (int) — permite ordenar las categorías globales de forma controlada

**Habito:**
- `meta` (int) — número de veces por semana/mes que el usuario quiere cumplir el hábito

#### Constructores actualizados
Se han actualizado los constructores con parámetros para incluir los nuevos campos.

---

## Fase 1 — Capa de entidades (27/06/2026)

### Entidades creadas
- `Usuario` — gestión de usuarios con email único
- `Categoria` — categorías globales y personalizadas por usuario
- `Habito` — hábitos con frecuencia, propietario y categoría
- `Registro` — registro diario de hábitos completados
- `Racha` — racha actual y máxima por hábito
- `Frecuencia` (enum) — DIARIO, SEMANAL, MENSUAL, PERSONALIZADO

### Decisiones de diseño
- **Code First con JPA/Hibernate** — las tablas se generan automáticamente desde las clases Java
- **GenerationType.IDENTITY** — PostgreSQL gestiona los IDs automáticamente
- **ddl-auto=update** — Hibernate actualiza el esquema sin borrar datos
- **EnumType.STRING** — la frecuencia se guarda como texto en la BD para mayor legibilidad
- **@OneToOne en Racha** — un hábito tiene exactamente una racha, garantizado con UNIQUE constraint

---

## Fase 0 — Preparación (27/06/2026)

### Herramientas instaladas
- Java 21 JDK (Eclipse Temurin)
- IntelliJ IDEA Ultimate (licencia estudiante hasta feb. 2027)
- PostgreSQL 16 + pgAdmin 4
- Git + GitHub
- Postman

### Configuración inicial
- Proyecto Spring Boot 3.3.5 con Maven
- Base de datos `habitos_db` en PostgreSQL local (localhost:5432)
- `.gitignore` excluyendo `application.properties` con credenciales
- `application.properties.example` para facilitar la configuración en otros equipos
- Repositorio GitHub: github.com/jgrabalosa/habitos-app
