// ── Tema: se ejecuta ANTES del render para evitar parpadeo ──
(function () {
    const guardado = localStorage.getItem('tema');
    const preferido = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    document.documentElement.setAttribute('data-bs-theme', guardado || preferido);
})();

function alternarTema() {
    const actual = document.documentElement.getAttribute('data-bs-theme');
    const nuevo = actual === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-bs-theme', nuevo);
    localStorage.setItem('tema', nuevo);
    actualizarIconoTema();
}

function actualizarIconoTema() {
    const icono = document.getElementById('iconoTema');
    if (!icono) return;
    const esOscuro = document.documentElement.getAttribute('data-bs-theme') === 'dark';
    icono.className = esOscuro ? 'fas fa-sun' : 'fas fa-moon';
}

document.addEventListener('DOMContentLoaded', actualizarIconoTema);