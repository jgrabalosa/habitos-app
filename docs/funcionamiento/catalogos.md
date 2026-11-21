# Catálogos: siembra, compartidos, retirados e identidad

Movido desde `CLAUDE.md` (T-005a). Para el catálogo de la tienda en sí, ver
«Catálogo de productos» en `docs/Logicas/03-calculos-y-logica.md`.

## Siembra de catálogos

Cada módulo siembra lo suyo con su propio `CommandLineRunner`:
`CategoriasInitializer`, `LogrosHabitosInitializer` y
`ProductosHabitosInitializer` (habitos; este último, la `PAUSA_HABITO` de la
tienda, con `origenApp = habitos`), `LogrosEnfoqueInitializer` (enfoque: los
21 logros de Norday Focus, con `origenApp = enfoque`) y
`CatalogoGamificacionInitializer` (gamificacion; también las cuatro prendas
de Nori, genéricas, con su colección, la versión mínima del core y su
oferta: ver «Catálogo de productos» en `docs/Logicas/03`).

La comprobación de idempotencia de logros y productos es **por código,
fila a fila** (`findByCodigo(...) != null` → saltar), no una guarda
todo-o-nada sobre la tabla entera: el catálogo de logros lo siembran dos
módulos distintos, y una guarda global haría que el segundo en arrancar no
sembrara nada.

## Catálogos compartidos

Los catálogos de logros y productos distinguen a qué app pertenecen con el
campo `origenApp` de `Logro` y `Producto`. Ya no es un plan a futuro: está
en producción y es carga estructural.

⚠️ **Invariante crítico.** `CatalogoGamificacionInitializer` recorre en cada
arranque todo `logroDAO.findAll()` y retira los que tengan
`origenApp = null`, sigan activos y no aparezcan en su propia lista
`LOGROS`. Es decir: **sembrar un logro genérico y registrarlo en esa lista
son la misma acción.** Un initializer aparte que cree logros con
`origenApp = null` es una bomba de relojería, porque el siguiente arranque
los dará por retirados. Vale igual para productos.

Hay un tope de seguridad: si los candidatos a retirada superan en número a
los códigos vigentes, no se toca nada y se avisa por log. Está pensado
justo para cazar a otro módulo sembrando sin `origenApp`.

El saldo de puntos (UsuarioMoneda) es único y compartido entre todas las
apps del ecosistema — no crear muros entre apps.

## Lo retirado del catálogo

Retirado no es borrado: si la fila ya existía, queda en la BD con
`activo = false` y el cliente deja de recibirla. Cada retirada está
comentada en su initializer con fecha y motivo.

| Código | Retirado | Motivo |
|---|---|---|
| `ESCUDO_RACHA` | 24-ago-2026 | Se vendía sin estar implementado (migración V6) |
| `INTERACCION_RESENA` | 24-ago-2026 | Premiar la reseña va contra la política de Google Play (V7). El diálogo de reseña sigue en la app; sólo se quita la recompensa |
| `TEMA_ALBA` | 6-sep-2026 | Se sale a Google Play con tres identidades: Profundidad, Neotokyo+ y Dulce |
| Los diez `AVATAR_*` | 15-sep-2026 | No encajaban con la app. El esqueleto sigue en `norday_flutter_core` |
| `IDENTIDAD_ALBA` | 17-sep-2026 | Inalcanzable sin `TEMA_ALBA` |

Para retirar un logro o producto genérico basta con sacarlo del array de
`CatalogoGamificacionInitializer`: el siguiente arranque lo desactiva.

**Reactivar no es simétrico.** Descomentar la fila no basta si ya existe en
la BD con `activo = false`: `findByCodigo(...) != null` salta la creación y
hace falta un `UPDATE` manual o una migración nueva. `ESCUDO_RACHA` e
`INTERACCION_RESENA` los desactivó una migración (V6 y V7): nunca editar
una migración ya aplicada, porque Flyway rechaza el cambio de checksum.
Antes de reactivar, comprobar en la BD si la fila existe. A 24-sep-2026 no
existe ninguna de las retiradas, ni en producción ni en staging: las BD se
reiniciaron después de retirarlas, así que hoy bastaría con descomentar.

## Elección de identidad en el onboarding

El usuario elige identidad (tema visual) la primera vez que entra:
`POST /api/gamificacion/identidad/elegir/{usuarioId}/{productoId}` →
`otorgarIdentidadElegida`, que otorga **y** equipa en un solo paso.

**No reutilizar `otorgarProducto` para esto.** Su guarda solo impide repetir
el *mismo* producto: llamándola cuatro veces con códigos distintos se
conseguían las cuatro identidades gratis (4000 monedas). La guarda correcta
es "¿ya tiene *alguna* de categoría Tema?", y para eso está
`poseeAlgunoDeCategoria` en el DAO.

`asegurarIdentidad` es la red de seguridad: si un usuario llega sin ninguna
identidad, le otorga `TEMA_PROFUNDIDAD`.

**Se llama desde el controlador, nunca desde el servicio.** Dentro de
`loginConGoogle`, que es `@Transactional`, una excepción marcaría la
transacción como rollback-only y el commit reventaría después con
`UnexpectedRollbackException`: el usuario recibiría un 401 de Google por un
fallo de identidad. Cualquier red de seguridad que se añada en el futuro
tiene el mismo problema y la misma solución.

Tiene una **ventana de gracia de 24h** desde el alta, para que no dispare
durante el onboarding y le robe la elección al usuario.
