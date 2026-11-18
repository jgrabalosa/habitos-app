# Una cosa puesta por sitio, garantizado por la base de datos

_18-nov-2026. Nori vestida, N-5 (T-023). Decidido por Quim el 17-nov sobre la
ficha; programado a mano por Claude (D-32 del proyecto)._

## Contexto

Lo puesto era `usuario_producto.equipado`, y que solo hubiera uno por
categoría lo cuidaba la lógica de `equiparProducto`. Con dos móviles a la vez
un usuario podía quedarse con dos equipados en la misma categoría, y entonces
`findEquipadoPorCategoria` (`getSingleResult`) fallaba para siempre al
equipar en ella. Ya podía pasar con las identidades, y con las prendas de
Nori (varios toques seguidos en el vestidor) es más probable.

## Decisión

1. **Una tabla `usuario_equipado`**, una fila por usuario y sitio (la
   categoría del producto), con clave primaria `(usuario_id, categoria)` y
   `usuario_producto_id` único. Es la fuente de verdad de lo puesto.
2. **Cambiar de prenda es actualizar la fila del sitio** (`INSERT … ON
   CONFLICT DO UPDATE`), nunca tener dos filas.
3. **`usuario_producto.equipado` se sigue escribiendo, coherente**, porque el
   inventario y la exportación lo devuelven y los clientes lo leen.
4. **La migración deja a quien tenga dos o más equipados en una categoría el
   de `usuario_producto_id` más alto**, la misma regla que ya sigue el core al
   leer el inventario, y rellena la tabla en una sola sentencia.
5. **Desequipar bloquea al usuario**, como el resto de la tienda.
6. Una por categoría vale para todo lo equipable (identidades y prendas).

## Alternativas descartadas

- **Un índice único parcial** sobre `usuario_producto (usuario_id, categoria)
  WHERE equipado`: al cambiar de prenda en el mismo sitio hay un instante con
  dos equipadas, y Hibernate escribe los cambios al final, en el orden en que
  cargó las filas: «la nueva, equipada» puede ir antes que «la vieja, no», y
  el índice la rechazaría. Además, la categoría vive en `producto`, no en
  `usuario_producto`, y habría que copiarla.
- **Seguir cuidándolo solo con la lógica** (y el bloqueo del usuario):
  arregla la carrera de hoy, pero no impide que un camino nuevo, una
  migración o un arreglo a mano deje dos equipados, y el fallo volvería a ser
  para siempre.
- **Quedarse con el primero en la limpieza**: el core ya decide por el de id
  más alto al leer el inventario; quedarse con otro cambiaría lo que el
  usuario ve puesto.
