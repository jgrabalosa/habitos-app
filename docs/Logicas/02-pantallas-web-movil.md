# HábitosApp — Pantallas: Web y Móvil

*Última actualización: julio 2026*

> ⚠️ **Este documento es un primer borrador.** Se ha construido a partir de las conversaciones de desarrollo, no de una revisión línea a línea del código de cada pantalla. Revísalo y corrige donde haga falta — especialmente la sección Web, donde el código HTML no se ha visto completo en esta sesión.

---

## WEB

### `login.html`
Pantalla de inicio de sesión y registro (probablemente con pestañas o alternancia entre ambos formularios). Incluye login con email/contraseña, login con Google, y formulario de registro (nombre, username, email, contraseña). Tiene toggle de mostrar/ocultar contraseña.

### `index.html` — Dashboard principal
Pantalla principal tras iniciar sesión. **Estado actual:** muestra estadísticas globales (Total, Completados, Pendientes) que mezclan datos individuales con grupales — **pendiente de rediseño en Fase 11** (se eliminarán). Lista los hábitos del usuario con opción de completar, editar y navegar al detalle.

**Cambios ya decididos para Fase 11 (aún no implementados):**
- Quitar las estadísticas globales
- Mostrar solo hábitos de HOY, separados en pendientes (arriba) y completados (abajo, se mueven al completarse)
- Grid de una sola columna, más fino, ancho completo
- Mini-heatmap tipo GitHub por cada hábito
- Sustituir nombre/categoría/descripción por badge de frecuencia + contador de progreso del periodo (ej. "Semanal 1/3", nunca en negativo aunque se supere la meta)

### `habito.html`
Formulario de creación/edición de hábito: nombre, descripción, frecuencia, meta, categoría.

### `habito-detalle.html`
Vista de detalle de un hábito individual. Incluye:
- Stat cards: racha actual, mejor racha, total completados, completados este mes
- Heatmap mensual navegable (mes anterior/siguiente)
- Últimos 10 registros con opción de editar la nota
- **Se mantiene tal cual en el rediseño de Fase 11**, solo se añadirá un pequeño espacio de footer tras "últimos registros"

### Pendiente de crear (Fase 9)
- Pantalla de logros y saldo ("Mis Logros" / el museo del usuario) — saldo de puntos, lista de logros conseguidos/pendientes, inventario de consumibles con opción de uso
- Pantalla de tienda — catálogo de productos con precio y botón de compra

---

## MÓVIL (Flutter)

### `SplashScreen` (en `main.dart`)
Pantalla de arranque. Comprueba si existe un token guardado en `SharedPreferences`; si lo hay, navega directo al `DashboardScreen`, si no, al `LoginScreen`.

### `LoginScreen`
Login y registro combinados con pestañas ("Iniciar sesión" / "Registrarse"). Incluye botón "Continuar con Google". Tras un login exitoso (normal o Google), se piden permisos de notificaciones y se registra el token FCM automáticamente.

### `DashboardScreen`
Pantalla principal. Muestra estadísticas (Total, Completados, Pendientes — mismo patrón que la web, pendiente de rediseño futuro) y la lista de hábitos activos del usuario, con botón "Completar" por hábito. Al completar un hábito, si se otorga el logro `RACHA_3`, se lanza automáticamente el diálogo de valoración de Google Play (`in_app_review`).

### `HabitoScreen`
Formulario de creación/edición de hábito (mismo propósito que `habito.html` en web).

### `HabitoDetalleScreen`
Equivalente móvil de `habito-detalle.html`: stat cards (racha actual, mejor racha, total, este mes), calendario/heatmap navegable, últimos registros.

### Pendiente de crear (Fase 9)
- Pantalla de logros y saldo
- Pantalla de tienda

---

## Notas técnicas relevantes para ambas plataformas

- El emulador Flutter siempre apunta a producción (Railway), nunca a `localhost` — los cambios de backend deben desplegarse antes de poder probarse desde el emulador o dispositivo
- Web y Móvil comparten exactamente la misma API REST del backend, sin diferencias de lógica entre plataformas
