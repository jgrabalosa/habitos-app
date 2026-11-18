# Ventas por periodo con una tabla de ofertas

_18-nov-2026. Nori vestida, N-4 (T-022). Decidido por Quim el 17-nov sobre la
ficha; programado a mano por Claude (D-32 del proyecto)._

## Contexto

Las prendas de Nori se venden por temporadas: el invierno durante unos meses,
la Navidad del día de salida al 6-ene, y algunas como regalo (precio 0)
durante un tiempo. Un mismo producto puede tener varios periodos seguidos con
precios distintos (gratis una semana y después a 200) y volver otro año. Lo
comprado se puede poner todo el año.

## Decisión

1. **Una tabla `oferta`** (producto, desde, hasta, precio): un producto con
   ofertas solo se compra dentro de una vigente y a su precio; sin ofertas,
   como siempre.
2. **Las fechas son días de la zona del usuario**, los dos extremos
   incluidos. Si se solapan, manda la de `desde` más reciente (Quim, 17-nov).
3. **Precio 0 es un regalo** y no mira el saldo, que puede ser negativo.
4. **Equipar no mira fechas.**
5. **`Producto` no tiene relación JPA hacia sus ofertas:** el catálogo las
   añade en un DTO (`ProductoCatalogoDTO`, el producto con `@JsonUnwrapped`
   más `ofertas`), leídas en una sola consulta.
6. **El catálogo cuenta en UTC** y no saca los productos con todas sus
   ofertas terminadas; la compra cuenta en la zona del usuario. Cerca de
   medianoche pueden no coincidir (409): se acepta y se documenta.

## Alternativas descartadas

- **Fechas en el propio producto** (`vende_desde`, `vende_hasta`): un solo
  periodo por producto. No permite «gratis la primera semana y después a
  200», ni volver a vender el producto otro año sin perder la historia.
- **Un precio de temporada sin historia** (cambiar `producto.precio` a mano al
  empezar y acabar cada periodo): depende de que alguien lo haga a su hora y
  no deja rastro de lo que costaba cada día.
- **Una relación `@OneToMany` de `Producto` a `Oferta`:** perezosa, revienta
  al pasar a JSON con `open-in-view=false`; ansiosa, metería las ofertas
  también en el inventario y en cada lectura de un producto.
- **Un campo `@Transient` en `Producto` relleno por el servicio:** cambiaría
  la forma del inventario (saldría `ofertas` nulo) y mezcla en la entidad
  algo que no es suyo.
