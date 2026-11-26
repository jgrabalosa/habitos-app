# Lecciones aprendidas: persistencia, servicios y contrato

Movido desde la sección «Lecciones aprendidas» del `CLAUDE.md` (T-005a).
Errores que ya se cometieron una vez. No se vuelven a cometer. Las lecciones
nuevas de este tipo se añaden aquí; las de compilar, desplegar y método de
trabajo siguen en el `CLAUDE.md` o en `lecciones-operacion.md`.

## Persistencia y entidades

- **Lo que «solo puede haber uno» lo garantiza la base de datos, no la
  lógica.** Dos móviles a la vez dejaban dos identidades equipadas en la misma
  categoría, y `findEquipadoPorCategoria` (`getSingleResult`) fallaba para
  siempre. Y con una tabla de una fila por sitio (`usuario_equipado`), no con
  un índice único parcial: al cambiar de prenda, el índice se incumple a
  mitad del cambio y Hibernate escribe las filas en el orden en que las cargó.
- **`LAZY` en una asociación no basta para `open-in-view=false`**: los DAO son
  `@Transactional` por clase y las entidades salen desconectadas. Hace falta
  transacción en quien lee la asociación.
- **Un test no compara el texto de un error de PostgreSQL: mira su SQLSTATE**
  (`23505` clave duplicada, `40P01` abrazo mortal). El texto va en el idioma
  del servidor: en el del robot, «llave duplicada viola restricción de
  unicidad». `UsuarioEquipadoTest` buscaba «duplicate» y fallaba allí, y los
  tests de concurrencia que buscaban «deadlock» habrían dejado de vigilar.
- **Nombres de columna, del repositorio, nunca de memoria.** La FK de
  `registro` es `habito_ref`, no `habito_id`.
- **El nombre real de la base es `habitos_db`**, no `habitas_db`.
- **`em.find` con bloqueo sobre una entidad que ya está en la sesión no la
  relee**: pide el bloqueo y deja los valores de antes de esperar. Con
  open-in-view el controlador ya la ha cargado, así que lo que se decida
  tras el bloqueo hay que decidirlo con `em.refresh(..., PESSIMISTIC_WRITE)`.
  Un completado veía activo un hábito pausado en medio, y un `PUT` lo
  reactivaba al guardar.

## Servicios y reglas de negocio

- **Una aserción sobre el objeto que ha construido el propio test no prueba
  nada.** Para comprobar lo que el servicio guarda hace falta `ArgumentCaptor`
  sobre el `save` del DAO.
- **Una guarda de nulo sobre un campo inyectado es una señal de alarma.**
  `if (dao == null) return ...` sólo existe para que los tests viejos no
  revienten, y su efecto es que el camino nuevo no se prueba.
- **Un `switch` sobre un valor exacto deja de valer en cuanto ese valor puede
  saltar.** Los hitos de racha (3, 7, 30, 100, 365) eran seguros mientras la
  racha subía de uno en uno; la comparación correcta no es `actual == umbral`.
- **Al refactorizar, la regla perezosa se pierde sin hacer ruido.** La rotura
  de racha (`sigueViva`) desapareció en un refactor y el efecto era una racha
  muerta de 6 que seguía a 7 en vez de reiniciarse a 1, repartiendo puntos de
  hito que no tocaban. Al mover lógica, enumerar qué reglas se aplicaban antes
  y comprobar una a una que siguen aplicándose.
- **Test en rojo antes del arreglo.** Si el test nuevo pasa contra el código
  sin arreglar, no demuestra nada: parar.
- **Un «después − antes» solo es tuyo si nadie escribe en medio.** El
  completado mide sus monedas y sus logros así, y solo bloqueaba el hábito:
  el paso de otro hábito del mismo usuario se colaba en la cuenta, y
  deshacer el primero devolvía lo del otro y le retiraba sus logros. Lo que
  se mide sobre el usuario se protege bloqueando al usuario.
- **Los bloqueos, siempre en el mismo orden: usuario y después hábito.**
  Todo lo que cambia un hábito pasa por `HabitoService.bloquear` (completar,
  deshacer, editar, pausar, reanudar, borrar, anular un cambio programado),
  que bloquea los dos en ese orden y devuelve el hábito releído. No se
  bloquea un hábito por otro camino. Ojo también con los bloqueos
  implícitos: borrar o actualizar filas las bloquea. La baja de la cuenta y
  el borrado de un hábito borraban las hijas antes que al usuario o al
  hábito; por eso bloquean al empezar.
- **Todo lo que concede logros, monedas o XP bloquea al usuario como
  primera escritura** (Hoy, crear hábito o categoría, notas, perfil, login
  con Google, toda la tienda). Si no, se cuela en el «después − antes» de
  un completado a medias, y deshacerlo lo devuelve: una compra hecha en
  medio salía gratis. Nunca dentro de `registrarMovimiento`: llegaría
  después de leer el saldo y fuera de orden. La lista está en 03,
  «Bloqueos».
- **Una lectura que escribe, escribe solo sus columnas, y solo si no han
  cambiado.** Poner al día la copia del objetivo se hace desde lecturas sin
  bloqueo, con una copia del hábito que puede ser vieja; guardarlo entero
  reactivaba hábitos pausados, y escribir sin condición deshacía un `PUT`
  confirmado entretanto.
- **Bloquear antes de leer, o escribir solo la columna que cambia.** Leer,
  bloquear y guardar la entidad entera pisa lo confirmado mientras se
  esperaba (el perfil pisaba las preferencias; la valoración, la
  nota). `IUsuarioDAO.bloquearYReleer` para el usuario; un `UPDATE` de una
  columna (nota, valoración, nombre de la mascota) donde no hay bloqueo.
- **Toda espera de bloqueo tiene tope** (`norday.bloqueo.espera-maxima`, 10 s
  por defecto, `lock_timeout` de la transacción) y acaba en 503 traducido.
  No depender de la configuración de la base de datos: la de producción no
  está versionada.
- **Un logro de estado se mira cada vez, no solo en el instante del
  cambio.** `MASCOTA_CRIA` se daba solo al cambiar de fase: si deshacer lo
  retiraba con la mascota todavía en cría, no volvía nunca.

- **Un rechazo esperado lleva su excepción con nombre, no `RuntimeException`.**
  «Saldo insuficiente» era una `RuntimeException` y salía como `ERROR` con
  traza en el log. `PeticionInvalidaException` → 400 y `WARN` en una línea;
  `ConflictoException` → 409. Solo lo inesperado llega al `ERROR`.

## Contrato con los clientes

- **Un cambio de contrato también rompe a los clientes viejos.** Al cambiar un
  DTO de entrada, el cliente que queda es la aplicación móvil, incluida la APK
  ya instalada. Si el servidor ignora un campo desconocido, un `null` puede
  borrar datos (por eso `HabitoService.actualizar` trata un `null` como «sin
  cambio»).
- **Un campo nuevo en la entrada se conserva al actualizar si no llega.** Las
  APK antiguas no lo mandan y `update` hace merge de la entidad entera: sin
  conservarlo, cada edición desde una APK vieja lo borra (`icono`, T-011a).
  El test de «actualizar sin el campo» tiene que fallar si se quita esa línea.
- **Una línea base de contrato sólo se captura antes de desplegar.** Después
  ya no hay con qué comparar.
