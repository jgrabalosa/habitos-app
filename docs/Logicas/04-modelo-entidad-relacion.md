# Norday Habits — Modelo Entidad-Relación

*Última actualización: octubre de 2026*

> Este documento describe el modelo funcional y las principales relaciones de datos actualmente implementadas en el backend. No debe interpretarse como especificación de funcionalidades futuras.

---

## 1. Arquitectura de datos

El backend de Norday está organizado en módulos:

- `norday-motor`: cuentas, autenticación, preferencias, gamificación, versión mínima por aplicación y funcionalidades compartidas del ecosistema.
- `habitos`: hábitos, registros, ritmo, pausas, racha heredada, logros específicos de hábitos y notificaciones relacionadas.
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
- ritmo (una fila por hábito; ver Ritmo);
- racha heredada;
- logros relacionados con hábitos.

El hábito contiene la `meta` que determina cuándo se considera cumplido el periodo correspondiente.

Su campo `activo` dice si el hábito está en pausa: un hábito con `activo = false` está en pausa (ver Pausa).

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

Un hábito en pausa no se puede completar: `completarHabito` responde con un conflicto («El hábito está en pausa»).

### ReversionRegistro y ReversionLogro

Permiten deshacer un completado con exactitud. Cada completado guarda una `ReversionRegistro` ligada a su `Registro` con:

- las monedas que otorgó ese completado (el propio completado, el hito de días hechos y los logros) y la experiencia que dio a la mascota;
- el estado previo de la racha: `rachaActual`, `rachaMaxima`, periodo de meta alcanzada y última fecha;
- el día completo previo de la mascota.

Las monedas y la experiencia se guardan como lo que dio el completado, no como una instantánea: al deshacer se restan, para no borrar lo ganado después. Los campos de estado previo admiten nulo cuando no hay nada que restaurar (un hábito sin racha, un usuario sin mascota).

Cada logro desbloqueado por ese completado, incluidos los que dispara indirectamente la mascota, queda en una `ReversionLogro` que cuelga de la reversión, para poder retirarlo si el completado se deshace.

### Racha (heredada)

La tabla `racha` sigue existiendo, pero ya no se escribe: desde el 21-oct-2026 `RegistroService` no la actualiza al completar un hábito y `HabitoService.actualizar` no la reinicia al cambiar la frecuencia. Los valores guardados quedan congelados.

Es una racha heredada:

- ya no da puntos ni cuenta para los logros de constancia: el hito de racha ya no se genera (el bloque que lo creaba está comentado), y los hitos y logros van por días hechos del hábito;
- el backend sigue enviándola en el detalle del hábito (`rachaActual` y `rachaMaxima` en `HabitoDetalleDTO`), porque la app ya instalada la lee; como ya no se actualiza, `rachaActual` acaba en cero por la rotura perezosa y `rachaMaxima` no cambia; la app nueva lee el ritmo;
- su rotura sigue siendo perezosa: `Racha.sigueViva` y `RachaService.rachaActualVigente` ponen `rachaActual` a cero cuando se lee una racha rota;
- el aviso de racha en peligro (`NotificadorRachaEnPeligro`) existe, pero su programación está comentada: hoy no se envía.

Al deshacer un completado, la racha vuelve a su estado previo a partir de la `ReversionRegistro`; como la racha ya no cambia, restaura los mismos valores.

### Ritmo

Es la medida que sustituye a la racha de cara al usuario: un valor de 0 a 1 por hábito, y su nivel de 1 a 5.

Se guarda como un ancla, con una fila por hábito (`habito_ref` es único):

- `ritmo_id`: clave primaria.
- `habito_ref`: obligatorio y único; clave ajena `FK_ritmo_habito` a `habito`.
- `valor`: obligatorio. El ritmo, de 0 a 1, al empezar el día `desde`.
- `desde`: obligatorio. El día del ancla.
- `valor_previo`: admite nulo. El ritmo del ancla anterior, para calcular el de hace siete días. Es nulo mientras el ancla no haya avanzado nunca, y al reanudar una pausa vuelve a serlo.
- `desde_previo`: admite nulo. El día de esa ancla anterior.
- `cambio`: admite nulo. El día del último cambio de frecuencia u objetivo, y también el de la última pausa o reanudación.

