# Datos del usuario: borrado de cuenta y exportación

Movido desde `CLAUDE.md` (T-005a).

## Borrado de cuenta: patrón LimpiadorDatosUsuario

`UsuarioService.eliminarCuenta()` **no** conoce las tablas de cada módulo.
Declara la interfaz `LimpiadorDatosUsuario` (en `core.service`), Spring
recolecta todas sus implementaciones en un `List<LimpiadorDatosUsuario>`,
se recorren, y solo después se borra el `Usuario`.

Implementaciones actuales, seis:

- `LimpiadorHabitos` — por cada hábito, el libro de hitos (`hito_pagado`),
  las reversiones, los registros, las rachas, los ritmos y el historial de
  objetivos (`habito_objetivo`); luego los hábitos; luego las categorías
  propias. El libro de hitos va antes que los registros porque también
  apunta a ellos.
- `LimpiadorGamificacion` — logros, monedas, lo puesto (`usuario_equipado`)
  antes que los productos (apunta a ellos), productos, mascota.
- `LimpiadorRecuperacion` — códigos de recuperación de contraseña, que
  cuelgan del email y no del id.
- `LimpiadorConocimiento` — píldoras vistas, preferencias de categoría y
  valoraciones.
- `LimpiadorDispositivos` — los tokens de notificaciones de todas las apps
  (`usuario_dispositivo`).
- `LimpiadorEnfoque` — las sesiones de Norday Focus (`sesion_enfoque`). Los
  minutos que llevaron a un hábito son registros del hábito y los borra
  `LimpiadorHabitos`.

**Cada módulo nuevo que guarde datos colgando de `Usuario` debe aportar su
propio limpiador.** Esto sustituye a la regla frágil de "acordarse de
añadir cada tabla nueva a `eliminarCuenta`": ese método ya no se toca.
El orden entre limpiadores es indiferente (son árboles de FK
independientes que solo apuntan a `Usuario`); el orden *dentro* de cada
limpiador sí importa.

## Exportación de datos: el mismo patrón

`ExportacionDatosService.exportar(id)` tampoco conoce las tablas de cada
módulo. La interfaz `ExportadorDatosUsuario` vive en `core.service` y
Spring recolecta sus implementaciones igual que con los limpiadores:
`ExportadorGamificacion`, `ExportadorHabitos`, `ExportadorConocimiento`,
`ExportadorDispositivos` (en qué apps hay token y desde cuándo; nunca el
token, como tampoco sale `usuario.fcm_token`) y `ExportadorEnfoque` (sección
`enfoque`: las sesiones, sin la clave).

Lo consume `GET /api/usuarios/{id}/exportar`, que comprueba que el
autenticado es el dueño y devuelve el JSON con `Content-Disposition`. Es la
portabilidad del RGPD (art. 20) y está prometida en la política de
privacidad.

**Un módulo nuevo que guarde datos del usuario aporta dos cosas: su
limpiador y su exportador.** Si solo aporta el limpiador, la exportación
mentirá por omisión.
