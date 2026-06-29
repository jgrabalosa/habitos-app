const API = 'http://localhost:8080/api';

// ── Verificar sesión ──────────────────────────────────
const usuarioJSON = sessionStorage.getItem('usuario');
if (!usuarioJSON) {
    window.location.href = '/login.html';
}
const usuario = JSON.parse(usuarioJSON);

// ── Inicializar ───────────────────────────────────────
document.getElementById('nombreUsuario').textContent = `Hola, ${usuario.nombre}`;

let habitos = [];
let completadosHoy = new Set();

cargarHabitos();

// ── Cargar hábitos ────────────────────────────────────
async function cargarHabitos() {
    try {
        const response = await fetch(`${API}/habitos/usuario/${usuario.usuarioId}/activos`);
        habitos = await response.json();

        await verificarCompletados();
        renderHabitos();
        actualizarEstadisticas();
    } catch (error) {
        console.error('Error cargando hábitos:', error);
    }
}

// ── Verificar completados hoy ─────────────────────────
async function verificarCompletados() {
    for (const habito of habitos) {
        const response = await fetch(`${API}/registros/habito/${habito.habitoId}/hoy`);
        const data = await response.json();
        if (data.completadoHoy) {
            completadosHoy.add(habito.habitoId);
        }
    }
}

// ── Renderizar hábitos ────────────────────────────────
function renderHabitos() {
    const lista = document.getElementById('listaHabitos');

    if (habitos.length === 0) {
        lista.innerHTML = `
            <div class="col-12 text-center text-muted py-4">
                <i class="fas fa-seedling fa-3x mb-3 text-primary"></i>
                <p class="mb-2">Todavía no tienes hábitos</p>
                <a href="/habito.html" class="btn btn-primary btn-sm">
                    <i class="fas fa-plus me-1"></i>Crear primer hábito
                </a>
            </div>`;
        return;
    }

    lista.innerHTML = habitos.map(habito => {
        const completado = completadosHoy.has(habito.habitoId);
        return `
        <div class="col-md-6 col-lg-4">
            <div class="card habito-card ${completado ? 'habito-completado' : 'habito-pendiente'} p-3">
                <div class="d-flex justify-content-between align-items-start mb-2">
                    <div>
                        <h6 class="mb-1 fw-bold">${habito.nombre}</h6>
                        <small class="text-muted">
                            <i class="fas fa-tag me-1"></i>${habito.tipo ? habito.tipo.nombre : 'Sin categoría'}
                        </small>
                    </div>
                    <span class="racha-badge">
                        🔥 ${habito.racha ? habito.racha.rachaActual : 0}
                    </span>
                </div>
                ${habito.descripcion ? `<p class="text-muted small mb-2">${habito.descripcion}</p>` : ''}
                <div class="d-flex gap-2 mt-2">
                    ${completado
            ? `<button class="btn btn-success btn-sm w-100" disabled>
                            <i class="fas fa-check me-1"></i>Completado hoy
                           </button>`
            : `<button class="btn btn-primary btn-sm w-100" onclick="completar(${habito.habitoId})">
                            <i class="fas fa-check me-1"></i>Completar
                           </button>`
        }
                </div>
            </div>
        </div>`;
    }).join('');
}

// ── Completar hábito ──────────────────────────────────
async function completar(habitoId) {
    try {
        const response = await fetch(`${API}/registros/completar/${habitoId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nota: '' })
        });

        if (response.ok) {
            completadosHoy.add(habitoId);
            renderHabitos();
            actualizarEstadisticas();
        }
    } catch (error) {
        console.error('Error completando hábito:', error);
    }
}

// ── Estadísticas ──────────────────────────────────────
function actualizarEstadisticas() {
    const total = habitos.length;
    const completados = completadosHoy.size;
    const pendientes = total - completados;

    document.getElementById('totalHabitos').textContent = total;
    document.getElementById('completadosHoy').textContent = completados;
    document.getElementById('pendientesHoy').textContent = pendientes;
}

// ── Cerrar sesión ─────────────────────────────────────
function cerrarSesion() {
    sessionStorage.removeItem('usuario');
    window.location.href = '/login.html';
}