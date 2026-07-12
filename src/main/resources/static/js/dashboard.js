const API = '/api';

// ── Verificar sesión ──────────────────────────────────
const token = localStorage.getItem('token');
const usuarioJSON = localStorage.getItem('usuario');
if (!token || !usuarioJSON) {
    window.location.href = '/login.html';
}
const usuario = JSON.parse(usuarioJSON);

// ── Headers con token ─────────────────────────────────
const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
};

// ── Inicializar ───────────────────────────────────────
document.getElementById('nombreUsuario').textContent = `Hola, ${usuario.nombre}`;
document.getElementById('fechaHoy').textContent = new Date().toLocaleDateString('es-ES',
    { weekday: 'long', day: 'numeric', month: 'long' });

let habitos = [];
let progresoPorHabito = {};   // habitoId -> {completadoHoy, completadosPeriodo, meta}
let registrosPorHabito = {};  // habitoId -> [fechas completadas] (para mini-heatmap)

const NOMBRES_FRECUENCIA = { DIARIO: 'Diario', SEMANAL: 'Semanal' };

cargarHabitos();

// ── Cargar hábitos ────────────────────────────────────
async function cargarHabitos() {
    try {
        const response = await fetch(`${API}/habitos/usuario/${usuario.usuarioId}/activos`, { headers });

        if (response.status === 401 || response.status === 403) {
            cerrarSesion();
            return;
        }

        habitos = await response.json();
        await cargarProgreso();
        await cargarMiniHeatmaps();
        renderHabitos();
    } catch (error) {
        console.error('Error cargando hábitos:', error);
    }
}

// ── Progreso del periodo por hábito ───────────────────
async function cargarProgreso() {
    for (const habito of habitos) {
        const response = await fetch(`${API}/registros/habito/${habito.habitoId}/hoy`, { headers });
        progresoPorHabito[habito.habitoId] = await response.json();
    }
}

// ── Registros para mini-heatmap (últimos 28 días) ─────
async function cargarMiniHeatmaps() {
    for (const habito of habitos) {
        try {
            const response = await fetch(`${API}/registros/habito/${habito.habitoId}`, { headers });
            const registros = await response.json();
            registrosPorHabito[habito.habitoId] = new Set(
                registros.filter(r => r.completado).map(r => r.fecha)
            );
        } catch {
            registrosPorHabito[habito.habitoId] = new Set();
        }
    }
}

// ── ¿Cuenta como "completado" para la UI de hoy? ──────
function estaHecho(habito) {
    const p = progresoPorHabito[habito.habitoId];
    if (!p) return false;
    if (habito.frecuencia === 'DIARIO') return p.completadosPeriodo >= p.meta;
    return p.completadoHoy; // SEMANAL: hoy ya aportaste — se mueve abajo, el badge muestra el progreso real
}

// ── Renderizar ────────────────────────────────────────
function renderHabitos() {
    const pendientes = habitos.filter(h => !estaHecho(h));
    const completados = habitos.filter(h => estaHecho(h));

    const listaP = document.getElementById('listaPendientes');
    const listaC = document.getElementById('listaCompletados');
    const seccionC = document.getElementById('seccionCompletados');

    if (habitos.length === 0) {
        listaP.innerHTML = `
            <div class="card p-4 text-center">
                <i class="fas fa-seedling fa-3x mb-3" style="color: var(--success);"></i>
                <h6 class="fw-bold mb-1">Tu primer hábito te espera</h6>
                <p class="text-muted small mb-3">Los grandes cambios empiezan con un paso pequeño. Crea tu primer hábito y empieza tu racha hoy.</p>
                <a href="/habito.html" class="btn btn-primary mx-auto">
                    <i class="fas fa-plus me-1"></i>Crear mi primer hábito
                </a>
            </div>`;
        seccionC.classList.add('d-none');
        return;
    }

    listaP.innerHTML = pendientes.length
        ? pendientes.map(h => cardHabito(h, false)).join('')
        : `<div class="card p-3 text-center">
               <span style="font-size: 1.6rem;">🎉</span>
               <p class="mb-0 fw-bold">¡Todo hecho por hoy!</p>
               <small class="text-muted">Disfruta el resto del día.</small>
           </div>`;

    listaC.innerHTML = completados.map(h => cardHabito(h, true)).join('');
    seccionC.classList.toggle('d-none', completados.length === 0);
}