El ritmo de hoy no se guarda. Se calcula al leer, repasando los registros desde el inicio del periodo de `desde`. Por eso completar o deshacer un hábito no escribe en esta tabla. El ancla solo se mueve en tres casos: al empezar una semana nueva, avanzando como mucho hasta el lunes de la semana en curso; al cambiar la frecuencia o el objetivo; y al pausar o reanudar el hábito.

El nivel no se guarda: se deriva del valor (ver los umbrales abajo).

Parámetros del ritmo. Son provisionales, como dice el propio código: se ajustan con uso real.

- Valor inicial: 0,5.
- Semivida al subir: 7 días. Semivida al bajar: 21 días.
- Umbrales de nivel: el nivel 2 empieza en 0,40; el 3 en 0,60; el 4 en 0,80; el 5 en 0,95.
- Días de entrada en el ritmo general: 14. Un hábito nuevo va ganando peso en el ritmo general hasta contar como los demás.
- Tendencia: sube si el ritmo ha cambiado en 0,01 o más en siete días; baja si ha cambiado en −0,045 o menos.
- Hábitos necesarios para el logro «Todos a una»: 3.

El ritmo general es la media ponderada de los ritmos de los hábitos activos.

### Pausa

No tiene tabla propia. Un hábito en pausa es un hábito con `activo = false`.

Pausar gasta una unidad del producto «Pausa» del inventario (código `PAUSA_HABITO`), ancla el ritmo en hoy con el valor que tiene y deja el hábito inactivo. Si no quedan unidades, responde con un conflicto («No te quedan unidades de PAUSA_HABITO») y no cambia nada; si el hábito ya estaba en pausa, también responde con un conflicto («El hábito ya está en pausa»).

Mientras está en pausa:

- sale de Hoy, que solo lee los hábitos activos;
- sale del ritmo general, que solo cuenta los activos;
- sale de los recordatorios, que solo se envían para los hábitos activos;
- su ritmo se queda congelado donde se pausó, y no se puede completar.

Reanudar es gratis. Si el hábito ya estaba activo, no hace nada. Si estaba en pausa, el ancla pasa al día de la vuelta con el valor que tenía al pausar, el día de cambio pasa a ser ese mismo día, y se borran `valor_previo` y `desde_previo`, para que la tendencia no compare con lo de antes de la pausa. Los días de pausa no se juzgan.

### Versión mínima (VersionApp)

Tabla `version_app`, del módulo `norday-motor` (migración V20). Tiene dos columnas:

- `app_id`: clave primaria, texto de hasta 30 caracteres.
- `version_minima`: obligatoria. El `versionCode` de Android que la app necesita; por debajo, la app se bloquea y pide actualizar.

Una app sin fila tiene mínima 0, así que nunca se bloquea por un dato que falta. La migración deja una fila para `habitos` con mínima 1, que no bloquea a nadie.

Se cambia a mano con un `UPDATE`, sin compilar ni reiniciar. El orden es siempre el mismo: primero se publica la versión nueva y se ve en Play; después se sube la mínima. La consulta es `GET /api/version/{appId}`.

### Logro

Es el catálogo de logros: código, nombre, descripción, categoría, nivel, puntos, icono y `origenApp`. Un logro del catálogo no pertenece a ningún usuario.

Existen dos grandes grupos:

1. logros genéricos del motor/ecosistema;
2. logros específicos del dominio de hábitos.

Los logros específicos de hábitos utilizan `origenApp = "habitos"`.

Los logros de constancia tienen códigos `RACHA_*`. Esos códigos no cambian, pero ya no miden rachas: `RACHA_3`, `RACHA_7`, `RACHA_10`… hasta `RACHA_365` cuentan las veces que se completa un mismo hábito. Los umbrales son 3, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 100 y 365. `RACHA_RECUPERADA` se concede al volver a un hábito después de unos días sin hacerlo.

Los logros de ritmo (`RITMO_BUENO`, `RITMO_FUERTE`, `RITMO_IMPARABLE`, `RITMO_GENERAL_FUERTE`, `RITMO_TODOS` y `RITMO_VUELTA`) se conceden al leer la pantalla de Hoy, porque el ritmo se mueve al cerrar el día o la semana y no al marcar.

