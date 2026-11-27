# Deshacer un completado: ReversionRegistro

Movido desde `CLAUDE.md` (T-005a).

Deshacer un completado revierte todos sus efectos. Al completar,
`RegistroService` guarda una `ReversionRegistro` ligada al registro: las
monedas y la XP que dio ese completado, el estado previo del día completo de
la mascota, y, en `ReversionLogro`, cada logro que desbloqueó, incluidos los
que dispara la mascota.

Al deshacer:

- los logros se retiran, para que puedan volver a concederse;
- las monedas no se borran, se compensan con un movimiento de signo
  contrario: el libro de `UsuarioMoneda` es de sólo añadir, y se permite
  saldo negativo;
- la XP se resta en lo que dio el completado, sin volver al valor previo,
  que borraría la ganada después.

**La racha ya no se guarda ni se restaura (T-039).** Desde el 21-oct-2026
(C3) completar no actualiza la `Racha`, así que restaurarla al deshacer
dejaba lo mismo que había. `RegistroService` crea la `ReversionRegistro` con
`racha_actual_previa`, `racha_maxima_previa`, `periodo_meta_alcanzada_previo`
y `ultima_fecha_previa` a `null`, y deshacer no lee esas columnas.

Las cuatro columnas **se quedan** en `reversion_registro` (ya son nullables),
sin migración: quitarlas impediría volver a un jar anterior (`docs/despliegue.md`,
«Vuelta atrás»). Las filas antiguas conservan sus valores sin uso. Se borrarán
con la retirada entera de `Racha`, que es otra tarea.

**Todo efecto nuevo que se añada a un completado tiene que quedar en la
reversión.** Si no, deshacer lo deja puesto y el usuario se queda con
puntos, XP o logros que no le tocan.
