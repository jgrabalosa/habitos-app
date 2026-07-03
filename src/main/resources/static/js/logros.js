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
    const lista = document.getElementById('listaLogros');

    const idsConseguidos = new Set(logrosConseguidos.map(ul => ul.logro.logroId));

    lista.innerHTML = catalogoLogros.map(logro => {
        const conseguido = idsConseguidos.has(logro.logroId);
        return `
        <div class="col-md-6 col-lg-4">
            <div class="card p-3 ${conseguido ? '' : 'opacity-50'}">
                <div class="d-flex justify-content-between align-items-start">
                    <div>
                        <h6 class="mb-1 fw-bold">${logro.nombre}</h6>
                        <small class="text-muted d-block">${logro.descripcion}</small>
                        <span class="badge bg-secondary mt-2">${logro.categoria}</span>
                        <span class="badge bg-info mt-2">${logro.nivel}</span>
                    </div>
                    <span class="fs-4">${conseguido ? '🏆' : '🔒'}</span>
                </div>
                <small class="text-muted mt-2">+${logro.puntos} pts</small>
            </div>
        </div>`;
    }).join('');
}

// ── Cerrar sesión ─────────────────────────────────────
function cerrarSesion() {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    window.location.href = '/login.html';
}