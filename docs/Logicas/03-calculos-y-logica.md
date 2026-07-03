# HábitosApp — Cálculos y Lógica de Negocio

*Última actualización: julio 2026*

> ⚠️ Muchos de los valores numéricos de este documento son **provisionales**, pensados para desarrollar y probar el flujo técnico, no para un lanzamiento real. Están marcados explícitamente donde aplica. Se reequilibrarán antes de V1.

---

## Rachas (lógica ACTUAL — pendiente de rediseño en la Fase Crítica)

> ⚠️ Esta sección describe cómo funciona el sistema **hoy**, antes del rediseño. Se sabe que tiene un fallo: solo funciona correctamente para hábitos `DIARIO`. Ver sección siguiente para el diseño nuevo, aún no implementado.

- **Racha actual (`rachaActual`):** al completar un hábito, si la última fecha completada fue exactamente "ayer", suma +1. Si no fue ni ayer ni hoy, se resetea a 1.
- **Mejor racha (`rachaMaxima`):** el valor más alto que `rachaActual` ha alcanzado. Se actualiza automáticamente cuando `rachaActual` la supera. Nunca disminuye.
- **Total completados:** `COUNT` de todos los registros del hábito con `completado = true`, sin límite de tiempo.
- **Completados este mes:** igual que el total, filtrado al mes consultado.
- **Porcentaje del mes:** solo se calcula para hábitos `DIARIO` (para `SEMANAL`/`MENSUAL` devuelve `null` actualmente). Fórmula: `(completados del mes / días transcurridos) × 100` si es el mes actual, o `(completados / días totales del mes) × 100` si es un mes pasado.

## Rachas — DISEÑO NUEVO (Fase Crítica, pendiente de implementar)

- **Regla unificada de cumplimiento de periodo:** un periodo (día o semana) se considera cumplido si `registros_completados_en_el_periodo >= meta` del hábito
- **Cierre de periodos** (huso horario del usuario): `DIARIO` cierra a las 00:00 cada día; `SEMANAL` cierra el lunes a las 00:00
- **Primer periodo:** se regala — no rompe la racha si no se cumple
- **Evaluación:** proactiva, mediante un proceso programado que revisa al cierre de cada periodo (no reactiva como hoy)
- **Frecuencias soportadas en V1:** solo `DIARIO` y `SEMANAL` (`MENSUAL` y `PERSONALIZADO` van a V2)
- **Cambiar la meta a mitad de periodo:** permitido, no rompe la racha; el periodo en curso pasa a exigir la nueva meta

---

## Sistema de Puntos (Gamificación — Fase 9)

### Fuentes de puntos

| Fuente | Puntos | Estado |
|---|---|---|
| Completar un hábito | 100 | Provisional — se espera bajar a 5-10 pts al reequilibrar |
| Hito de racha (3) | 20 | Provisional |
| Hito de racha (7) | 50 | Provisional |
| Hito de racha (30) | 200 | Provisional |
| Hito de racha (100) | 500 | Provisional |
| Hito de racha (365) | 1000 | Provisional |
| Logro nivel Fácil | 100 | Provisional |
| Logro nivel Medio | 200 | Provisional |
| Logro nivel Difícil | 500 | Provisional |

**Principio de diseño:** los puntos ganados por hitos de racha son irrevocables — si la racha se rompe después, los puntos ya ganados no se retiran.

**Problema conocido a reequilibrar:** con los valores actuales, completar 2-3 hábitos diarios ya iguala o supera el valor de un logro "Difícil" (500 pts) en 1-2 días de uso, lo que infla la economía. Se corregirá antes de V1.

### Cálculo del saldo

`saldo = SUM(cantidad)` de todos los movimientos en `UsuarioMoneda` del usuario. Se calcula al vuelo en cada consulta — nunca se guarda un valor cacheado, para que el ledger sea siempre la fuente de verdad única.

### Compra de productos

Al comprar, se valida `saldo actual >= precio del producto` antes de crear el movimiento. Si no hay saldo suficiente, la compra se rechaza sin modificar nada.

---

## Catálogo de Logros (25 logros)

Cada logro pertenece a una de 5 categorías y un nivel de dificultad (Fácil/Medio/Difícil), que determina sus puntos.

