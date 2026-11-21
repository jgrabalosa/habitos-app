# Notificaciones: un token por app

Movido desde `CLAUDE.md` (T-005a).

FCM da un token por instancia de app: Norday Habits y Norday Focus en el
mismo móvil tienen dos. Cada usuario tiene una fila por app en
`usuario_dispositivo` (motor, `core`), y nada más.

- **Cada app registra su token con la cabecera `X-Norday-App`** en
  `PUT /api/usuarios/{id}/fcm-token`. Sin cabecera, o vacía, es `habitos`:
  la APK ya instalada no la manda. Una app que no está en `AppNorday` recibe
  400 y no se guarda nada. **Una app nueva se añade a `AppNorday` antes de
  publicarse.**
- **Un token es de un solo usuario por app.** Si llega para otro (cerró
  sesión y entró otra persona en el mismo móvil), se le quita al anterior:
  su fila de esa app y, para `habitos`, su `usuario.fcm_token`. Si no, los
  avisos del anterior llegarían al móvil del nuevo.
- **Todo envío push pide el token de la app a la que va**, con
  `DispositivoService.tokenDe(usuarioId, appId)` (o `tokensDe` para muchos
  usuarios). Nunca `usuario.getFcmToken()`. Los recordatorios de hábitos y los
  avisos de la app (`AvisosAppScheduler`) van a `habitos`. Si FCM responde `UNREGISTERED`, `olvidarToken`.
- **`usuario.fcm_token` es columna de transición**: se sigue escribiendo
  con el token de `habitos` para poder volver al jar anterior a V24, y
  `DispositivoService` la lee como reserva para `habitos` cuando no hay
  fila. Admite 255 caracteres y la tabla 4096: un token más largo va solo a
  la tabla y deja la columna a null. Ningún envío la lee directamente. Volver al jar anterior solo es
  seguro mientras Norday Focus no esté publicada, y al volver a avanzar hay
  que resincronizar (SQL en `docs/despliegue.md`). No se quita hasta que no
  haga falta volver atrás.
