# Deshacer un completado: ReversionRegistro

Movido desde `CLAUDE.md` (T-005a).

Deshacer un completado revierte todos sus efectos. Al completar,
`RegistroService` guarda una `ReversionRegistro` ligada al registro: las
monedas y la XP que dio ese completado, el estado previo de la racha y del
día completo de la mascota, y, en `ReversionLogro`, cada logro que
desbloqueó, incluidos los que dispara la mascota.

Al deshacer:

- los logros se retiran, para que puedan volver a concederse;
- las monedas no se borran, se compensan con un movimiento de signo
  contrario: el libro de `UsuarioMoneda` es de sólo añadir, y se permite
  saldo negativo;
- la XP se resta en lo que dio el completado, sin volver al valor previo,
  que borraría la ganada después;
- la racha vuelve a su estado previo.

**Todo efecto nuevo que se añada a un completado tiene que quedar en la
reversión.** Si no, deshacer lo deja puesto y el usuario se queda con
puntos, XP o logros que no le tocan.
