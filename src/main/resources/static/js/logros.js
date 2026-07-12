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

let catalogoLogros = [];
let logrosConseguidos = [];

cargarSaldo();
cargarLogros();

// ── Cargar saldo ──────────────────────────────────────
async function cargarSaldo() {
    try {
        const response = await fetch(`${API}/gamificacion/saldo/${usuario.usuarioId}`, { headers });

        if (response.status === 401 || response.status === 403) {
            cerrarSesion();
            return;
        }

        const data = await response.json();
        document.getElementById('saldoPuntos').textContent = data.saldo;
    } catch (error) {
        console.error('Error cargando saldo:', error);
    }
}

// ── Cargar logros (catálogo + conseguidos) ────────────
async function cargarLogros() {
    try {
        const [catalogoResponse, conseguidosResponse] = await Promise.all([
            fetch(`${API}/gamificacion/logros/catalogo`, { headers }),
            fetch(`${API}/gamificacion/logros/usuario/${usuario.usuarioId}`, { headers })
        ]);

        catalogoLogros = await catalogoResponse.json();
        logrosConseguidos = await conseguidosResponse.json();

        renderLogros();
    } catch (error) {
        console.error('Error cargando logros:', error);
    }
}

// ── Renderizar logros ─────────────────────────────────
function renderLogros() {
    const ICONOS_CATEGORIA = {
        'Inicio': '🌱',
        'Constancia': '🔥',
        'Volumen': '📊',
        'Variedad': '🎨',
        'Exploración': '🧭'
    };

    function renderLogros() {
        const lista = document.getElementById('listaLogros');
        const idsConseguidos = new Set(logrosConseguidos.map(ul => ul.logro.logroId));

        // Barra de progreso global
        const total = catalogoLogros.length;
        const conseguidos = idsConseguidos.size;
        const pct = total > 0 ? Math.round((conseguidos / total) * 100) : 0;
        document.getElementById('textoProgreso').textContent = `${conseguidos} de ${total} logros`;
        document.getElementById('pctProgreso').textContent = `${pct}%`;
        document.getElementById('barraProgreso').style.width = `${pct}%`;

        if (catalogoLogros.length === 0) {
            lista.innerHTML = `
            <div class="col-12 text-center text-muted py-4">
                <span style="font-size: 2rem;">🧭</span>
                <p class="mb-0">El catálogo de logros llegará pronto.</p>
            </div>`;
            return;
        }

        lista.innerHTML = catalogoLogros.map(logro => {
            const conseguido = idsConseguidos.has(logro.logroId);
            const icono = ICONOS_CATEGORIA[logro.categoria] || '⭐';
            return `
        <div class="col-md-6 col-lg-4">
            <div class="card p-3 h-100 ${conseguido ? '' : 'opacity-50'}">
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <h6 class="mb-1 fw-bold">${icono} ${logro.nombre}</h6>
                        <small class="text-muted d-block">${logro.descripcion}</small>
                        <span class="badge-frecuencia mt-2 d-inline-block">${logro.categoria} · ${logro.nivel}</span>
                    </div>
                    <span class="fs-4">${conseguido ? '🏆' : '🔒'}</span>
                </div>
                <small class="text-muted mt-2">+${logro.puntos} pts</small>
            </div>
        </div>`;
        }).join('');
    }
}

// ── Cerrar sesión ─────────────────────────────────────
function cerrarSesion() {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    window.location.href = '/login.html';
}