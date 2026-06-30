const API = '/api';

// ── Verificar sesión ──────────────────────────────────
const token = localStorage.getItem('token');
const usuarioJSON = localStorage.getItem('usuario');
if (!token || !usuarioJSON) {
    window.location.href = '/login.html';
}

// ── Headers con token ─────────────────────────────────
const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
};

// ── Obtener ID del hábito de la URL ───────────────────
const params = new URLSearchParams(window.location.search);
const habitoId = params.get('id');

if (!habitoId) {
    window.location.href = '/index.html';
}

// ── Estado del mes mostrado ───────────────────────────
let mesActual = new Date();
mesActual.setDate(1);

const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
    'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

// ── Cargar detalle ─────────────────────────────────────
async function cargarDetalle() {
    const año = mesActual.getFullYear();
    const mes = String(mesActual.getMonth() + 1).padStart(2, '0');
    const mesParam = `${año}-${mes}`;

    try {
        const response = await fetch(`${API}/habitos/${habitoId}/detalle?mes=${mesParam}`, { headers });

        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login.html';
            return;
        }

        if (!response.ok) {
            const mensaje = await response.text();
            alert(mensaje);
            window.location.href = '/index.html';
            return;
        }

        const detalle = await response.json();
        renderDetalle(detalle);
    } catch (error) {
        console.error('Error cargando detalle:', error);
    }
}

// ── Renderizar todo ────────────────────────────────────
function renderDetalle(detalle) {
    document.getElementById('nombreHabito').textContent = detalle.nombre;
    document.getElementById('rachaActual').textContent = detalle.rachaActual;
    document.getElementById('rachaMaxima').textContent = detalle.rachaMaxima;
    document.getElementById('totalCompletados').textContent = detalle.totalCompletados;
    document.getElementById('porcentajeMes').textContent =
        detalle.porcentajeMesActual !== null ? `${Math.round(detalle.porcentajeMesActual)}%` : '-';

    document.getElementById('mesActual').textContent =
        `${meses[mesActual.getMonth()]} ${mesActual.getFullYear()}`;

    // Deshabilitar botón "siguiente" si es el mes actual
    const hoy = new Date();
    const esMesActual = mesActual.getMonth() === hoy.getMonth() && mesActual.getFullYear() === hoy.getFullYear();
    document.getElementById('btnMesSiguiente').disabled = esMesActual;

    renderHeatmap(detalle.heatmap);
    renderRegistros(detalle.ultimosRegistros);
}

// ── Heatmap ─────────────────────────────────────────────
function renderHeatmap(dias) {
    const grid = document.getElementById('heatmapGrid');
    const primerDia = new Date(dias[0].fecha + 'T00:00:00');
    const diaSemana = (primerDia.getDay() + 6) % 7; // Lunes = 0

    let html = '';
    for (let i = 0; i < diaSemana; i++) {
        html += `<div class="heatmap-day futuro"></div>`;
    }

    dias.forEach(dia => {
        const fecha = new Date(dia.fecha + 'T00:00:00');
        const numero = fecha.getDate();
        html += `<div class="heatmap-day ${dia.completado ? 'completado' : ''}" title="${dia.fecha}">${numero}</div>`;
    });

    grid.innerHTML = html;
}

// ── Últimos registros ───────────────────────────────────
function renderRegistros(registros) {
    const lista = document.getElementById('listaRegistros');

    if (registros.length === 0) {
        lista.innerHTML = '<p class="text-muted text-center mb-0">Todavía no hay registros</p>';
        return;
    }

    lista.innerHTML = registros.map(r => `
        <div class="d-flex justify-content-between align-items-center py-2 border-bottom">
            <div>
                <strong>${r.fecha}</strong>
                ${r.nota ? `<br><small class="text-muted">${r.nota}</small>` : ''}
            </div>
            <span class="badge ${r.completado ? 'bg-success' : 'bg-secondary'}">
                ${r.completado ? '✅ Completado' : 'No completado'}
            </span>
        </div>
    `).join('');
}

// ── Navegación entre meses ─────────────────────────────
function cambiarMes(direccion) {
    mesActual.setMonth(mesActual.getMonth() + direccion);
    cargarDetalle();
}

// ── Inicializar ─────────────────────────────────────────
cargarDetalle();