(function(){
  var NORI = {"huevo": "/img/nori/nori-huevo.png", "huevo_ok": "/img/nori/nori-huevo-despierta.png", "cria": "/img/nori/nori-cria.png", "adulto": "/img/nori/nori-adulta.png"};
  var quieto = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var svgNS = 'http://www.w3.org/2000/svg';

  /* ── Ilustraciones ──────────────────────────────────────────────────── */
  document.getElementById('noriAnillo').src = NORI.adulto;
  document.getElementById('noriDemo').src = NORI.adulto;
  document.getElementById('fHuevo').src  = NORI.huevo;
  document.getElementById('fCria').src   = NORI.cria;
  document.getElementById('fAdulto').src = NORI.adulto;

  /* ── Fondos generativos ─────────────────────────────────────────────── */
  var polvo = document.getElementById('polvo');
  for (var i = 0; i < 95; i++) {
    var c = document.createElementNS(svgNS,'circle');
    c.setAttribute('cx',(Math.random()*1000).toFixed(1));
    c.setAttribute('cy',(Math.random()*700).toFixed(1));
    c.setAttribute('r',(Math.random()*1.1+.35).toFixed(2));
    c.setAttribute('opacity',(Math.random()*.30+.06).toFixed(2));
    polvo.appendChild(c);
  }

  var ciudad = document.getElementById('ciudad'), ventanas = [], x = -20;
  while (x < 1020) {
    var w = 40+Math.random()*70, h = 130+Math.random()*300;
    var b = document.createElementNS(svgNS,'rect');
    b.setAttribute('class','bloque'); b.setAttribute('x',x); b.setAttribute('y',700-h);
    b.setAttribute('width',w); b.setAttribute('height',h); ciudad.appendChild(b);
    for (var vy = 700-h+16; vy < 690; vy += 22) {
      for (var vx = x+9; vx < x+w-12; vx += 17) {
        var v = document.createElementNS(svgNS,'rect');
        v.setAttribute('class','ventana'); v.setAttribute('x',vx); v.setAttribute('y',vy);
        v.setAttribute('width',7); v.setAttribute('height',10);
        ciudad.appendChild(v); ventanas.push(v);
      }
    }
    x += w+6+Math.random()*10;
  }
  ventanas.sort(function(){ return Math.random()-.5; });

  var petalosG = document.getElementById('petalos'), petalos = [];
  for (var p = 0; p < 130; p++) {
    var pc = document.createElementNS(svgNS,'circle');
    pc.setAttribute('cx',(Math.random()*1000).toFixed(1));
    pc.setAttribute('cy',(Math.random()*700).toFixed(1));
    pc.setAttribute('r',(Math.random()*9+3).toFixed(1));
    petalosG.appendChild(pc); petalos.push(pc);
  }

  /* Calendario del mes: la misma figura que dibuja el detalle de hábito. */
  var mes = document.getElementById('mes');
  ['fuera','n2','n3','n1','n3','','n2','n3','n3','n1','','n3','n2','n3',
   'n2','','n3','n1','n3','n3','n2','n3','n2','','n1','n3','n3','n2',
   'n3','n2','n3','fuera','fuera','fuera','fuera'].forEach(function(n,i){
    var el = document.createElement('i');
    if (n) el.className = n;
    el.style.setProperty('--d', (i*0.018).toFixed(3)+'s');
    mes.appendChild(el);
  });

  /* ── El hero jugable ────────────────────────────────────────────────── */
  var hechos = [false,false,false,false];
  var astros = [].slice.call(document.querySelectorAll('.cruz .astro'));
  var halos  = [].slice.call(document.querySelectorAll('.cruz .halo'));
  var cruz = document.getElementById('cruz'), luz = document.getElementById('luzAlba');
  var noriEl = document.getElementById('nori'), noriImg = document.getElementById('noriImg');
  var faseTxt = document.getElementById('faseTxt'), pista = document.getElementById('pista');
  var puntosEl = document.getElementById('puntos'), contador = document.getElementById('contador');

  var FASES = [
    {src:'huevo',    txt:'Nori está dormida'},
    {src:'huevo_ok', txt:'Nori se ha despertado'},
    {src:'cria',     txt:'Nori ha salido del huevo'},
    {src:'cria',     txt:'Nori está creciendo'},
    {src:'adulto',   txt:'Nori ha crecido contigo'}
  ];

  function pintar(){
    var n = hechos.filter(Boolean).length;
    contador.textContent = n+' de 4 completados';
    puntosEl.textContent = 240 + n*10;

    astros.forEach(function(a,i){ a.classList.toggle('viva', i<n); });
    halos.forEach(function(h,i){ h.classList.toggle('viva', i<n); });
    cruz.classList.toggle('enlazada', n===4);

    var cuantas = Math.round(ventanas.length*(n/4)*.55);
    ventanas.forEach(function(v,i){ v.classList.toggle('viva', i<cuantas); });

    luz.style.height = (n/4*46)+'%';

    var cuantos = Math.round(petalos.length*(n/4)*.8);
    petalos.forEach(function(c,i){ c.classList.toggle('viva', i<cuantos); });

    var fase = FASES[n];
    if (noriImg.dataset.fase !== fase.src) {
      noriImg.dataset.fase = fase.src;
      noriImg.src = NORI[fase.src];
      if (!quieto) {
        noriEl.classList.add('saltando');
        setTimeout(function(){ noriEl.classList.remove('saltando'); }, 550);
      }
    }
    faseTxt.textContent = fase.txt;
    pista.classList.toggle('hecho', n===4);
    pista.textContent = n===0 ? 'Marca un hábito y mira qué pasa'
                      : '';
  }

  [].forEach.call(document.querySelectorAll('.fh'), function(f){
    f.addEventListener('click', function(){
      var i = +f.dataset.i;
      hechos[i] = !hechos[i];
      f.setAttribute('aria-pressed', hechos[i] ? 'true' : 'false');
      pintar();
    });
  });

  /* ── El selector: la barra y los especímenes mandan lo mismo ────────── */
  var demo = document.getElementById('demo');
  function aplicar(id){
    if (demo && !quieto) {
      demo.classList.add('mudando');
      setTimeout(function(){ demo.classList.remove('mudando'); }, 340);
    }
    document.documentElement.setAttribute('data-tema', id);
    [].forEach.call(document.querySelectorAll('.id'), function(o){
      o.setAttribute('aria-pressed', o.dataset.id === id ? 'true' : 'false');
    });
  }
  [].forEach.call(document.querySelectorAll('.id, .spec'), function(b){
    b.addEventListener('click', function(){ aplicar(b.dataset.id); });
  });


  /* ── Entradas al cruzar el borde ────────────────────────────────────── */
  if (!quieto && 'IntersectionObserver' in window) {
    var obs = new IntersectionObserver(function(items){
      items.forEach(function(it){
        if (!it.isIntersecting) return;
        it.target.classList.add('in');
        [].forEach.call(it.target.querySelectorAll('.rv,.rv-i,.rv-d,.ln'), function(e){ e.classList.add('in'); });
        contarDentro(it.target);
        obs.unobserve(it.target);
      });
    }, {rootMargin:'0px 0px -12% 0px', threshold:.12});
    [].forEach.call(document.querySelectorAll('[data-obs]'), function(s){ obs.observe(s); });
  } else {
    document.body.classList.add('sin-animacion');
    [].forEach.call(document.querySelectorAll('[data-obs],.rv,.rv-i,.rv-d,.ln'), function(e){ e.classList.add('in'); });
  }

  /* ── Cifras que cuentan al entrar ───────────────────────────────────── */
  function contarDentro(raiz){
    [].forEach.call(raiz.querySelectorAll('.num[data-hasta]'), function(el){
      if (el.dataset.hecho) return;
      el.dataset.hecho = '1';
      var fin = +el.dataset.hasta, t0 = null, dur = 1100;
      if (quieto) { el.textContent = fin; return; }
      function paso(t){
        if (!t0) t0 = t;
        var p = Math.min((t - t0)/dur, 1);
        el.textContent = Math.round(fin * (1 - Math.pow(1-p, 3)));
        if (p < 1) requestAnimationFrame(paso);
      }
      requestAnimationFrame(paso);
    });
  }

  /* ── Parallax del fondo, a un cuarto de velocidad ───────────────────── */
  if (!quieto) {
    var capas = document.querySelector('.fondo'), pendiente = false;
    window.addEventListener('scroll', function(){
      if (pendiente) return;
      pendiente = true;
      requestAnimationFrame(function(){
        capas.style.transform = 'translate3d(0,' + (window.scrollY * .25) + 'px,0)';
        pendiente = false;
      });
    }, {passive:true});
  }

  /* ── El teléfono mira al cursor. Tres grados, no doce. ──────────────── */
  var escena = document.getElementById('escena'), marco = document.getElementById('marco');
  if (!quieto && escena && window.matchMedia('(pointer:fine)').matches) {
    escena.addEventListener('pointermove', function(e){
      var r = escena.getBoundingClientRect();
      marco.classList.add('vivo');
      marco.style.setProperty('--ry', (((e.clientX - r.left)/r.width - .5) *  6).toFixed(2) + 'deg');
      marco.style.setProperty('--rx', (((e.clientY - r.top)/r.height - .5) * -4).toFixed(2) + 'deg');
    });
    escena.addEventListener('pointerleave', function(){
      marco.classList.remove('vivo');
      marco.style.setProperty('--ry','0deg'); marco.style.setProperty('--rx','0deg');
    });
  }

  /* ── Carga: contador y cortina ──────────────────────────────────────── */
  var carga = document.getElementById('carga');
  var cargaN = document.getElementById('cargaN'), cargaB = document.getElementById('cargaB');
  function abrir(){
    carga.classList.add('fuera');
    document.body.classList.add('listo');
  }
  if (quieto) {
    carga.style.display = 'none';
    document.body.classList.add('listo');
  } else {
    var n = 0;
    var reloj = setInterval(function(){
      n = Math.min(n + Math.ceil(Math.random()*9), 100);
      cargaN.textContent = String(n).padStart(3,'0');
      cargaB.style.transform = 'scaleX(' + (n/100) + ')';
      if (n >= 100) { clearInterval(reloj); setTimeout(abrir, 420); }
    }, 55);
    carga.addEventListener('click', function(){ clearInterval(reloj); abrir(); });
  }

  var cargaT = document.getElementById('cargaT'), FRASE = 'Constancia, no perfección';
  if (quieto) { cargaT.textContent = FRASE; }
  else {
    var k = 0;
    var tecla = setInterval(function(){
      cargaT.textContent = FRASE.slice(0, ++k);
      if (k >= FRASE.length) { clearInterval(tecla); carga.classList.add('escrito'); }
    }, 42);
  }
  var pegado = document.getElementById('pegado');
  if (pegado && !quieto && 'IntersectionObserver' in window) {
    new IntersectionObserver(function(x){
      pegado.classList.toggle('activo', x[0].isIntersecting);
    }, {rootMargin:'-35% 0px -35% 0px'}).observe(pegado);
  }
  var demoW = document.getElementById('demoW');
  if (!quieto && demoW && window.matchMedia('(pointer:fine)').matches) {
    demoW.addEventListener('pointermove', function(e){
      var r = demoW.getBoundingClientRect();
      demo.classList.add('vivo');
      demo.style.setProperty('--ry', (((e.clientX-r.left)/r.width - .5) *  6).toFixed(2)+'deg');
      demo.style.setProperty('--rx', (((e.clientY-r.top)/r.height - .5) * -4).toFixed(2)+'deg');
    });
    demoW.addEventListener('pointerleave', function(){
      demo.classList.remove('vivo');
      demo.style.setProperty('--ry','0deg'); demo.style.setProperty('--rx','0deg');
    });
  }

  pintar();
})();
