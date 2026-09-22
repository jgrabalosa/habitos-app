# Norday Habits — Modelo Entidad-Relación

*Última actualización: septiembre 2026*

> Este documento describe el modelo funcional y las principales relaciones de datos actualmente implementadas en el backend. No debe interpretarse como especificación de funcionalidades futuras.

---

## 1. Arquitectura de datos

El backend de Norday está organizado en módulos:

- `norday-motor`: cuentas, autenticación, preferencias, gamificación y funcionalidades compartidas del ecosistema.
- `habitos`: hábitos, registros, rachas, logros específicos de hábitos y notificaciones relacionadas.
- `conocimiento`: categorías, píldoras de conocimiento, preferencias y valoraciones.
- `norday-server`: aplicación Spring Boot que integra y expone los módulos.

La cuenta de usuario y la gamificación son compartidas por las aplicaciones del ecosistema.

Este documento cubre el motor y el dominio de Norday Habits. Las entidades de `conocimiento` (`Categoria`, `Pildora`, `PildoraCategoria`, `UsuarioPildora`, `UsuarioCategoriaPreferencia` y `ValoracionPildora`) pertenecen a Norday Conocimiento y se describen en su propio módulo, en `conocimiento/src/main/java/com/norday/conocimiento/model/`.

---

## 2. Entidades principales

### Usuario

Representa la cuenta común del ecosistema.

Entre sus datos y relaciones se encuentran:

- identidad de usuario y credenciales;
- preferencias;
- idioma y zona horaria;
- token FCM cuando está disponible;
- productos/equipamiento;
- moneda y movimientos de gamificación;
- logros;
- mascota;
- datos de autenticación y recuperación de contraseña.

El backend es la fuente de verdad para el estado persistente del usuario.

### CodigoRecuperacion

Código temporal para restablecer la contraseña. Va ligado al email, no al usuario, y tiene una fecha de caducidad. Es parte del motor de cuenta.

### Hábito

Representa un hábito creado por el usuario.

Está relacionado con:

- usuario propietario;
- categoría;
- registros de cumplimiento;
- información de racha;
- estadísticas derivadas;
- logros relacionados con hábitos.

El hábito contiene la `meta` que determina cuándo se considera cumplido el periodo correspondiente.

### Categoría

Agrupa hábitos.

Las categorías pueden ser del catálogo existente o creadas por el usuario. La creación y uso de categorías interviene también en los logros relacionados con variedad.

### Registro

Representa el cumplimiento de un hábito en una fecha determinada.

Un registro se relaciona con:

- un hábito;
- una fecha;
- el estado de completado;
- información necesaria para revertir los efectos de un completado cuando corresponde.

La lógica de registros es responsable de aplicar los efectos de gamificación derivados del cumplimiento.

### ReversionRegistro y ReversionLogro

Permiten deshacer un completado con exactitud. Cada completado guarda una `ReversionRegistro` ligada a su `Registro` con:

- las monedas que otorgó ese completado (el propio completado, el hito de racha y los logros) y la experiencia que dio a la mascota;
- el estado previo de la racha: `rachaActual`, `rachaMaxima`, periodo de meta alcanzada y última fecha;
- el día completo previo de la mascota.

Las monedas y la experiencia se guardan como lo que dio el completado, no como una instantánea: al deshacer se restan, para no borrar lo ganado después. Los campos de estado previo admiten nulo cuando no hay nada que restaurar (un hábito sin racha, un usuario sin mascota).

Cada logro desbloqueado por ese completado, incluidos los que dispara indirectamente la mascota, queda en una `ReversionLogro` que cuelga de la reversión, para poder retirarlo si el completado se deshace.

### Racha

Representa la continuidad de periodos cumplidos de un hábito.

Se manejan principalmente:

- `rachaActual`;
- `rachaMaxima`;
- última fecha relevante para la racha.

La racha se normaliza de forma perezosa mediante `RachaService`; no depende de un proceso que resetee todas las rachas a una hora fija.

### Logro

Es el catálogo de logros: código, nombre, descripción, categoría, nivel, puntos, icono y `origenApp`. Un logro del catálogo no pertenece a ningún usuario.

Existen dos grandes grupos:

1. logros genéricos del motor/ecosistema;
2. logros específicos del dominio de hábitos.

Los logros específicos de hábitos utilizan `origenApp = "habitos"`.

El servicio de dominio determina qué logro corresponde y el servicio genérico de gamificación gestiona su concesión.

### UsuarioLogro