El servicio de dominio determina qué logro corresponde y el servicio genérico de gamificación gestiona su concesión.

### UsuarioLogro

Representa la concesión de un logro a un usuario: qué usuario, qué logro y cuándo lo consiguió. La pareja usuario-logro es única (`UQ_usuario_logro`): un usuario no puede tener el mismo logro dos veces.

### Moneda / movimientos

La economía de puntos se registra mediante movimientos asociados al usuario (`UsuarioMoneda`).

El saldo se obtiene a partir del ledger de movimientos, evitando mantener un saldo independiente como única fuente de verdad.

Los movimientos pueden corresponder, entre otros, a:

- completados de hábitos;
- hitos de días hechos (`HITO_DIAS_HECHOS`): una vez por hábito, al llegar a 3, 7, 30, 100 y 365 días hechos en total, seguidos o no;
- operaciones de gamificación.

El movimiento `HITO_RACHA` ya no se genera: el código que lo creaba está comentado.

### Producto

Representa elementos disponibles en el catálogo de gamificación.

Los productos pueden estar relacionados con:

- temas/identidades;
- comida para la mascota;
- la pausa de hábito (`PAUSA_HABITO`, ver Pausa);
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
 │           ├── ────── Racha (heredada)
 │           └── 1:1 ── Ritmo
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
- días hechos de un mismo hábito (los logros `RACHA_*`);
- ritmo (los logros `RITMO_*`);
- volumen de actividad;
- primera nota.

Los logros de hábitos se identifican mediante su origen de aplicación.

---

## 5. Eliminaciones de cuenta y portabilidad

La eliminación de una cuenta sigue el patrón de limpieza por responsabilidades utilizado en el backend. Cada módulo aporta su propio limpiador.

El limpiador de hábitos (`LimpiadorHabitos`) recorre los hábitos del usuario y borra, en este orden, las reversiones, los registros, la racha y el ritmo de cada uno. Después borra los hábitos y, por último, las categorías personalizadas del usuario. El ritmo se borra antes que su hábito porque la clave ajena `FK_ritmo_habito` no tiene borrado en cascada. Eliminar un hábito suelto (`HabitoService.eliminar`) sigue el mismo orden.

La exportación de datos se organiza mediante los exportadores correspondientes a los dominios del sistema, con el objetivo de mantener separadas las responsabilidades y facilitar la portabilidad de los datos. El exportador de hábitos (`ExportadorHabitos`) incluye, por cada hábito, su racha (si existe), su ritmo (`valor`, `desde`, `valorPrevio`, `desdePrevio` y `cambio`) y sus registros, además de las categorías personalizadas.

---

## 6. Zona horaria e idioma

La zona horaria del usuario forma parte de la lógica funcional del sistema y se utiliza para interpretar fechas y periodos.

El idioma y otras preferencias se sincronizan con el backend.

Esto es especialmente relevante para:

- registros;
- rachas y ritmo;
- notificaciones;
- fechas mostradas al usuario.

---

## 7. Reglas de integridad relevantes

- Un hábito pertenece a un usuario.
- Un registro pertenece a un hábito.
- La racha heredada ya no se escribe (apagada el 21-oct-2026); deshacer un completado restaura su estado previo, que no ha cambiado.
- El ritmo de un hábito es una sola fila (`habito_ref` es único). Completar o deshacer no lo escribe: se calcula al leer desde su ancla.
- Un hábito en pausa no se puede completar, no se pone al día y no entra en Hoy ni en el ritmo general.
- Los efectos de gamificación se registran mediante movimientos y concesiones de logros.
- Los productos y su equipamiento son persistentes y tienen como fuente de verdad el backend.
- Las operaciones de gamificación deben respetar las relaciones y restricciones del usuario propietario.
- Las operaciones de eliminación y exportación deben mantener separadas las responsabilidades de cada dominio.

---

## 8. Notas sobre documentación

Este documento sustituye la descripción antigua del modelo que contenía elementos ya retirados o marcados como pendientes, como determinados valores de frecuencia y estructuras de gamificación que ya no representan el estado actual.

No se incluyen aquí funcionalidades futuras que no estén demostradas en el código actual.
