/* Norday Habits — páginas legales (privacidad.html y eliminar-cuenta.html).
 *
 * Se ven con la identidad que el visitante eligió en la landing: la misma
 * clave de localStorage que guarda js/landing.js. Sin elección, Profundidad.
 * Va en <head> y sin defer, para que la página no aparezca un instante con
 * otra identidad. Sin JavaScript en línea: la CSP de Caddy lo bloquea.
 */
(function () {
  'use strict';

  // El fondo de cada identidad, para la barra del navegador del móvil.
  var FONDOS = { profundidad: '#070D19', neotokyo: '#090614', dulce: '#FFFCF5' };

  var id = null;
  try { id = localStorage.getItem('norday-identidad'); } catch (e) { /* sin almacenamiento, da igual */ }
  if (!Object.prototype.hasOwnProperty.call(FONDOS, id)) id = 'profundidad';

  document.documentElement.setAttribute('data-id', id);
  var meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', FONDOS[id]);
})();
