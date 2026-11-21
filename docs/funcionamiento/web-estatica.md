# La web antigua no se toca

Movido desde `CLAUDE.md` (T-005a).

En `norday-server/src/main/resources/static/` sólo viven la landing
(`index.html`), las dos páginas legales (`privacidad.html`,
`eliminar-cuenta.html`), `favicon.ico`, `js/landing.js`, `js/legal.js`, y en `img/` las imágenes y las letras.

El dashboard web antiguo —`app.html`, `login.html`, `habito.html`,
`habito-detalle.html`, `logros.html` y sus `js/` y `css/`— se borró el
12-sep-2026. No lo enlazaba nadie, no se tocaba desde agosto, y seguía
servido en producción: un `login.html` vivo que guardaba el token en
`localStorage`.

**Regla permanente: ahí no se trabaja.** Ni arreglos, ni refactors, ni
"aprovechar que estamos". Si algún día hace falta la aplicación en formato
web, se empieza de cero y en su sitio, no reanimando esto.

Consecuencias prácticas:

- No se añaden rutas nuevas al `permitAll` de `SecurityConfig` para servir
  páginas. Las que hay son las que hay.
- Los estáticos no llevan JavaScript en línea: lo impide la CSP de Caddy
  (`script-src 'self'`, sin hashes). Cualquier `<script>` en línea o atributo
  `onclick` nuevo se bloqueará en el navegador. El bloque
  `<script type="application/ld+json">` de la landing no cuenta como
  JavaScript en línea: es un dato, no se ejecuta y la CSP no lo bloquea.
- Al cambiar un DTO de entrada, el único cliente que queda es la aplicación
  móvil, incluida **la APK ya instalada en los dispositivos**. La web ya no
  cuenta como cliente del contrato.
