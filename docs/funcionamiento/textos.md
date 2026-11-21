# Textos y bundles de mensajes

Movido desde `CLAUDE.md` (T-005a).

El motor no contiene texto de cara al usuario específico de una app. Cada
módulo aporta su propio bundle y lo registra implementando
`ProveedorMensajes`; `MensajesConfig` los recolecta. `EmailService` solo
recibe claves, nunca frases.

**El correo de bienvenida es HTML con Nori** (T-044). La plantilla es
`norday-server/src/main/resources/correo/bienvenida.html` y sus imágenes
(`nori.png`, `instagram.png`, `tiktok.png`, tal cual, sin redibujar) están en
`correo/img/`, en el mismo módulo que los bundles. `CorreoBienvenidaService`
(motor) sustituye los marcadores `{{...}}` con las claves `email.bienvenida.*`
del bundle de `habitos` en el idioma del usuario y escapa todo valor que entra
en el HTML (el nombre, el primero: lo escribe el usuario). Sin motor de
plantillas ni dependencias nuevas. Lo manda `EmailService.enviarHtml`:
`multipart/related` con las imágenes dentro del correo por `Content-ID`
(`cid:nori`, `cid:instagram`, `cid:tiktok`), nunca en base64 ni por URL, y
dentro una `multipart/alternative` con texto plano y HTML. Los enlaces (Play,
Instagram, TikTok) son claves del bundle, no constantes del motor. Un texto
nuevo de la plantilla es un marcador nuevo y una clave en los tres idiomas.
**Apóstrofos:** solo el titular lleva argumentos (`{0}`) y pasa por
`MessageFormat`, donde el apóstrofo se dobla (`I''m`); las demás claves se leen
tal cual y llevan `'` simple. `enviarEmail` de texto plano no cambia
(recuperación de contraseña). Tests: `CorreoBienvenidaTest`.

**El remitente:** en staging y producción `norday.mail.from` lleva nombre
(`Norday <hola@norday.app>`). `enviarHtml` toma de ahí solo la dirección y le
pone el nombre de la clave `email.bienvenida.remitente`; antes salía
`Nori de Norday Habits <Norday <…>>`, que no es una dirección, y el correo no
se enviaba (21-nov-2026, visto en staging). Los tests usaban un remitente sin
nombre: `CorreoBienvenidaTest` prueba ahora también el de staging.

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
