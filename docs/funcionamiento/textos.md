# Textos y bundles de mensajes

Movido desde `CLAUDE.md` (T-005a).

El motor no contiene texto de cara al usuario específico de una app. Cada
módulo aporta su propio bundle y lo registra implementando
`ProveedorMensajes`; `MensajesConfig` los recolecta. `EmailService` solo
recibe claves, nunca frases.

Los ficheros `.properties` viven todos juntos en
`norday-server/src/main/resources/mensajes/`, aunque cada uno lo aporte
conceptualmente su módulo. En ejecución da igual: el jar los une en un
único classpath.

De ahí que `BundlesMensajesTest` viva en `norday-server` y no en
`norday-motor`: comprueba los bundles de `core`, `habitos` y `enfoque` (que
cada idioma traduzca exactamente las claves del base, y que el motor no
lleve textos de una app), y que cada motivo de rechazo de un aporte
(`DestinoMinutosHabitos.MOTIVOS` y los de `MotivosEnfoque`) tenga texto en
el bundle de quien lo pone y quepa en `sesion_enfoque.aporte_motivo` (40).
`norday-server` es el único módulo que ve todos. Un test que cruce
módulos pertenece al módulo que los reúne.

El fichero base (sin sufijo de idioma) va en español, que es la caída
acordada. Idiomas soportados: `es`, `en`, `pt`.

Los catálogos viajan por `codigo`; el nombre en BD es solo caída para el
cliente, que traduce por código.