Representa la concesión de un logro a un usuario: qué usuario, qué logro y cuándo lo consiguió. La pareja usuario-logro es única (`UQ_usuario_logro`): un usuario no puede tener el mismo logro dos veces.

### Moneda / movimientos

La economía de puntos se registra mediante movimientos asociados al usuario (`UsuarioMoneda`).

El saldo se obtiene a partir del ledger de movimientos, evitando mantener un saldo independiente como única fuente de verdad.

Los movimientos pueden corresponder, entre otros, a:

- completados de hábitos;
- hitos de racha;
- operaciones de gamificación.

### Producto

Representa elementos disponibles en el catálogo de gamificación.

Los productos pueden estar relacionados con:

- temas/identidades;
- comida para la mascota;
- otros elementos de equipamiento según el catálogo activo.

### UsuarioProducto

Relaciona al usuario con los productos adquiridos o disponibles para su cuenta y permite gestionar el equipamiento correspondiente.

El backend mantiene la información de productos/equipamiento como fuente de verdad.

### Mascota

La mascota forma parte de la gamificación compartida del ecosistema.

Guarda:

- la experiencia acumulada;
- el nombre;
- la fecha de la última comida;
- la fecha del último día completo;
- la fase elegida (`faseElegida`).

El nivel no se guarda: se deriva de la experiencia, y la fase (huevo, cría, adulto) se deriva del nivel. Los umbrales viven en `MascotaService`.

`faseElegida` permite al usuario elegir qué fase de la mascota ve, entre las que ya ha desbloqueado. Sólo afecta a la imagen: no toca experiencia, evolución ni logros. Vale nulo, que significa «la que toque», al nacer y tras cada evolución, para que el cambio se vea.

La mascota puede generar logros indirectamente derivados de acciones realizadas desde hábitos.

---

## 3. Relaciones principales

De forma simplificada:

```text
Usuario
 ├── 1:N ── Hábito
 │           ├── N:1 ── Categoría
 │           ├── 1:N ── Registro ── 1:N ── ReversionRegistro ── 1:N ── ReversionLogro
 │           └── ────── Racha
 │
 ├── 1:N ── UsuarioLogro ── N:1 ── Logro
 ├── 1:N ── UsuarioMoneda
 ├── 1:N ── UsuarioProducto ── N:1 ── Producto
 └── 1:1 ── Mascota
```

La relación exacta entre entidades y las tablas auxiliares se define en las clases JPA y en el esquema de base de datos desplegado.

---

## 4. Gamificación

La gamificación está separada entre el motor común y el dominio de hábitos.

### Motor común

Gestiona elementos como:

- bienvenida;
- login con Google;
- identidades;
- mascota;
- catálogo de productos;
- moneda;
- logros genéricos.

### Dominio de hábitos

Gestiona los logros derivados de:

- creación de hábitos;
- categorías;
- registros;
- rachas;
- volumen de actividad;
- primera nota.

Los logros de hábitos se identifican mediante su origen de aplicación.

---

## 5. Eliminaciones de cuenta y portabilidad

La eliminación de una cuenta sigue el patrón de limpieza por responsabilidades utilizado en el backend.

La exportación de datos se organiza mediante los exportadores correspondientes a los dominios del sistema, con el objetivo de mantener separadas las responsabilidades y facilitar la portabilidad de los datos.

---

## 6. Zona horaria e idioma

La zona horaria del usuario forma parte de la lógica funcional del sistema y se utiliza para interpretar fechas y periodos.

El idioma y otras preferencias se sincronizan con el backend.

Esto es especialmente relevante para:

- registros;
- rachas;
- notificaciones;
- fechas mostradas al usuario.

---

## 7. Reglas de integridad relevantes

- Un hábito pertenece a un usuario.
- Un registro pertenece a un hábito.
- La racha se calcula a partir del estado de cumplimiento del hábito.
- Los efectos de gamificación se registran mediante movimientos y concesiones de logros.
- Los productos y su equipamiento son persistentes y tienen como fuente de verdad el backend.
- Las operaciones de gamificación deben respetar las relaciones y restricciones del usuario propietario.
- Las operaciones de eliminación y exportación deben mantener separadas las responsabilidades de cada dominio.

---

## 8. Notas sobre documentación

Este documento sustituye la descripción antigua del modelo que contenía elementos ya retirados o marcados como pendientes, como determinados valores de frecuencia y estructuras de gamificación que ya no representan el estado actual.

No se incluyen aquí funcionalidades futuras que no estén demostradas en el código actual.
