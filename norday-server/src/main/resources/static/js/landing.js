/* Norday Habits — landing.
 *
 * Todo lo que se dibuja aquí es una traducción a canvas de lo que dibuja la
 * app, en norday_flutter_core (v0.54.0):
 *   - fondo_estelar.dart, capa_constelacion.dart, constelaciones.dart
 *   - fondo_ciudad.dart, fondo_burbujas.dart, grano_papel.dart, lapiz.dart
 *   - check_circular.dart, superficie_identidad.dart, indicador_nivel.dart
 * y en habitos_app_mobile: dashboard_screen.dart, identidad_ui.dart y
 * tira_semana.dart. Mismos colores, tamaños, tiempos y curvas. Si la app
 * cambia, esto también.
 *
 * Sin JavaScript en línea: la CSP de Caddy lo bloquea (script-src 'self').
 */
(function () {
  'use strict';

  var quieto = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var DPR = Math.min(window.devicePixelRatio || 1, 2);

  /* ── Tokens (theme/app_theme.dart e identidades_paleta.dart) ──────────── */
  var TOKENS = {
    profundidad: {
      forma: 'glass', bg: '#070D19', surface: '#1E2E4C', surface2: '#101B2F',
      primary: '#27C76F', success: '#27C76F', streak: '#FFB020', points: '#FFB020',
      text: '#EEF2F6', muted: '#A3B3C9', inactivo: '#3F4551', aro: '#8D95A0',
      trazo: '#27C76F', successText: '#27C76F', pointsText: '#FFB020', surfaceAlta: '#2B4068', transicion: 280,
      curva: [0.215, 0.61, 0.355, 1]
    },
    neotokyo: {
      forma: 'chamfer', bg: '#090614', surface: '#110B21', surface2: '#160F28',
      primary: '#FF2E88', success: '#FF2E88', streak: '#00E5FF', points: '#FFE347',
      text: '#F5EEFF', muted: '#A08FC2', inactivo: '#473E5B', aro: '#6A5D85',
      trazo: '#FF2E88', successText: '#FF2E88', pointsText: '#FFE347', surfaceAlta: '#1D1433', transicion: 200, chaflan: 10,
      curva: [0.19, 1, 0.22, 1]
    },
    dulce: {
      forma: 'pill', bg: '#FFFCF5', surface: '#FFFCF5', surface2: '#F6EFE3',
      primary: '#FF6FA5', success: '#E53F80', streak: '#E53F80', points: '#E86A58',
      text: '#4A2E3D', muted: '#7E3A67', inactivo: '#C9ABB9', aro: '#A57492',
      trazo: '#E53F80', successText: '#B01E5A', pointsText: '#BB2E1A', surfaceAlta: '#FFFCF5', transicion: 320,
      curva: [0.175, 0.885, 0.32, 1.275]
    }
  };
  var idActual = 'profundidad';
  function T() { return TOKENS[idActual]; }

  /* ── Utilidades ───────────────────────────────────────────────────────── */
  function rgba(hex, a) {
    var n = parseInt(hex.slice(1), 16);
    return 'rgba(' + (n >> 16 & 255) + ',' + (n >> 8 & 255) + ',' + (n & 255) + ',' + Math.max(0, Math.min(1, a)) + ')';
  }
  /* Azar con semilla: el mismo dibujo en cada fotograma, como en la app. */
  function azar(semilla) {
    var a = semilla >>> 0;
    return function () {
      a = (a + 0x6D2B79F5) >>> 0;
      var t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  var easeOut = function (t) { return 1 - (1 - t) * (1 - t); };
  var easeOutCubic = function (t) { return 1 - Math.pow(1 - t, 3); };
  var easeInOut = function (t) { return t < .5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2; };
  function easeOutBack(t) { var c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); }
  function elasticOut(t) { // Curves.elasticOut, periodo 0,4
    if (t === 0 || t === 1) return t;
    var p = 0.4, s = p / 4;
    return Math.pow(2, -10 * t) * Math.sin((t - s) * (Math.PI * 2) / p) + 1;
  }
  function intervalo(v, a, b, curva) {
    var t = clamp((v - a) / (b - a), 0, 1);
    return curva ? curva(t) : t;
  }
  /* Un canvas al tamaño CSS pedido, con su densidad. */
  function preparar(cv, w, h) {
    cv.width = Math.max(1, Math.round(w * DPR));
    cv.height = Math.max(1, Math.round(h * DPR));
    cv.style.width = w + 'px';
    cv.style.height = h + 'px';
    var c = cv.getContext('2d');
    c.setTransform(DPR, 0, 0, DPR, 0, 0);
    c.clearRect(0, 0, w, h);
    return c;
  }

  /* ── Figuras como polilíneas densas (para el lápiz) ───────────────────── */
  function circulo(cx, cy, r) {
    var pts = [], n = Math.max(24, Math.ceil(2 * Math.PI * r / 1.5));
    for (var i = 0; i < n; i++) {
      var a = -Math.PI / 2 + 2 * Math.PI * i / n;
      pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
    }
    return { pts: pts, cerrado: true, dentro: function (x, y) { return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r; },
      path: function (c) { c.beginPath(); c.arc(cx, cy, r, 0, Math.PI * 2); c.closePath(); } };
  }
  function rrect(x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    var pts = [];
    function arco(cx, cy, a0) {
      var n = Math.max(4, Math.ceil(r * Math.PI / 2 / 1.5));
      for (var i = 0; i <= n; i++) {
        var a = a0 + (Math.PI / 2) * i / n;
        pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)]);
      }
    }
    arco(x + w - r, y + r, -Math.PI / 2);
    arco(x + w - r, y + h - r, 0);
    arco(x + r, y + h - r, Math.PI / 2);
    arco(x + r, y + r, Math.PI);
    function dentro(px, py) {
      if (px < x || px > x + w || py < y || py > y + h) return false;
      var qx = px < x + r ? x + r : px > x + w - r ? x + w - r : px;
      var qy = py < y + r ? y + r : py > y + h - r ? y + h - r : py;
      return (px - qx) * (px - qx) + (py - qy) * (py - qy) <= r * r;
    }
    return { pts: pts, cerrado: true, dentro: dentro,
      path: function (c) { c.beginPath(); if (c.roundRect) c.roundRect(x, y, w, h, r); else c.rect(x, y, w, h); c.closePath(); } };
  }
  function linea(x0, y0, x1, y1) { return { pts: [[x0, y0], [x1, y1]], cerrado: false }; }

  /* Muestrea la figura cada `paso` px: punto, normal y distancia recorrida. */
  function muestrear(fig, paso) {
    var p = fig.pts.slice();
    if (fig.cerrado) p.push(p[0]);
    var acc = [0];
    for (var i = 1; i < p.length; i++) acc.push(acc[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]));
    var L = acc[acc.length - 1];
    var cuenta = Math.max(2, Math.round(L / paso));
    var total = fig.cerrado ? cuenta : cuenta + 1;
    var salto = L / cuenta, ms = [], k = 1;
    for (var j = 0; j < total; j++) {
      var d = Math.min(j * salto, L);
      while (k < acc.length - 1 && acc[k] < d) k++;
      var seg = acc[k] - acc[k - 1] || 1, u = (d - acc[k - 1]) / seg;
      var x = p[k - 1][0] + (p[k][0] - p[k - 1][0]) * u, y = p[k - 1][1] + (p[k][1] - p[k - 1][1]) * u;
      var tx = (p[k][0] - p[k - 1][0]) / seg, ty = (p[k][1] - p[k - 1][1]) / seg;
      ms.push({ x: x, y: y, nx: -ty, ny: tx, s: d });
    }
    return { ms: ms, L: L };
  }

  /* ── El lápiz (lapiz.dart) ────────────────────────────────────────────── */
  function pasada(c, M, p, color) {
    var ms = M.ms, n = ms.length, r = azar(p.semilla);
    var f1 = 0.05 + r() * 0.05, f2 = 0.11 + r() * 0.08;
    var fase1 = r() * 2 * Math.PI, fase2 = r() * 2 * Math.PI, faseP = r() * 2 * Math.PI;
    function punto(i) {
      var m = ms[i % n], s = m.s + Math.floor(i / n) * M.L;
      var onda = p.amplitud * (0.6 * Math.sin(s * f1 + fase1) + 0.4 * Math.sin(s * f2 + fase2));
      return [m.x + m.nx * onda + p.dx, m.y + m.ny * onda + p.dy];
    }
    c.lineCap = 'round';
    for (var j = 0; j < p.cuenta; j++) {
      var t = j / p.cuenta;
      var presion = (0.55 + 0.45 * Math.sin(Math.PI * t)) * (0.82 + 0.18 * (0.5 + 0.5 * Math.sin(2 * Math.PI * 1.3 * t + faseP)));
      presion = clamp(presion, 0.2, 1);
      var ancho = p.grosor * presion, alfa = p.alfa * (0.75 + 0.25 * presion);
      if (p.puntas) { var f = Math.min(1, Math.min(t / 0.08, (1 - t) / 0.08)); ancho *= 0.3 + 0.7 * f; }
      if (r() < 0.18) alfa *= 0.25 + 0.35 * r();
      var a = punto(p.k0 + j), b = punto(p.k0 + j + 1);
      c.beginPath(); c.moveTo(a[0], a[1]); c.lineTo(b[0], b[1]);
      c.lineWidth = ancho; c.strokeStyle = rgba(color, alfa); c.stroke();
    }
  }
  function trazoLapiz(c, fig, color, grosor, semilla) {
    var M = muestrear(fig, 1.5), n = M.ms.length, cerrado = fig.cerrado, r = azar(semilla);
    var inicio = cerrado ? Math.floor(r() * n) : 0;
    var cuentaFirme = cerrado ? Math.round(n * 1.06) : n - 1;
    var cuentaTercera = Math.round(n * 0.35);
    var inicioTercera = cerrado ? (inicio + Math.floor(n / 3)) % n : Math.floor(r() * Math.max(1, n - 1 - cuentaTercera));
    pasada(c, M, { semilla: semilla, amplitud: 0.6, grosor: grosor, alfa: 0.92, dx: 0, dy: 0, k0: inicio, cuenta: cuentaFirme, puntas: !cerrado }, color);
    pasada(c, M, { semilla: semilla + 1, amplitud: 1.3, grosor: grosor * 0.55, alfa: 0.5, dx: 0.6, dy: 0.5, k0: inicio, cuenta: cuentaFirme, puntas: !cerrado }, color);
    pasada(c, M, { semilla: semilla + 2, amplitud: 1.6, grosor: grosor * 0.4, alfa: 0.35, dx: -0.4, dy: 0.3, k0: inicioTercera, cuenta: cuentaTercera, puntas: !cerrado }, color);
  }
  function caja(fig) {
    var x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
    fig.pts.forEach(function (p) { x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); });
    return { x: x0, y: y0, w: x1 - x0, h: y1 - y0 };
  }
  function capaRayas(c, fig, color, tinta, o) {
    var r = azar(o.semilla), b = caja(fig);
    var cx = b.x + b.w / 2, cy = b.y + b.h / 2, radio = Math.sqrt(b.w * b.w + b.h * b.h) / 2;
    var nbx = -Math.sin(o.angulo), nby = Math.cos(o.angulo);
    var dentro = o.dentro || fig.dentro;
    c.lineCap = 'round';
    for (var s = -radio; s <= radio; s += o.separacion * (0.8 + 0.4 * r())) {
      var desvio = (r() * 2 - 1) * 3 * Math.PI / 180, a = o.angulo + desvio;
      var dx = Math.cos(a), dy = Math.sin(a), nx = -dy, ny = dx;
      var bx = cx + nbx * s, by = cy + nby * s;
      var tramos = [], ini = null;
      for (var t = -radio; t <= radio; t += 1) {
        var px = bx + dx * t, py = by + dy * t;
        var ok = dentro(px, py) && tinta(px, py) > o.minimo;
        if (ok && ini === null) ini = t;
        if (!ok && ini !== null) { tramos.push([ini, t]); ini = null; }
      }
      if (ini !== null) tramos.push([ini, radio]);
      for (var k = 0; k < tramos.length; k++) {
        var t0 = tramos[k][0] + r() * 2.5, t1 = tramos[k][1] - r() * 2.5;
        var curva = (r() * 2 - 1) * 0.7;
        if (t1 - t0 < 2) continue;
        var q = function (tt) {
          var u = clamp((tt - t0) / (t1 - t0), 0, 1), off = curva * Math.sin(Math.PI * u);
          return [bx + dx * tt + nx * off, by + dy * tt + ny * off];
        };
        for (var tt = t0; tt < t1; tt += 2) {
          var fin = Math.min(tt + 2, t1), mitad = (tt + fin) / 2;
          var u = clamp((mitad - t0) / (t1 - t0), 0, 1), presion = 0.35 + 0.65 * Math.sin(Math.PI * u);
          var qm = q(mitad), tn = tinta(qm[0], qm[1]);
          if (tn <= o.minimo) continue;
          var p0 = q(tt), p1 = q(fin);
          c.beginPath(); c.moveTo(p0[0], p0[1]); c.lineTo(p1[0], p1[1]);
          c.lineWidth = o.grosor * (0.6 + 0.5 * presion);
          c.strokeStyle = rgba(color, o.alfa * presion * tn * (0.7 + 0.3 * r()));
          c.stroke();
        }
      }
    }
  }
  function sombreado(c, fig, o) {
    var tinta = o.intensidad || function () { return 1; };
    var base = { angulo: o.angulo == null ? Math.PI / 4 : o.angulo, separacion: o.separacion || 2.4, grosor: o.grosor || 0.9,
      alfa: o.alfa == null ? 0.6 : o.alfa, minimo: 0, semilla: o.semilla, dentro: o.dentro };
    c.save();
    if (fig.path) { fig.path(c); c.clip(); }
    capaRayas(c, fig, o.color, tinta, base);
    if (o.cruzado) {
      capaRayas(c, fig, o.color, tinta, { angulo: base.angulo + 70 * Math.PI / 180, separacion: base.separacion * 1.4,
        grosor: base.grosor, alfa: base.alfa * 0.55, minimo: 0.6, semilla: o.semilla + 11, dentro: o.dentro });
    }
    c.restore();
  }
  function semillaDe(w, h) { return (Math.round(w) * 73 + Math.round(h) * 31) | 0; }

  /* Lo que es mucho dibujo se graba una vez (pintarConCache). */
  var cache = {};
  function grabado(clave, w, h, pad, dibujo) {
    var k = clave + '|' + idActual + '|' + w + 'x' + h + '|' + DPR;
    if (!cache[k]) {
      var cv = document.createElement('canvas');
      cv.width = Math.ceil((w + 2 * pad) * DPR); cv.height = Math.ceil((h + 2 * pad) * DPR);
      var c = cv.getContext('2d'); c.setTransform(DPR, 0, 0, DPR, pad * DPR, pad * DPR);
      dibujo(c);
      cache[k] = cv;
    }
    return cache[k];
  }

  /* ── Los datos de la demo: una semana real, con hoy de verdad ─────────── */
  var HABITOS = [
    { n: 'Beber agua', ritmo: 4, pasado: [1, 1, 1, 1, 0, 1], hecho: true },
    { n: 'Leer 20 minutos', ritmo: 3, pasado: [1, 0, 1, 1, 1, 0], hecho: false },
    { n: 'Salir a caminar', ritmo: 3, pasado: [0, 1, 1, 0, 1, 1], hecho: false },
    { n: 'Meditar', ritmo: 2, pasado: [1, 0, 0, 1, 0, 1], hecho: false }
  ];
  var ahora = new Date();
  var indiceHoy = (ahora.getDay() + 6) % 7; // lunes = 0
  var lunes = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate() - indiceHoy);
  var DIAS = ['LUN', 'MAR', 'MIÉ', 'JUE', 'VIE', 'SÁB', 'DOM'];
  function estadoDia(h, i) { // 'lleno' | 'vacio' | 'futuro'
    if (i > indiceHoy) return 'futuro';
    if (i === indiceHoy) return h.hecho ? 'lleno' : 'vacio';
    return h.pasado[(6 - indiceHoy + i) % 6] ? 'lleno' : 'vacio';
  }
  function hechos() { return HABITOS.filter(function (h) { return h.hecho; }).length; }
  function avance() { return hechos() / HABITOS.length; }

  /* ══════════════════════════════════════════════════════════════════════
     FONDOS
     ══════════════════════════════════════════════════════════════════════ */
  var fondo = document.getElementById('fondo'), grano = document.getElementById('grano');
  var VW = 0, VH = 0;
  var estrellas = [], burbujas = [];
  var gestoFondo = { desde: null, t: 1, inicio: 0 };

  function generarCielo() {
    var r = azar(7), n = Math.round(clamp(44 * (VW * VH) / (390 * 844), 44, 170));
    var radios = [0.8, 0.8, 1.0, 1.0, 1.1, 1.4, 1.7];
    estrellas = [];
    for (var i = 0; i < n; i++) estrellas.push({ x: r(), y: r(), r: radios[Math.floor(r() * radios.length)], o: 0.18 + r() * (0.85 - 0.18) });
    var rb = azar(23), nb = Math.round(clamp(110 * (VW * VH) / (390 * 844), 110, 300));
    burbujas = [];
    for (var j = 0; j < nb; j++) burbujas.push({ x: rb(), y: rb(), r: 5 + rb() * 13, i: j });
  }

  function pintarCielo(c) {
    var t = T(), lado = Math.min(VW, VH);
    function nebulosa(ax, ay, radio, color, alfa, stop) {
      var cx = VW / 2 + ax * VW / 2, cy = VH / 2 + ay * VH / 2;
      var g = c.createRadialGradient(cx, cy, 0, cx, cy, radio * lado);
      g.addColorStop(0, rgba(color, alfa)); g.addColorStop(stop, rgba(color, 0)); g.addColorStop(1, rgba(color, 0));
      c.fillStyle = g; c.fillRect(0, 0, VW, VH);
    }
    nebulosa(0.44, -0.84, 1.3, t.primary, 0.13, 0.62);
    nebulosa(-0.64, 0.64, 1.0, '#4060AA', 0.16, 0.68);
    estrellas.forEach(function (e) {
      c.fillStyle = rgba(t.text, e.o);
      c.beginPath(); c.arc(e.x * VW, e.y * VH, e.r, 0, Math.PI * 2); c.fill();
    });
  }

  /* El perfil de la ciudad, escrito a mano en fondo_ciudad.dart. */
  var BLOQUES = [[-0.02, 0.1007, 0.6918], [0.0998, 0.1019, 0.6555], [0.2168, 0.0766, 0.6583], [0.3089, 0.1314, 0.3659],
    [0.4519, 0.0682, 0.8668], [0.5364, 0.0638, 0.9875], [0.6198, 0.1189, 0.7309], [0.7485, 0.0614, 0.6699],
    [0.8186, 0.0771, 0.4694], [0.9041, 0.1018, 0.6084]];
  function ruido(b, col, fil) {
    var h = Math.imul(b, 73856093) ^ Math.imul(col, 19349663) ^ Math.imul(fil, 83492791);
    h = h & 0x7FFFFFFF; h = Math.imul(h ^ (h >> 13), 1274126177) & 0x7FFFFFFF;
    return (h % 100000) / 100000;
  }
  function parpadeo(v) { return v < 0.25 ? v / 0.25 : v < 0.45 ? 0.15 : 1; }
  function pintarCiudad(c, fraccion, desde, gesto) {
    var t = T(), banda = VH * 0.60, suelo = VH;
    // En pantalla ancha el perfil se repite: una ciudad del ancho de un móvil
    // estirada a 1400 px dejaría de ser una ciudad.
    var tile = Math.min(VW, 520), tiles = Math.ceil(VW / tile) + 1;
    for (var tIdx = 0; tIdx < tiles; tIdx++) {
      for (var i = 0; i < BLOQUES.length; i++) {
        var b = BLOQUES[i], izq = tIdx * tile + b[0] * tile, ancho = b[1] * tile, alto = b[2] * banda, arriba = suelo - alto;
        var bid = tIdx * 10 + i;
        c.fillStyle = t.surface2; c.fillRect(izq, arriba, ancho, alto);
        c.strokeStyle = rgba(i % 2 === 0 ? t.primary : t.streak, 0.45); c.lineWidth = 1.2;
        c.beginPath(); c.moveTo(izq, arriba); c.lineTo(izq + ancho, arriba); c.stroke();
        var col = 0;
        for (var vx = izq + 5; vx + 4 <= izq + ancho - 5; vx += 10) {
          var fil = 0;
          for (var vy = arriba + 8; vy + 6 <= suelo; vy += 14) {
            var rr = ruido(bid, col, fil), enc = rr < fraccion;
            var nueva = enc && desde != null && rr >= desde, luz = nueva ? parpadeo(gesto) : 1;
            if (enc && luz > 0.5) {
              c.save(); c.shadowColor = rgba('#FFE347', 0.10 * 2.4); c.shadowBlur = 8;
              c.fillStyle = rgba('#FFE347', 0.10); c.fillRect(vx - 3, vy - 3, 10, 12); c.restore();
            }
            c.fillStyle = rgba('#FFE347', enc ? 0.80 * (luz < 1 ? Math.max(luz, 0.05) : 1) : 0.04);
            c.fillRect(vx, vy, 4, 6);
            fil++;
          }
          col++;
        }
      }
    }
  }

  function burbujaLapiz(b) {
    var t = T(), r = b.r;
    return grabado('burbuja' + b.i + '_' + r.toFixed(2), r * 2, r * 2, 3, function (c) {
      var fig = circulo(r, r, r), bx = r - r * 0.34, by = r - r * 0.34;
      sombreado(c, fig, { color: t.trazo, separacion: 2.2, alfa: 0.9, semilla: 7 * b.i + 3,
        intensidad: function (x, y) { return clamp((Math.hypot(x - bx, y - by) - r * 0.28) / r, 0, 1); } });
      trazoLapiz(c, fig, t.trazo, 1.4, 7 * b.i + 5);
    });
  }
  function pintarBurbujas(c, visibles, desde, gesto) {
    for (var i = 0; i < visibles && i < burbujas.length; i++) {
      var b = burbujas[i];
      var esc = desde != null && i >= desde ? easeOutBack(gesto) : 1;
      if (esc <= 0) continue;
      var img = burbujaLapiz(b), lado = b.r * 2 + 6;
      c.save();
      c.globalAlpha = 0.15; // tintaMaximaBurbuja
      c.translate(b.x * VW, b.y * VH); c.scale(esc, esc);
      c.drawImage(img, -lado / 2, -lado / 2, lado, lado);
      c.restore();
    }
  }
  function visiblesBurbujas(av) { return Math.round(burbujas.length * av * 0.8); }

  function pintarFondo() {
    var c = preparar(fondo, VW, VH), t = T();
    c.fillStyle = t.bg; c.fillRect(0, 0, VW, VH);
    var g = gestoFondo;
    if (idActual === 'profundidad') pintarCielo(c);
    else if (idActual === 'neotokyo') pintarCiudad(c, avance() * 0.55, g.desde, g.t);
    else pintarBurbujas(c, visiblesBurbujas(avance()), g.desde, g.t);
  }
  /* Al marcar: ventanas que parpadean (600 ms) o burbujas que rebotan (320). */
  function animarFondo(desde) {
    if (quieto || idActual === 'profundidad') { gestoFondo = { desde: null, t: 1 }; pintarFondo(); return; }
    var dur = idActual === 'neotokyo' ? 600 : 320, t0 = performance.now();
    gestoFondo = { desde: desde, t: 0 };
    (function paso(ts) {
      gestoFondo.t = clamp((ts - t0) / dur, 0, 1);
      pintarFondo();
      if (gestoFondo.t < 1) requestAnimationFrame(paso); else gestoFondo.desde = null;
    })(t0);
  }

  function pintarGrano() {
    if (idActual !== 'dulce') return;
    var c = preparar(grano, VW, VH);
    var tile = document.createElement('canvas'); tile.width = tile.height = 200;
    var tc = tile.getContext('2d'), r = azar(0x4D6F);
    for (var y = 0; y < 200; y += 10) for (var x = 0; x < 200; x += 10) for (var k = 0; k < 3; k++) {
      var px = x + r() * 10, py = y + r() * 10, a = r() * 0.05;
      tc.fillStyle = tc.strokeStyle = rgba('#786046', a);
      if (Math.floor(r() * 40) === 0) {
        var largo = 2 + r(), caida = r() * 0.6 - 0.3;
        tc.lineWidth = 1; tc.beginPath(); tc.moveTo(px, py); tc.lineTo(px + largo, py + caida); tc.stroke();
      } else tc.fillRect(px, py, 1, 1);
    }
    c.setTransform(1, 0, 0, 1, 0, 0);
    c.fillStyle = c.createPattern(tile, 'repeat'); c.fillRect(0, 0, grano.width, grano.height);
  }

  /* ══════════════════════════════════════════════════════════════════════
     LA CONSTELACIÓN DEL DÍA (Profundidad) — la Cruz del Sur, cuatro hábitos
     ══════════════════════════════════════════════════════════════════════ */
  var CRUZ = { puntos: [[0.50, 0.14], [0.54, 0.88], [0.24, 0.56], [0.80, 0.50]], segmentos: [[0, 1], [2, 3]] };
  var cielo = document.getElementById('cielo');
  var vuelos = {}, apagados = {}, realce = 0;

  function cuadroConstelacion(w, h) {
    var dx = w * 0.14, dy = h * 0.30, dw = w * 0.72, dh = h * 0.44, lado = Math.min(dw, dh);
    return { x: dx + dw / 2 - lado / 2, y: dy + dh / 2 - lado / 2, lado: lado };
  }
  function astroide(c, cx, cy, r) {
    c.beginPath();
    for (var k = 0; k <= 64; k++) {
      var a = 2 * Math.PI * k / 64, cs = Math.cos(a), sn = Math.sin(a);
      var x = cx + r * cs * cs * cs, y = cy + r * sn * sn * sn;
      if (k) c.lineTo(x, y); else c.moveTo(x, y);
    }
    c.closePath();
  }
  function estrella(c, x, y, esc, op, rl) {
    var t = T(), radioHalo = 17 * esc * (1 + 0.3 * rl), int = op * (1 + 0.5 * rl);
    var g = c.createRadialGradient(x, y, 0, x, y, radioHalo);
    g.addColorStop(0, rgba(t.streak, 0.34 * int)); g.addColorStop(0.35, rgba(t.streak, 0.12 * int)); g.addColorStop(1, rgba(t.streak, 0));
    c.fillStyle = g; c.beginPath(); c.arc(x, y, radioHalo, 0, Math.PI * 2); c.fill();
    c.fillStyle = rgba(t.streak, 0.35 * op);
    for (var k = 0; k < 4; k++) {
      var a = Math.PI / 4 + k * Math.PI / 2, dx = Math.cos(a), dy = Math.sin(a), px = -dy * 0.5 * esc, py = dx * 0.5 * esc;
      c.beginPath(); c.moveTo(x + px, y + py); c.lineTo(x + dx * 5 * esc, y + dy * 5 * esc); c.lineTo(x - px, y - py); c.closePath(); c.fill();
    }
    astroide(c, x, y, 9.5 * esc); c.fillStyle = rgba(t.streak, 0.55 * op); c.fill();
    astroide(c, x, y, 7 * esc); c.fillStyle = rgba(t.text, 0.95 * op); c.fill();
    c.fillStyle = rgba(t.text, op); c.beginPath(); c.arc(x, y, 2 * esc, 0, Math.PI * 2); c.fill();
  }
  function pintarConstelacion(c, w, h, completas, opciones) {
    var t = T(), q = cuadroConstelacion(w, h), rl = opciones.realce || 0;
    var vu = opciones.vuelos || {}, ap = opciones.apagados || {};
    function sit(p) { return [q.x + p[0] * q.lado, q.y + p[1] * q.lado]; }
    var enc = clamp(completas, 0, CRUZ.puntos.length);
    function luz(i) {
      if (i < enc) { var v = vu[i]; return v == null || v >= 1 ? 1 : 0; }
      var a = ap[i]; return a == null ? 0 : 1 - easeOut(a);
    }
    // El día cerrado apaga un poco lo de debajo. En la app es la pantalla
    // entera; aquí, un velo con los bordes difuminados para que no se vea
    // la caja del dibujo.
    if (rl > 0 && opciones.velo) {
      var cxv = w / 2, cyv = q.y + q.lado / 2, rv = Math.max(w, h) * 0.62;
      var gv = c.createRadialGradient(cxv, cyv, 0, cxv, cyv, rv);
      gv.addColorStop(0, 'rgba(0,0,0,' + (0.25 * rl) + ')'); gv.addColorStop(0.6, 'rgba(0,0,0,' + (0.2 * rl) + ')'); gv.addColorStop(1, 'rgba(0,0,0,0)');
      c.fillStyle = gv; c.fillRect(0, 0, w, h);
    }
    c.save();
    c.globalCompositeOperation = 'lighter'; // la luz se suma, como BlendMode.plus
    CRUZ.segmentos.forEach(function (sg) {
      var pa = sit(CRUZ.puntos[sg[0]]), pb = sit(CRUZ.puntos[sg[1]]), la = luz(sg[0]), lb = luz(sg[1]);
      c.lineWidth = 1.2; c.lineCap = 'round';
      if (la > 0 && lb > 0) {
        c.strokeStyle = rgba(t.streak, (0.38 + 0.22 * rl) * Math.min(la, lb));
        c.beginPath(); c.moveTo(pa[0], pa[1]); c.lineTo(pb[0], pb[1]); c.stroke();
      } else if (la > 0 || lb > 0) {
        var desde = la > 0 ? pa : pb, hacia = la > 0 ? pb : pa, lc = Math.max(la, lb);
        var g = c.createLinearGradient(desde[0], desde[1], hacia[0], hacia[1]);
        g.addColorStop(0, rgba(t.streak, 0.22 * lc)); g.addColorStop(0.55, rgba(t.streak, 0)); g.addColorStop(1, rgba(t.streak, 0));
        c.strokeStyle = g; c.beginPath(); c.moveTo(desde[0], desde[1]); c.lineTo(hacia[0], hacia[1]); c.stroke();
      }
    });
    CRUZ.puntos.forEach(function (p, i) {
      var s = sit(p), v = i < enc ? vu[i] : null;
      if (i < enc && (v == null || v >= 1)) estrella(c, s[0], s[1], 1, 1, rl);
      else {
        c.fillStyle = rgba(t.text, 0.16); c.beginPath(); c.arc(s[0], s[1], 1.6, 0, Math.PI * 2); c.fill();
        if (v != null) vuelo(c, h, s, v, rl);
        else { var lq = luz(i); if (lq > 0) estrella(c, s[0], s[1], 1, lq, rl); }
      }
    });
    c.restore();
  }
  /* La estrella entra como fugaz: de fuera por la izquierda, en arco. */
  function vuelo(c, h, llegada, t, rl) {
    var tk = T(), salida = [-30, llegada[1] - h * 0.34];
    var rx = llegada[0] - salida[0], ry = llegada[1] - salida[1], largo = Math.hypot(rx, ry);
    var nx = ry / largo, ny = -rx / largo; if (ny > 0) { nx = -nx; ny = -ny; }
    var cx = (salida[0] + llegada[0]) / 2 + nx * largo * 0.18, cy = (salida[1] + llegada[1]) / 2 + ny * largo * 0.18;
    function punto(s) { var u = 1 - s; return [salida[0] * u * u + cx * 2 * u * s + llegada[0] * s * s, salida[1] * u * u + cy * 2 * u * s + llegada[1] * s * s]; }
    var av = easeOutCubic(t), estela = 0.22 * (1 - t);
    c.lineCap = 'round';
    for (var k = 0; k < 24; k++) {
      var s0 = Math.max(0, av - estela * k / 24), s1 = Math.max(0, av - estela * (k + 1) / 24), resto = 1 - k / 24;
      var a = punto(s0), b = punto(s1);
      c.lineWidth = 0.3 + 2.1 * resto; c.strokeStyle = rgba(tk.streak, 0.8 * resto);
      c.beginPath(); c.moveTo(a[0], a[1]); c.lineTo(b[0], b[1]); c.stroke();
    }
    var p = punto(av); estrella(c, p[0], p[1], 0.8 + 0.2 * av, 1, rl);
  }
  var animando = false;
  function pintarCieloApp() {
    // La figura va sobre la lista, no sobre la tira: en la app ocupa la banda
    // central de la pantalla, donde el texto va siempre en su fila.
    // Un canvas no se estira con left/right: el tamaño se le da a mano.
    var app = document.getElementById('app'), arriba = Math.max(0, lista.offsetTop - 16);
    cielo.style.top = arriba + 'px';
    var w = app.clientWidth + 64, h = app.clientHeight - arriba, c = preparar(cielo, w, h);
    if (idActual !== 'profundidad') return;
    var v = {}, a = {};
    Object.keys(vuelos).forEach(function (k) { v[k] = vuelos[k].t; });
    Object.keys(apagados).forEach(function (k) { a[k] = apagados[k].t; });
    pintarConstelacion(c, w, h, hechos(), { vuelos: v, apagados: a, realce: realce, velo: true });
  }
  function bucleCielo() {
    if (animando) return;
    animando = true;
    (function paso(ts) {
      var vivos = false;
      [vuelos, apagados].forEach(function (m) {
        Object.keys(m).forEach(function (k) {
          var e = m[k]; e.t = clamp((ts - e.t0) / e.dur, 0, 1);
          if (e.t < 1) vivos = true; else if (m === apagados) delete m[k];
        });
      });
      pintarCieloApp();
      if (vivos) requestAnimationFrame(paso); else { animando = false; vuelos = {}; pintarCieloApp(); }
    })(performance.now());
  }

  /* ══════════════════════════════════════════════════════════════════════
     LA PANTALLA HOY
     ══════════════════════════════════════════════════════════════════════ */
  var lista = document.getElementById('lista');
  var compl = document.createElement('p');
  compl.className = 'compl'; compl.textContent = 'COMPLETADOS'; compl.setAttribute('aria-hidden', 'true');

  var CHEV = '<svg class="chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg>';

  /* ── El check (check_circular.dart): 550 ms, deshacer 320 ms ─────────── */
  function pintarCheck(cv, v) {
    var t = T(), s = 44, c = preparar(cv, s, s);
    var relleno = intervalo(v, 0, 0.35, easeOut), trazo = intervalo(v, 0.35, 1, easeOutCubic);
    var u = intervalo(v, 0.15, 0.75), pop = u < 0.4 ? 1 + 0.18 * (u / 0.4) : 1.18 + (1 - 1.18) * elasticOut((u - 0.4) / 0.6);
    cv.style.transform = 'scale(' + pop + ')';
    var cfg = t.forma === 'glass' ? { aro: 3.4, check: 0.09, glow: 5, base: true, insinuado: true }
      : t.forma === 'chamfer' ? { aro: 2.0, check: 0.10, base: true } : { aro: 3.0, check: 0.10 };
    var cx = s / 2, cy = s / 2, radio = s / 2 - cfg.aro / 2;
    function figura(esc) {
      var r = radio * esc;
      if (t.forma !== 'chamfer') { c.beginPath(); c.arc(cx, cy, r, 0, Math.PI * 2); c.closePath(); return; }
      var ch = clamp(t.chaflan * esc, 0, r);
      c.beginPath(); c.moveTo(cx - r + ch, cy - r); c.lineTo(cx + r - ch, cy - r); c.lineTo(cx + r, cy - r + ch);
      c.lineTo(cx + r, cy + r - ch); c.lineTo(cx + r - ch, cy + r); c.lineTo(cx - r + ch, cy + r);
      c.lineTo(cx - r, cy + r - ch); c.lineTo(cx - r, cy - r + ch); c.closePath();
    }
    function camino(p) {
      var p1 = [s * 0.28, s * 0.53], p2 = [s * 0.44, s * 0.68], p3 = [s * 0.73, s * 0.35];
      var a = clamp(p * 2, 0, 1);
      c.beginPath(); c.moveTo(p1[0], p1[1]); c.lineTo(p1[0] + (p2[0] - p1[0]) * a, p1[1] + (p2[1] - p1[1]) * a);
      if (p > 0.5) { var b = clamp((p - 0.5) * 2, 0, 1); c.lineTo(p2[0] + (p3[0] - p2[0]) * b, p2[1] + (p3[1] - p2[1]) * b); }
    }
    if (cfg.base) { figura(1); c.fillStyle = t.surface; c.fill(); }
    if (t.forma === 'pill') trazoLapiz(c, circulo(cx, cy, radio), t.aro, 2.2, 4444);
    else { figura(1); c.lineWidth = cfg.aro; c.strokeStyle = t.aro; c.stroke(); }
    if (cfg.insinuado) { camino(1); c.lineWidth = s * cfg.check; c.lineCap = 'round'; c.lineJoin = 'round'; c.strokeStyle = rgba(t.aro, 0.15); c.stroke(); }
    if (relleno > 0) {
      if (t.forma === 'pill') {
        var fig = circulo(cx, cy, radio * relleno);
        sombreado(c, fig, { color: t.success, separacion: 1.6, alfa: 0.95, cruzado: true, semilla: 4444 });
        trazoLapiz(c, fig, t.success, 1.8, 4457);
      } else {
        if (cfg.glow) { c.save(); c.shadowColor = rgba(t.success, 0.55); c.shadowBlur = cfg.glow * 2 * relleno; figura(relleno); c.fillStyle = t.success; c.fill(); c.restore(); }
        figura(relleno); c.fillStyle = t.success; c.fill();
      }
    }
    if (trazo > 0) {
      camino(trazo); c.lineWidth = s * cfg.check;
      c.lineCap = t.forma === 'chamfer' ? 'butt' : 'round'; c.lineJoin = t.forma === 'chamfer' ? 'miter' : 'round';
      c.strokeStyle = t.forma === 'pill' ? t.text : '#FFFFFF'; c.stroke();
    }
  }
  function animarCheck(cv, desde, hasta, alFinal) {
    if (quieto) { pintarCheck(cv, hasta); if (alFinal) alFinal(); return; }
    // La identidad con la que se empezó: las tarjetas de «Identidades»
    // animan con la suya aunque la página lleve otra.
    var idCheck = idActual;
    var dur = hasta > desde ? 550 : 320, t0 = performance.now();
    (function paso(ts) {
      var p = clamp((ts - t0) / dur, 0, 1);
      conIdentidad(idCheck, 0, 0, function () { pintarCheck(cv, desde + (hasta - desde) * p); });
      if (p < 1) requestAnimationFrame(paso); else if (alFinal) alFinal();
    })(t0);
  }

  /* ── Indicador de ritmo (indicador_nivel.dart) ───────────────────────── */
  function pintarRitmo(cv, nivel, tam) {
    var t = T(), hueco = tam / 5, w = tam * 5 + hueco * 4, c = preparar(cv, w + 4, tam + 4);
    c.translate(2, 2);
    var grosor = Math.max(1, tam * 0.08);
    for (var i = 0; i < 5; i++) {
      var x = i * (tam + hueco), llena = i < nivel;
      if (t.forma === 'pill') {
        var d = grosor / 2, lado = tam - grosor, r = lado / 2 * (0.6 + 0.1 * i);
        var cx = x + d + lado / 2, cy = d + lado - r, fig = circulo(cx, cy, r), sem = 11 * i + Math.round(tam);
        if (llena) {
          var bx = cx - r * 0.34, by = cy - r * 0.34;
          sombreado(c, fig, { color: t.points, separacion: Math.max(1.2, tam * 0.08), grosor: Math.max(0.6, tam * 0.045), alfa: 0.95, cruzado: true, semilla: sem,
            intensidad: function (px, py) { return clamp((Math.hypot(px - bx, py - by) - r * 0.3) / r * 1.6, 0, 1); } });
        }
        trazoLapiz(c, fig, llena ? t.points : t.aro, grosor, sem + 5);
        continue;
      }
      var g = llena ? 0 : grosor / 2, bxx = x + g, byy = g, lad = tam - 2 * g;
      if (t.forma === 'glass') astroide(c, bxx + lad / 2, byy + lad / 2, lad / 2);
      else {
        var an = lad * 0.6, l0 = bxx + (lad - an) / 2, ch = an * 0.25;
        c.beginPath(); c.moveTo(l0 + ch, byy); c.lineTo(l0 + an - ch, byy); c.lineTo(l0 + an, byy + ch); c.lineTo(l0 + an, byy + lad - ch);
        c.lineTo(l0 + an - ch, byy + lad); c.lineTo(l0 + ch, byy + lad); c.lineTo(l0, byy + lad - ch); c.lineTo(l0, byy + ch); c.closePath();
      }
      if (llena) { c.fillStyle = t.points; c.fill(); }
      else { c.lineWidth = grosor; c.lineJoin = 'round'; c.strokeStyle = t.aro; c.stroke(); }
    }
  }

  /* ── Superficie de una fila (superficie_identidad.dart) ──────────────── */
  function pintarSuperficie(cv, w, h, estado, radio, prota) {
    var t = T(), pad = 14;
    cv.style.left = cv.style.top = -pad + 'px';
    var c = preparar(cv, w + 2 * pad, h + 2 * pad);
    c.translate(pad, pad);
    cv.classList.toggle('late', t.forma === 'chamfer' && estado === 'late' && !quieto);
    // La protagonista de Profundidad: surfaceAlta plana y un filo claro.
    if (t.forma === 'glass' && prota) {
      c.beginPath(); if (c.roundRect) c.roundRect(0.5, 0.5, w - 1, h - 1, 24); else c.rect(0.5, 0.5, w - 1, h - 1);
      c.fillStyle = t.surfaceAlta; c.fill(); c.lineWidth = 1; c.strokeStyle = 'rgba(255,255,255,0.08)'; c.stroke();
      return;
    }
    if (t.forma === 'glass') return; // la fila de Profundidad no lleva superficie
    if (t.forma === 'chamfer') {
      var ch = Math.min(t.chaflan, h / 2), o = 0.6;
      c.beginPath(); c.moveTo(o + ch, o); c.lineTo(w - o - ch, o); c.lineTo(w - o, o + ch); c.lineTo(w - o, h - o - ch);
      c.lineTo(w - o - ch, h - o); c.lineTo(o + ch, h - o); c.lineTo(o, h - o - ch); c.lineTo(o, o + ch); c.closePath();
      if (prota) { c.fillStyle = t.surface; c.fill(); }
      c.lineWidth = 1.2; c.strokeStyle = rgba(t.primary, estado === 'fijo' ? 0.7 : 1); c.stroke();
      if (estado === 'late') cv.style.animationDelay = -(Date.now() % 2400) + 'ms';
      return;
    }
    // Dulce: la sombra a rayas desplazada (7, 9), el papel y el filo a lápiz.
    var img = grabado('sup' + radio, w, h, pad, function (k) {
      var rr = rrect(0, 0, w, h, radio), des = rrect(7, 9, w, h, radio);
      sombreado(k, { pts: des.pts, cerrado: true, dentro: function (x, y) { return des.dentro(x, y) && !rr.dentro(x, y); } }, {
        color: t.successText, separacion: 2.1, alfa: 0.55, grosor: 0.9, cruzado: true, semilla: semillaDe(w, h),
        dentro: function (x, y) { return des.dentro(x, y) && !rr.dentro(x, y); },
        intensidad: function (x, y) { return Math.max(0.15, 1 - Math.max(Math.max(0, x - w), Math.max(0, y - h)) / 9); } });
      rr.path(k); k.fillStyle = t.surface; k.fill();
      trazoLapiz(k, rrect(0.8, 0.8, w - 1.6, h - 1.6, radio), t.trazo, 1.6, semillaDe(w, h));
    });
    c.drawImage(img, -pad, -pad, w + 2 * pad, h + 2 * pad);
  }

  /* El chip de Dulce, a lápiz y sin relleno. */
  function pintarChipLapiz(chip) {
    var cv = chip.querySelector('canvas');
    if (idActual !== 'dulce') { if (cv) cv.remove(); return; }
    if (!cv) { cv = document.createElement('canvas'); cv.className = 'fila__deco'; cv.setAttribute('aria-hidden', 'true'); chip.appendChild(cv); }
    var w = chip.offsetWidth, h = chip.offsetHeight, pad = 3, t = T();
    cv.style.left = cv.style.top = -pad + 'px';
    var c = preparar(cv, w + 2 * pad, h + 2 * pad); c.translate(pad, pad);
    trazoLapiz(c, rrect(0.6, 0.6, w - 1.2, h - 1.2, 999), t.trazo, 1.1, semillaDe(w, h));
  }

  /* ── El mini-mapa de la semana ───────────────────────────────────────── */
  function pintarMapa(el, h) {
    var t = T();
    el.innerHTML = '';
    for (var i = 0; i < 7; i++) {
      var est = estadoDia(h, i), esHoy = i === indiceHoy, celda = document.createElement('i');
      if (idActual === 'dulce') {
        var cv = document.createElement('canvas'); celda.appendChild(cv);
        var c = preparar(cv, 22, 22); c.translate(3, 3);
        if (est === 'futuro') { c.fillStyle = t.inactivo; c.beginPath(); c.arc(8, 8, 3, 0, Math.PI * 2); c.fill(); }
        else {
          var fig = circulo(8, 8, 7.2);
          if (est === 'lleno') sombreado(c, fig, { color: t.success, separacion: 2.6, alfa: 0.7, grosor: 1, semilla: 30 + i });
          var lado = esHoy ? [t.trazo, 1.5] : est === 'vacio' ? [t.aro, 1.2] : null;
          if (lado) trazoLapiz(c, fig, lado[0], lado[1], 90 + i);
        }
        cv.style.position = 'absolute'; cv.style.left = cv.style.top = '-3px';
      } else {
        var d = document.createElement('span');
        d.className = 'cel ' + (est === 'futuro' ? 'desc' : est === 'lleno' ? 'llena' : 'vacia') + (esHoy && est !== 'futuro' ? ' hoy' : '');
        celda.appendChild(d);
      }
      el.appendChild(celda);
    }
  }

  /* ── Las filas ───────────────────────────────────────────────────────── */
  function construirFila(h, alTocar) {
    var f = document.createElement('div');
    f.className = 'fila';
    f.innerHTML =
      '<canvas class="fila__deco" aria-hidden="true"></canvas>' +
      '<div class="fila__i"><span class="fila__n">' + h.n + '</span>' +
      '<div class="fila__m"><span class="chip"><span class="chip__t"></span></span><canvas class="ritmoFila" aria-hidden="true"></canvas></div>' +
      '<div class="fila__p" aria-hidden="true"><span class="mapa"></span><span class="detalle">Ver detalle</span>' + CHEV + '</div></div>' +
      '<button class="check" type="button"><canvas aria-hidden="true"></canvas></button>';
    h.el = f;
    h.check = f.querySelector('.check');
    h.cv = f.querySelector('.check canvas');
    h.v = h.hecho ? 1 : 0;
    h.check.addEventListener('click', function () { alTocar(h); });
    return f;
  }
  HABITOS.forEach(function (h) { construirFila(h, alternar); });

  function pintarFila(h) {
    var f = h.el;
    f.classList.toggle('hecha', h.hecho);
    f.querySelector('.chip__t').textContent = 'Diario · ' + (h.hecho ? 1 : 0) + '/1';
    h.check.setAttribute('aria-pressed', h.hecho ? 'true' : 'false');
    h.check.setAttribute('aria-label', (h.hecho ? 'Deshacer ' : 'Completar ') + h.n);
    pintarRitmo(f.querySelector('.ritmoFila'), h.ritmo, idActual === 'dulce' ? 14 : 11);
    pintarMapa(f.querySelector('.mapa'), h);
    pintarChipLapiz(f.querySelector('.chip'));
    pintarSuperficie(f.querySelector('.fila__deco'), f.offsetWidth, f.offsetHeight, h.hecho ? 'encendido' : 'late', 20);
    pintarCheck(h.cv, h.v);
  }

  /* Pendientes arriba y «COMPLETADOS» debajo, como en la app. El paso de
     una fila a la otra sección se anima (FLIP) con la curva de la identidad. */
  function ordenar(animar) {
    var antes = {};
    if (animar) HABITOS.forEach(function (h) { antes[h.n] = h.el.getBoundingClientRect().top; });
    var pend = HABITOS.filter(function (h) { return !h.hecho; }), hech = HABITOS.filter(function (h) { return h.hecho; });
    pend.forEach(function (h) { lista.appendChild(h.el); });
    lista.appendChild(compl);
    compl.hidden = hech.length === 0;
    hech.forEach(function (h) { lista.appendChild(h.el); });
    document.getElementById('cuenta').textContent = hech.length + ' de ' + HABITOS.length;
    if (!animar || quieto) return;
    var t = T(), dur = t.transicion * 2;
    HABITOS.forEach(function (h) {
      var dy = antes[h.n] - h.el.getBoundingClientRect().top;
      if (!dy) return;
      h.el.style.transition = 'none';
      h.el.style.transform = 'translateY(' + dy + 'px)';
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          h.el.style.transition = 'transform ' + dur + 'ms cubic-bezier(' + t.curva.join(',') + '), opacity .4s ease';
          h.el.style.transform = '';
        });
      });
    });
  }

  /* ── Tira de la semana ───────────────────────────────────────────────── */
  var tira = document.getElementById('tira');
  var FL_IZQ = '<span class="tira__fl"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg></span>';
  var FL_DER = '<span class="tira__fl"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 18 6-6-6-6"/></svg></span>';
  function avanceDia(i) {
    if (i > indiceHoy) return null;
    return HABITOS.filter(function (h) { return estadoDia(h, i) === 'lleno'; }).length / HABITOS.length;
  }
  /* La tira de siete días (tira_semana.dart). `avanceDe(i)` da lo hecho ese
     día de 0 a 1, o null si no se espera nada; `elegido` es el día marcado. */
  function pintarTira(cont, desde, avanceDe, elegido, flechas) {
    var t = T(), html = flechas ? FL_IZQ : '';
    for (var i = 0; i < 7; i++) {
      var d = new Date(desde.getFullYear(), desde.getMonth(), desde.getDate() + i);
      html += '<div class="dia"><span class="dia__n">' + DIAS[i] + '</span><span class="dia__c' + (i === elegido ? ' sel' : '') +
        '"><canvas aria-hidden="true"></canvas><span style="position:relative">' + d.getDate() + '</span></span><canvas class="dia__b"></canvas></div>';
    }
    cont.innerHTML = html + (flechas ? FL_DER : '');
    [].forEach.call(cont.querySelectorAll('.dia'), function (el, i) {
      var sel = i === elegido, cv = el.querySelector('.dia__c canvas'), pad = 8;
      cv.style.position = 'absolute'; cv.style.left = cv.style.top = -pad + 'px';
      var c = preparar(cv, 32 + 2 * pad, 32 + 2 * pad); c.translate(pad, pad);
      var color = sel ? rgba(t.primary, 0.18) : t.inactivo;
      if (t.forma === 'pill') {
        var fig = circulo(16, 16, 15);
        if (sel) sombreado(c, fig, { color: t.primary, separacion: 2.6, alfa: 0.7, grosor: 1, semilla: 70 });
        else { fig.path(c); c.fillStyle = t.inactivo; c.fill(); }
        if (sel) trazoLapiz(c, fig, t.text, 2, 71);
      } else {
        var r = t.forma === 'glass' ? 5 : 1;
        c.save();
        if (sel) { c.shadowColor = color; c.shadowBlur = t.forma === 'glass' ? 6 : 10; }
        c.fillStyle = color; c.beginPath(); if (c.roundRect) c.roundRect(0, 0, 32, 32, r); else c.rect(0, 0, 32, 32); c.fill();
        c.restore();
        if (sel) { c.lineWidth = 2; c.strokeStyle = t.text; c.beginPath(); if (c.roundRect) c.roundRect(1, 1, 30, 30, r); else c.rect(1, 1, 30, 30); c.stroke(); }
      }
      var av = avanceDe(i), bc = preparar(el.querySelector('.dia__b'), 14, 4);
      if (av == null) { bc.fillStyle = t.inactivo; bc.beginPath(); bc.arc(7, 2, 2, 0, Math.PI * 2); bc.fill(); }
      else if (t.forma === 'pill') {
        trazoLapiz(bc, linea(0, 2, 14, 2), t.aro, 1.6, 12);
        if (av > 0) trazoLapiz(bc, linea(0, 2, 14 * av, 2), t.success, 2.4, 12);
      } else {
        bc.fillStyle = t.aro; bc.beginPath(); if (bc.roundRect) bc.roundRect(0, 0.5, 14, 3, 2); else bc.rect(0, 0.5, 14, 3); bc.fill();
        if (av > 0) { bc.fillStyle = t.success; bc.beginPath(); if (bc.roundRect) bc.roundRect(0, 0.5, 14 * av, 3, 2); else bc.rect(0, 0.5, 14 * av, 3); bc.fill(); }
      }
    });
  }

  /* ── Tarjeta del ritmo ───────────────────────────────────────────────── */
  var ritmo = document.getElementById('ritmo');
  function pintarTarjetaRitmo(tarjeta, nivel) {
    var deco = tarjeta.querySelector('.fila__deco');
    if (!deco) { deco = document.createElement('canvas'); deco.className = 'fila__deco'; deco.setAttribute('aria-hidden', 'true'); tarjeta.insertBefore(deco, tarjeta.firstChild); }
    pintarSuperficie(deco, tarjeta.offsetWidth, tarjeta.offsetHeight, 'fijo', 20);
    pintarRitmo(tarjeta.querySelector('.ritmo__i'), nivel, 20);
  }

  /* ── Nori ────────────────────────────────────────────────────────────── */
  var nori = document.getElementById('nori'), noriImg = document.getElementById('noriImg');
  function pintarNori(saltar) {
    var animo = hechos() === HABITOS.length ? 'exultante' : 'sonriente';
    var src = 'img/nori/cria_' + animo + '.webp';
    if (noriImg.getAttribute('src') !== src) noriImg.setAttribute('src', src);
    if (saltar && !quieto) { nori.classList.remove('salta'); void nori.offsetWidth; nori.classList.add('salta'); }
  }

  /* ── Marcar y deshacer ───────────────────────────────────────────────── */
  var cierreHecho = false;
  function alternar(h) {
    var antes = avance(), antesHechos = hechos();
    h.hecho = !h.hecho;
    var hasta = h.hecho ? 1 : 0;
    pintarFila(h);
    animarCheck(h.cv, h.v, hasta, function () { h.v = hasta; });
    h.v = hasta;
    // El fondo cambia con el avance del día, como publicarProgresoDia.
    if (idActual === 'neotokyo') animarFondo(h.hecho ? antes * 0.55 : null);
    else if (idActual === 'dulce') animarFondo(h.hecho ? visiblesBurbujas(antes) : null);
    pintarTiraHoy();
    if (h.hecho) {
      if (!quieto) vuelos[antesHechos] = { t: 0, t0: performance.now(), dur: 900 };
      pintarNori(true);
    } else {
      if (!quieto) apagados[antesHechos - 1] = { t: 0, t0: performance.now(), dur: 400 };
      realce = 0;
      pintarNori(false);
    }
    bucleCielo();
    // La fila cambia de sección cuando el check ha hecho su gesto.
    setTimeout(function () {
      ordenar(true);
      HABITOS.forEach(function (x) { if (x !== h) pintarFila(x); });
      pintarSuperficie(h.el.querySelector('.fila__deco'), h.el.offsetWidth, h.el.offsetHeight, h.hecho ? 'encendido' : 'late', 20);
      if (h.hecho && hechos() === HABITOS.length) setTimeout(cerrarDia, quieto ? 0 : 400);
    }, quieto ? 0 : (h.hecho ? 450 : 200));
  }

  /* ── El cierre del día (cierre_del_dia.dart), sólo en Profundidad ─────── */
  var cierre = document.getElementById('cierre'), cierreCv = document.getElementById('cierreCv');
  var cierreTextos = 0, cierreVelo = 0, cerrando = false;
  function pintarCierre() {
    var w = cierre.clientWidth, h = cierre.clientHeight, c = preparar(cierreCv, w, h), q = cuadroConstelacion(w, h);
    c.fillStyle = 'rgba(0,0,0,' + (0.75 * cierreVelo) + ')'; c.fillRect(0, 0, w, h);
    c.globalAlpha = cierreVelo;
    pintarConstelacion(c, w, h, HABITOS.length, { realce: cierreVelo, velo: false });
    c.globalAlpha = 1;
    var arriba = document.getElementById('cierreArriba'), abajo = document.getElementById('cierreAbajo');
    arriba.style.opacity = abajo.style.opacity = 1;
    arriba.style.bottom = (h - q.y + 16) + 'px';
    abajo.style.top = (q.y + q.lado + 16) + 'px';
    function alfa(a, b) { return cierreVelo * intervalo(cierreTextos, a, b, easeOut); }
    document.getElementById('cierreTit').style.opacity = alfa(0.21, 0.38);
    document.getElementById('cierreNom').style.opacity = alfa(0.48, 0.66);
    document.getElementById('cierreDes').style.opacity = alfa(0.83, 1.0);
  }
  function cerrarDia() {
    if (cierreHecho || idActual !== 'profundidad' || hechos() !== HABITOS.length) return;
    cierreHecho = true;
    cierre.classList.add('on');
    if (quieto) { cierreVelo = 1; cierreTextos = 1; pintarCierre(); return; }
    var t0 = performance.now();
    (function paso(ts) {
      cierreVelo = clamp((ts - t0) / 500, 0, 1);
      cierreTextos = clamp((ts - t0) / 2900, 0, 1);
      pintarCierre();
      if (cierreTextos < 1) requestAnimationFrame(paso);
    })(t0);
  }
  cierre.addEventListener('click', function () {
    if (cerrando || cierreTextos < 1) return;
    cerrando = true;
    function fin() { cierre.classList.remove('on'); cerrando = false; realce = 1; pintarCieloApp(); }
    if (quieto) { cierreVelo = 0; fin(); return; }
    var t0 = performance.now();
    (function paso(ts) {
      cierreVelo = 1 - clamp((ts - t0) / 500, 0, 1);
      pintarCierre();
      if (cierreVelo > 0) requestAnimationFrame(paso); else fin();
    })(t0);
  });

  /* ── Selector de identidad ───────────────────────────────────────────── */
  var botones = [].slice.call(document.querySelectorAll('.id'));
  function aplicar(id) {
    idActual = id;
    document.documentElement.setAttribute('data-id', id);
    document.querySelector('meta[name="theme-color"]').setAttribute('content', TOKENS[id].bg);
    botones.forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.ir === id ? 'true' : 'false'); });
    try { localStorage.setItem('norday-identidad', id); } catch (e) { /* sin almacenamiento, da igual */ }
    redibujar();
  }
  botones.forEach(function (b) { b.addEventListener('click', function () { aplicar(b.dataset.ir); }); });

  function pintarTiraHoy() { pintarTira(tira, lunes, avanceDia, indiceHoy, true); }

  /* ══════════════════════════════════════════════════════════════════════
     CÓMO FUNCIONA — tres pasos
     ══════════════════════════════════════════════════════════════════════ */
  // Paso 1: una fila suelta, que se marca y se desmarca sin tocar el resto.
  var DEMO = { n: 'Estirar 5 minutos', ritmo: 3, pasado: [1, 1, 0, 1, 1, 0], hecho: false };
  construirFila(DEMO, function (h) {
    h.hecho = !h.hecho;
    var hasta = h.hecho ? 1 : 0, desde = h.v;
    h.v = hasta;
    pintarFila(h);
    animarCheck(h.cv, desde, hasta);
    pintarSuperficie(h.el.querySelector('.fila__deco'), h.el.offsetWidth, h.el.offsetHeight, h.hecho ? 'encendido' : 'late', 20);
  });
  DEMO.el.classList.add('fila--sola');
  document.getElementById('pasoFila').appendChild(DEMO.el);

  // Paso 3: la semana pasada, con un día sin nada. La barrita se queda
  // vacía, sin rojo: la app nunca pinta un fallo.
  var SEMANA_PASADA = [1, 0.75, 0, 0.5, 1, 0.75, 1];
  var lunesPasado = new Date(lunes.getFullYear(), lunes.getMonth(), lunes.getDate() - 7);
  function pintarPasos() {
    pintarTira(document.getElementById('pasoTira'), lunesPasado, function (i) { return SEMANA_PASADA[i]; }, -1, false);
    pintarTarjetaRitmo(document.getElementById('pasoRitmo'), 2);
    pintarFila(DEMO);
  }

  // El fondo se aleja (0,55) en cuanto el hero deja de verse.
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(function (e) {
      fondo.classList.toggle('lejos', !e[0].isIntersecting);
    }, { threshold: 0.35 }).observe(document.querySelector('.hero'));
  }

  /* ══════════════════════════════════════════════════════════════════════
     LO QUE SE QUEDA — mes, valoración y vuelta
     ══════════════════════════════════════════════════════════════════════ */
  function decorar(el, prota) {
    var deco = el.querySelector(':scope > .fila__deco');
    if (!deco) { deco = document.createElement('canvas'); deco.className = 'fila__deco'; deco.setAttribute('aria-hidden', 'true'); el.insertBefore(deco, el.firstChild); }
    pintarSuperficie(deco, el.offsetWidth, el.offsetHeight, 'fijo', prota ? 26 : 20, prota);
  }

  /* Una celda del calendario del detalle (celdaHeatmap): llena o vacía. */
  function pintarCelda(celda, llena, lado) {
    var t = T();
    celda.innerHTML = '';
    if (idActual === 'dulce') {
      var cv = document.createElement('canvas'), pad = 3;
      cv.style.position = 'absolute'; cv.style.left = cv.style.top = -pad + 'px';
      celda.appendChild(cv);
      var c = preparar(cv, lado + 2 * pad, lado + 2 * pad); c.translate(pad, pad);
      var fig = circulo(lado / 2, lado / 2, lado / 2 - 0.8);
      if (llena) sombreado(c, fig, { color: t.success, separacion: 2.6, alfa: 0.7, grosor: 1, semilla: Math.round(lado) + 3 });
      else trazoLapiz(c, fig, t.aro, 1.2, Math.round(lado) + 9);
      return;
    }
    var d = document.createElement('span');
    d.className = 'cel ' + (llena ? 'llena' : 'vacia');
    celda.appendChild(d);
  }

  var MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
  // El mes pasado entero, con días marcados que salen de una semilla fija.
  var mesPasado = new Date(ahora.getFullYear(), ahora.getMonth() - 1, 1);
  var diasMesPasado = new Date(mesPasado.getFullYear(), mesPasado.getMonth() + 1, 0).getDate();
  var marcados = (function () { var r = azar(2026), a = []; for (var i = 0; i < diasMesPasado; i++) a.push(r() < 0.72); return a; })();

  function pintarMes() {
    document.getElementById('mesTit').textContent = MESES[mesPasado.getMonth()] + ' de ' + mesPasado.getFullYear();
    var g = document.getElementById('mesG'), hueco = (mesPasado.getDay() + 6) % 7;
    g.innerHTML = '';
    for (var k = 0; k < hueco; k++) g.appendChild(document.createElement('span'));
    for (var i = 0; i < diasMesPasado; i++) g.appendChild(document.createElement('i'));
    var lado = g.querySelector('i').offsetWidth;
    [].forEach.call(g.querySelectorAll('i'), function (el, i) { pintarCelda(el, marcados[i], lado); });
    [].forEach.call(document.querySelectorAll('.mes__li'), function (el) { pintarCelda(el, el.dataset.v === '1', 10); });
    document.getElementById('diasMes').textContent = marcados.filter(Boolean).length;
    decorar(document.getElementById('mes'));
    [].forEach.call(document.querySelectorAll('.cifra'), function (el) { decorar(el); });
  }

  /* La estrella de la valoración (estrella_valoracion.dart): Lucide en
     Profundidad y Neotokyo+, a lápiz en Dulce. Llena en pointsText. */
  var ESTRELLA = 'M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z';
  function estrellaLapiz(cv, marcada, tam) {
    var t = T(), c = preparar(cv, tam + 4, tam + 4); c.translate(2, 2);
    var grosor = Math.max(1, tam * 0.08), d = grosor / 2, r = (tam - grosor) / 2, cx = d + r, cy = d + r, pts = [];
    for (var k = 0; k < 10; k++) {
      var rr = k % 2 === 0 ? r : r * 0.45, a = -Math.PI / 2 + k * Math.PI / 5;
      pts.push([cx + rr * Math.cos(a), cy + rr * Math.sin(a)]);
    }
    var dens = [];
    for (var j = 0; j < 10; j++) { var p0 = pts[j], p1 = pts[(j + 1) % 10], n = Math.ceil(Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / 1.5); for (var q = 0; q < n; q++) dens.push([p0[0] + (p1[0] - p0[0]) * q / n, p0[1] + (p1[1] - p0[1]) * q / n]); }
    var path2 = new Path2D(); pts.forEach(function (p, i) { if (i) path2.lineTo(p[0], p[1]); else path2.moveTo(p[0], p[1]); }); path2.closePath();
    var fig = { pts: dens, cerrado: true, dentro: function (x, y) { return c.isPointInPath(path2, (x + 2) * DPR, (y + 2) * DPR); },
      path: function (k) { k.beginPath(); pts.forEach(function (p, i) { if (i) k.lineTo(p[0], p[1]); else k.moveTo(p[0], p[1]); }); k.closePath(); } };
    if (marcada) {
      var bx = cx - r * 0.34, by = cy - r * 0.34;
      sombreado(c, fig, { color: t.pointsText, separacion: Math.max(1.2, tam * 0.08), grosor: Math.max(0.6, tam * 0.045), alfa: 0.95, cruzado: true, semilla: Math.round(tam),
        intensidad: function (x, y) { return clamp((Math.hypot(x - bx, y - by) - r * 0.3) / r * 1.6, 0, 1); } });
    }
    trazoLapiz(c, fig, marcada ? t.pointsText : t.aro, grosor, Math.round(tam) + 5);
  }
  function pintarEstrella(btn, marcada, tam) {
    var t = T();
    if (idActual === 'dulce') {
      btn.innerHTML = '<canvas aria-hidden="true"></canvas>';
      estrellaLapiz(btn.firstChild, marcada, tam);
    } else {
      btn.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" style="width:' + tam + 'px;height:' + tam + 'px;fill:none;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;stroke:' +
        (marcada ? t.pointsText : t.aro) + '"><path d="' + ESTRELLA + '"/></svg>';
    }
  }
  var valoracion = 4;
  var cajaEstrellas = document.getElementById('estrellas');
  for (var e = 1; e <= 5; e++) {
    (function (v) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'estrella'; b.setAttribute('role', 'radio');
      b.setAttribute('aria-label', v + (v === 1 ? ' estrella' : ' estrellas'));
      b.addEventListener('click', function () { valoracion = valoracion === v ? null : v; pintarEstrellas(); });
      cajaEstrellas.appendChild(b);
    })(e);
  }
  function pintarEstrellas() {
    [].forEach.call(cajaEstrellas.children, function (b, i) {
      b.setAttribute('aria-checked', valoracion === i + 1 ? 'true' : 'false');
      pintarEstrella(b, valoracion != null && i < valoracion, 36);
    });
  }
  function pintarQueda() {
    pintarMes();
    pintarEstrellas();
    decorar(document.getElementById('hoja'), true);
    var media = document.getElementById('media'), me = media.querySelector('.media__e');
    var hueco = document.createElement('span'); hueco.className = 'media__e'; hueco.setAttribute('aria-hidden', 'true');
    media.replaceChild(hueco, me);
    pintarEstrella(hueco, true, 20);
    decorar(media);
  }

  /* ══════════════════════════════════════════════════════════════════════
     IDENTIDADES — cada tarjeta, pintada con la suya
     ══════════════════════════════════════════════════════════════════════ */
  /* Pinta con otra identidad sin cambiar la de la página: todo lo que dibuja
     lee idActual, VW, VH y los arrays del fondo, así que se cambian y se
     devuelven. */
  function conIdentidad(id, w, h, fn) {
    var guard = { id: idActual, vw: VW, vh: VH, e: estrellas, b: burbujas };
    idActual = id;
    if (w) { VW = w; VH = h; generarCielo(); }
    try { fn(); } finally {
      idActual = guard.id; VW = guard.vw; VH = guard.vh; estrellas = guard.e; burbujas = guard.b;
    }
  }
  var CINTURON = [[0.14, 0.77], [0.51, 0.53], [0.86, 0.23]];
  var IDENTS = [].slice.call(document.querySelectorAll('.ident')).map(function (card, k) {
    var h = { n: ['Mirar las estrellas', 'Salir a correr', 'Regar las plantas'][k], ritmo: 3 + (k % 2),
      pasado: [1, 0, 1, 1, 1, 0], hecho: k !== 2 };
    var id = card.dataset.id;
    construirFila(h, function (x) {
      conIdentidad(id, 0, 0, function () {
        x.hecho = !x.hecho;
        var hasta = x.hecho ? 1 : 0, desde = x.v;
        x.v = hasta;
        pintarFila(x);
        animarCheck(x.cv, desde, hasta);
      });
    });
    card.querySelector('.ident__f').appendChild(h.el);
    return { card: card, id: id, h: h };
  });

  function pintarMundoTarjeta(o) {
    var card = o.card, w = card.offsetWidth, h = card.offsetHeight, cv = card.querySelector('.ident__cv');
    var mundo = card.querySelector('.ident__m').offsetHeight;
    // El cielo y las burbujas cubren la tarjeta entera; la ciudad, solo la
    // parte de arriba, para que no pase por detrás del texto.
    conIdentidad(o.id, w, TOKENS[o.id].forma === 'chamfer' ? mundo : h, function () {
      var t = T(), c = preparar(cv, w, h), r = TOKENS[o.id].forma === 'pill' ? 26 : 24;
      function forma(k) {
        k.beginPath();
        if (t.forma === 'chamfer') {
          var ch = 12; k.moveTo(ch, 0); k.lineTo(w - ch, 0); k.lineTo(w, ch); k.lineTo(w, h - ch);
          k.lineTo(w - ch, h); k.lineTo(ch, h); k.lineTo(0, h - ch); k.lineTo(0, ch); k.closePath();
        } else if (k.roundRect) k.roundRect(0.5, 0.5, w - 1, h - 1, r); else k.rect(0.5, 0.5, w - 1, h - 1);
      }
      c.save(); forma(c); c.clip();
      c.fillStyle = t.bg; c.fillRect(0, 0, w, h);
      if (t.forma === 'glass') {
        pintarCielo(c);
        // El Cinturón de Orión encendido: la constelación de tres hábitos.
        var q = { x: w * 0.55, y: mundo * 0.08, lado: Math.min(w * 0.4, mundo * 0.42) };
        c.save(); c.globalCompositeOperation = 'lighter';
        c.strokeStyle = rgba(t.streak, 0.38); c.lineWidth = 1.2; c.beginPath();
        CINTURON.forEach(function (p, i) { var x = q.x + p[0] * q.lado, y = q.y + p[1] * q.lado; if (i) c.lineTo(x, y); else c.moveTo(x, y); });
        c.stroke();
        CINTURON.forEach(function (p) { estrella(c, q.x + p[0] * q.lado, q.y + p[1] * q.lado, 0.85, 1, 0); });
        c.restore();
      } else if (t.forma === 'chamfer') {
        pintarCiudad(c, 0.2, null, 1);
      } else {
        burbujas = burbujas.slice(0, 40);
        pintarBurbujas(c, 26, null, 1);
      }
      c.restore();
      // El borde de la tarjeta, en el lenguaje de cada identidad.
      if (t.forma === 'glass') { forma(c); c.lineWidth = 1; c.strokeStyle = 'rgba(255,255,255,0.10)'; c.stroke(); }
      else if (t.forma === 'chamfer') { forma(c); c.lineWidth = 1.2; c.strokeStyle = rgba(t.primary, 0.7); c.stroke(); }
      else trazoLapiz(c, rrect(1, 1, w - 2, h - 2, r), t.trazo, 1.6, semillaDe(w, h));
    });
  }
  function pintarIdentidades() {
    IDENTS.forEach(function (o) {
      conIdentidad(o.id, 0, 0, function () { pintarFila(o.h); });
      pintarMundoTarjeta(o);
      var b = o.card.querySelector('.ident__b'), activa = o.id === idActual;
      b.setAttribute('aria-pressed', activa ? 'true' : 'false');
      b.textContent = activa ? 'Así se ve ahora' : 'Ver la página así';
    });
  }
  [].forEach.call(document.querySelectorAll('.ident__b'), function (b) {
    b.addEventListener('click', function () {
      if (b.dataset.ir === idActual) return;
      aplicar(b.dataset.ir);
    });
  });

  /* Las ventanas de Cómo funciona y Lo que se queda. En Dulce van en papel con
     el filo a lápiz, como las superficies de la app; en las otras dos las
     dibuja el CSS y aquí no se pinta nada. */
  function pintarVentanas() {
    [].forEach.call(document.querySelectorAll('.pasos .paso__v, .queda .bloque__v'), function (v) {
      var cv = v.querySelector(':scope > canvas.ventana__cv');
      if (idActual !== 'dulce') { if (cv) v.removeChild(cv); return; }
      if (!cv) {
        cv = document.createElement('canvas'); cv.className = 'ventana__cv'; cv.setAttribute('aria-hidden', 'true');
        v.insertBefore(cv, v.firstChild);
      }
      var w = v.offsetWidth, h = v.offsetHeight, c = preparar(cv, w, h);
      trazoLapiz(c, rrect(2, 2, w - 4, h - 4, 20), T().trazo, 1.5, semillaDe(w, h));
    });
  }

  function redibujar() {
    VW = window.innerWidth; VH = window.innerHeight;
    generarCielo();
    pintarFondo(); pintarGrano();
    pintarTiraHoy(); pintarTarjetaRitmo(ritmo, 3);
    pintarPasos();
    pintarQueda();
    pintarVentanas();
    pintarIdentidades();
    [].forEach.call(document.querySelectorAll('.priv__f'), function (el) { decorar(el); });
    HABITOS.forEach(pintarFila);
    pintarCieloApp();
    pintarNori(false);
  }

  var guardada = null;
  try { guardada = localStorage.getItem('norday-identidad'); } catch (e) { /* nada */ }
  if (guardada && TOKENS[guardada]) {
    idActual = guardada;
    document.documentElement.setAttribute('data-id', guardada);
    botones.forEach(function (b) { b.setAttribute('aria-pressed', b.dataset.ir === guardada ? 'true' : 'false'); });
  }
  document.documentElement.setAttribute('data-id', idActual);
  ordenar(false);
  redibujar();
  // Las letras llegan después: las medidas de los chips y las filas cambian.
  if (document.fonts) {
    document.fonts.ready.then(redibujar);
    document.fonts.addEventListener('loadingdone', function () { cache = {}; redibujar(); });
  }
  var espera;
  window.addEventListener('resize', function () {
    clearTimeout(espera);
    espera = setTimeout(function () { cache = {}; redibujar(); }, 150);
  });
})();
