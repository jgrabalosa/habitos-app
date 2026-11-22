# Borrar una categoría deja sus hábitos «sin categoría»

(T-031a, 22-nov-2026.)

## Contexto

`DELETE /api/categorias/{id}` fallaba con un 500 si algún hábito usaba la
categoría: la clave ajena `FK_habito_categoria` (`habito.tipo_id`) lo impide.
La app va a dejar al usuario crear y borrar categorías propias (T-031b), así
que hay que decidir qué pasa con los hábitos.

## Decisión

Al borrar, los hábitos que la usan pasan a `tipo = null` («sin categoría»),
también los pausados y los inactivos, y después se borra la categoría, todo en
la misma transacción. No cambia nada más de los hábitos.

El paso es un único `UPDATE` en lote (`IHabitoDAO.quitarCategoria`) que llama
`CategoriaService.eliminar` tras bloquear al usuario. Vive en el módulo
`habitos`; el motor no gana ninguna referencia a categorías.

## Alternativas descartadas

- **Impedir el borrado mientras tenga hábitos (409).** Obliga al usuario a ir
  hábito por hábito antes de poder borrar, y la categoría es solo una
  etiqueta: no justifica el bloqueo.
- **Borrar también los hábitos.** Destruiría registros, rachas y objetivos por
  quitar una etiqueta. Irreversible.
- **Recorrer los hábitos con la entidad y guardarlos uno a uno.** Más código,
  una lectura y un guardado por hábito, y guardar la entidad entera pisa lo que
  otra petición haya confirmado entretanto (ver «Una lectura que escribe» en
  `lecciones-persistencia-y-servicios.md`). El `UPDATE` toca solo `tipo_id`.
- **`ON DELETE SET NULL` en la clave ajena.** Dejaría la regla en el esquema,
  y el esquema de producción lo lleva Flyway sobre una base creada con
  `ddl-auto=update`: una migración más con riesgo para algo que el servicio
  resuelve con una línea.

## Consecuencias

- Un hábito sin categoría ya era un caso válido (`categoriaId` null al crear).
- El logro `CATEGORIAS_3`/`CATEGORIAS_5` cuenta categorías distintas entre los
  hábitos activos: borrar una categoría puede bajar ese número, pero los logros
  ya concedidos no se retiran.
- Los hábitos no avisan al usuario de que han perdido la categoría: la app
  (T-031b) debería decirlo antes de confirmar el borrado.
