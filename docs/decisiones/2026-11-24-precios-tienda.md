# Precios de la tienda (rebalanceo del 24-nov-2026, D-71)

## Contexto

Sobraban puntos: un usuario normal compraba toda la tienda en menos de un
mes. Lo que se gana (`ParametrosPremio`, `ParametrosPremioEnfoque`, hitos,
logros) está calibrado y no se toca; lo que se compra cuesta más.

## Decisión

Precios iguales para todos y fijos («lo vamos viendo»), sin mirar los saldos
reales. Lo ya comprado y los saldos no se tocan.

| Código | Antes | Ahora |
|---|---|---|
| `TEMA_PROFUNDIDAD`, `TEMA_NEOTOKYO_PLUS`, `TEMA_DULCE` | 1000 | 1250 |
| `PRENDA_GORRO_LANA` (producto y oferta) | 200 | 400 |
| `PRENDA_BUFANDA` (producto y oferta) | 250 | 400 |
| `PRENDA_TRAJE_NOEL` (producto y oferta) | 400 | 500 |
| `PRENDA_GORRO_NOEL` | 0 | 0 (regalo) |
| `PAUSA_HABITO` | 300 | 350 |
| `COMIDA_BASICA` | 50 | 100 |

Las prendas de invierno suben ya: con tres hábitos, las tres prendas de pago
son unos 18 días. Una identidad (1250) son unas tres semanas con dos sesiones
de Focus al día y unas dos y media con tres hábitos.

Se aplica con la migración V34 (con el precio viejo en el `WHERE`, para no
pisar un cambio a mano) y con los mismos números en los initializers, para las
bases nuevas.

## Alternativas descartadas

- **Subir solo lo nuevo y dejar lo de invierno como estaba.** Las prendas de
  invierno se habrían quedado baratas justo en la temporada en que más se
  compran.
- **Identidades a 2.500.** Se acerca demasiado a lo que se gana en un mes y
  deja el premio de los logros de identidad (250) muy lejos del precio.
- **Un 15 % redondeado a 50 en todo.** Dejaba lo barato (comida, prendas)
  casi igual, que es donde estaba el desajuste.

## Consecuencias

- El premio de los logros de identidad (250) sigue siendo menor que el precio
  de un tema, como exige el catálogo.
- Quien ya compró a los precios viejos conserva lo comprado; no hay
  reembolsos ni compensaciones.
- Cambiar un precio más adelante vuelve a ser migración e initializer a la vez
  (`docs/funcionamiento/catalogos.md`).