// ── Card de un hábito (fila completa) ─────────────────
function cardHabito(habito, hecho) {
    const p = progresoPorHabito[habito.habitoId] || { completadosPeriodo: 0, meta: habito.meta };
    const racha = habito.racha ? habito.racha.rachaActual : 0;
    const frec = NOMBRES_FRECUENCIA[habito.frecuencia] || habito.frecuencia;

    return `
    <div class="card habito-fila ${hecho ? 'habito-hecha' : ''} p-3" id="habito-${habito.habitoId}"
         onclick="window.location.href='/habito-detalle.html?id=${habito.habitoId}'">
        <div class="d-flex justify-content-between align-items-center gap-3">
            <div class="flex-grow-1 min-w-0">
                <div class="d-flex align-items-center gap-2 flex-wrap">
                    <h6 class="mb-0 fw-bold text-truncate">${habito.nombre}</h6>
                    <span class="badge-frecuencia">${frec} · ${p.completadosPeriodo}/${p.meta}</span>
                    ${racha > 0 ? `<span class="racha-badge">🔥 ${racha}</span>` : ''}
                </div>
                <div class="mini-heatmap mt-2">${miniHeatmap(habito.habitoId)}</div>
            </div>
            <div class="d-flex gap-2 align-items-center">
                ${hecho
        ? `<span class="check-hecho"><i class="fas fa-check"></i></span>`
        : `<button class="btn btn-primary btn-sm btn-completar"
                               onclick="event.stopPropagation(); abrirModalNota(${habito.habitoId})">
                           <i class="fas fa-check me-1"></i>Completar
                       </button>`}
                <button class="btn btn-outline-secondary btn-sm"
                        onclick="event.stopPropagation(); window.location.href='/habito.html?id=${habito.habitoId}'">
                    <i class="fas fa-edit"></i>
                </button>
            </div>
        </div>
    </div>`;
}

// ── Mini-heatmap: últimos 28 días ─────────────────────
function miniHeatmap(habitoId) {
    const fechas = registrosPorHabito[habitoId] || new Set();
    let html = '';
    for (let i = 27; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const iso = d.toISOString().split('T')[0];
        const hoy = i === 0 ? ' es-hoy' : '';
        html += `<span class="mini-dia ${fechas.has(iso) ? 'lleno' : ''}${hoy}" title="${iso}"></span>`;
    }
    return html;
}

// ── Modal de nota ─────────────────────────────────────
let habitoIdPendiente = null;

function abrirModalNota(habitoId) {
    habitoIdPendiente = habitoId;
    document.getElementById('notaCompletar').value = '';
    new bootstrap.Modal(document.getElementById('modalNota')).show();
}

// ── Completar (con animación + celebraciones) ─────────
async function confirmarCompletar() {
    const nota = document.getElementById('notaCompletar').value.trim();

    try {
        const response = await fetch(`${API}/registros/completar/${habitoIdPendiente}`, {
            method: 'POST',
            headers,
            body: JSON.stringify({ nota })
        });

        if (response.ok) {
            const data = await response.json();
            bootstrap.Modal.getInstance(document.getElementById('modalNota')).hide();

            // Animación del check antes de re-renderizar
            const card = document.getElementById(`habito-${habitoIdPendiente}`);
            if (card) card.classList.add('completando');

            // Refrescar datos de ese hábito
            const r = await fetch(`${API}/registros/habito/${habitoIdPendiente}/hoy`, { headers });
            progresoPorHabito[habitoIdPendiente] = await r.json();
            registrosPorHabito[habitoIdPendiente]?.add(new Date().toISOString().split('T')[0]);

            setTimeout(() => {
                renderHabitos();
                if (data.logrosOtorgados && data.logrosOtorgados.length > 0) {
                    mostrarCelebraciones(data.logrosOtorgados);
                }
            }, 450);
        }
    } catch (error) {
        console.error('Error completando hábito:', error);
    }
}

// ── Celebraciones: toasts encadenados (~2.5s cada uno) ─
const NOMBRES_LOGROS = {}; // se rellena al vuelo desde el catálogo si hace falta

async function mostrarCelebraciones(codigos) {
    // Cargar nombres reales del catálogo una sola vez
    if (Object.keys(NOMBRES_LOGROS).length === 0) {
        try {
            const r = await fetch(`${API}/gamificacion/logros/catalogo`, { headers });
            const catalogo = await r.json();
            catalogo.forEach(l => NOMBRES_LOGROS[l.codigo] = l.nombre);
        } catch { /* si falla, mostramos el código */ }
    }

    const contenedor = document.getElementById('celebraciones');
    codigos.forEach((codigo, i) => {
        setTimeout(() => {
            const toast = document.createElement('div');
            toast.className = 'toast-logro';
            toast.innerHTML = `<span class="emoji">🏆</span>
                <div><strong>¡Logro desbloqueado!</strong><br>
                <small>${NOMBRES_LOGROS[codigo] || codigo}</small></div>`;
            toast.onclick = () => toast.remove();
            contenedor.appendChild(toast);
            setTimeout(() => toast.remove(), 2600);
        }, i * 2800);
    });
}

// ── Cerrar sesión ─────────────────────────────────────
function cerrarSesion() {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    window.location.href = '/login.html';
}