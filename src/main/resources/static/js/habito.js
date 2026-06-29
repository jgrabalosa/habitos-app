const API = 'http://localhost:8080/api';

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

// ── Cargar categorías ─────────────────────────────────
async function cargarCategorias() {
    try {
        const response = await fetch(`${API}/categorias/usuario/${usuario.usuarioId}`, { headers });

        if (response.status === 401 || response.status === 403) {
            window.location.href = '/login.html';
            return;
        }

        const categorias = await response.json();
        const select = document.getElementById('categoria');
        select.innerHTML = '<option value="">Sin categoría</option>';

        categorias.forEach(cat => {
            const option = document.createElement('option');
            option.value = cat.categoriaId;
            option.textContent = cat.esGlobal ? `${cat.nombre} (global)` : cat.nombre;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('Error cargando categorías:', error);
    }
}

// ── Crear hábito ──────────────────────────────────────
async function crearHabito() {
    const nombre = document.getElementById('nombre').value.trim();
    const descripcion = document.getElementById('descripcion').value.trim();
    const frecuencia = document.getElementById('frecuencia').value;
    const meta = parseInt(document.getElementById('meta').value);
    const categoriaId = document.getElementById('categoria').value;
    const errorDiv = document.getElementById('errorMsg');
    const okDiv = document.getElementById('okMsg');

    if (!nombre) {
        errorDiv.textContent = 'El nombre es obligatorio';
        errorDiv.classList.remove('d-none');
        return;
    }

    const habito = {
        nombre,
        descripcion,
        frecuencia,
        meta,
        propietario: { usuarioId: usuario.usuarioId }
    };

    if (categoriaId) {
        habito.tipo = { categoriaId: parseInt(categoriaId) };
    }

    try {
        const response = await fetch(`${API}/habitos`, {
            method: 'POST',
            headers,
            body: JSON.stringify(habito)
        });

        if (response.ok) {
            errorDiv.classList.add('d-none');
            okDiv.textContent = '¡Hábito creado correctamente!';
            okDiv.classList.remove('d-none');
            setTimeout(() => {
                window.location.href = '/index.html';
            }, 1500);
        } else {
            const mensaje = await response.text();
            errorDiv.textContent = mensaje;
            errorDiv.classList.remove('d-none');
        }
    } catch (error) {
        errorDiv.textContent = 'Error de conexión con el servidor';
        errorDiv.classList.remove('d-none');
    }
}

// ── Inicializar ───────────────────────────────────────
cargarCategorias();