### Inicio (todos Fácil / 100 pts)
| Código | Nombre | Condición |
|---|---|---|
| `PRIMER_HABITO` | Tu primer hábito propio | Crear el primer hábito personalizado |
| `BIENVENIDO` | Bienvenido/a | Actualizar el perfil de usuario por primera vez |
| `PRIMERA_CATEGORIA` | Organizado desde el día 1 | Crear la primera categoría personalizada |
| `LOGIN_GOOGLE` | Conectado con Google | Iniciar sesión con Google |
| `PRIMEROS_PASOS` | Primeros pasos | Completar el primer hábito (primer registro total) |

### Constancia
| Código | Nombre | Nivel | Condición |
|---|---|---|---|
| `RACHA_3` | En racha | Fácil | Racha actual = 3 y máxima = actual |
| `RACHA_7` | Buen ritmo | Medio | Racha actual = 7 |
| `RACHA_RECUPERADA` | Resiliencia | Medio | Racha actual = 3 y máxima > actual (indica que hubo una racha rota antes) |
| `RACHA_30` | Imparable | Difícil | Racha actual = 30 |
| `RACHA_100` | Maestro de la constancia | Difícil | Racha actual = 100 |
| `RACHA_365` | Leyenda | Difícil | Racha actual = 365 |

### Volumen
| Código | Nombre | Nivel | Condición |
|---|---|---|---|
| `HABITOS_ACTIVOS_3` | Coleccionista de hábitos | Fácil | 3 hábitos activos simultáneos |
| `HABITOS_ACTIVOS_5` | Vida equilibrada | Medio | 5 hábitos activos simultáneos |
| `REGISTROS_100` | Cien no es nada | Medio | 100 registros completados en total |
| `REGISTROS_500` | Quinientos y contando | Difícil | 500 registros completados en total |
| `REGISTROS_1000` | Mil pasos | Difícil | 1000 registros completados en total |

### Variedad
| Código | Nombre | Nivel | Condición |
|---|---|---|---|
| `CATEGORIAS_3` | Explorador de categorías | Fácil | 3 categorías distintas en uso |
| `CATEGORIAS_5` | Todoterreno | Medio | 5 categorías distintas en uso |
| `FRECUENCIAS_MIXTAS` | Mezcla de frecuencias | Medio | Un hábito diario + semanal + mensual activos a la vez* |
| `CATEGORIAS_PERSONALIZADAS_3` | Diseñador de hábitos | Medio | 3 categorías personalizadas propias creadas |

*Nota: `FRECUENCIAS_MIXTAS` referencia `MENSUAL`, que se elimina en la Fase Crítica — este logro necesitará revisión cuando se implemente el rediseño de rachas.

### Exploración
| Código | Nombre | Nivel | Condición |
|---|---|---|---|
| `PRIMERA_NOTA` | Historias que contar | Fácil | Añadir una nota a un registro |
| `NOTAS_10` | Diario detallado | Medio | Notas en 10 registros distintos |
| `VER_DETALLE_HABITO` | Vista completa | Fácil | Consultar el detalle/heatmap de un hábito |
| `EDITAR_HABITO` | Perfeccionista | Fácil | Editar un hábito existente |
| `INTERACCION_RESENA` | Tu opinión cuenta | Fácil | Interactuar con el diálogo de valoración de Google Play |

---

## Catálogo de Productos (Tienda)

| Producto | Tipo | Precio | Efecto |
|---|---|---|---|
| Escudo de racha | Consumible | 300 (provisional) | Protege 1 pérdida de racha *(lógica de activación aún no implementada — pendiente de la Fase Crítica)* |

**Tipos de producto soportados:**
- **Consumible:** se compra y se gasta al usar (ej. Escudo de racha)
- **Equipable:** se puede poseer varios, pero solo uno activo a la vez (ej. futuro tema de color)
- **Coleccionable:** se posee todo lo comprado, sin exclusión (ej. futuros iconos de perfil)

---

## Notificaciones push

- Recordatorio diario fijo, disparado por `NotificacionScheduler` (cron)
- Horario actual de prueba: 15:37 Europe/Madrid — pendiente de definir horario definitivo
- Contenido: mensaje fijo genérico (personalización dinámica aplazada a V2)
