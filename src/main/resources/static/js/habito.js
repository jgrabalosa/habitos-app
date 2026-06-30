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

// ── Detectar modo edición ─────────────────────────────
const params = new URLSearchParams(window.location.search);
const habitoId = params.get('id');
const esEdicion = habitoId !== null;

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

        if (esEdicion) await cargarHabito();
    } catch (error) {
        console.error('Error cargando categorías:', error);
    }
}

// ── Cargar hábito existente (modo edición) ────────────
async function cargarHabito() {
    try {
        const response = await fetch(`${API}/habitos/${habitoId}`, { headers });
        const habito = await response.json();

        document.getElementById('nombre').value = habito.nombre;
        document.getElementById('descripcion').value = habito.descripcion || '';
        document.getElementById('frecuencia').value = habito.frecuencia;
        document.getElementById('meta').value = habito.meta || 1;
        if (habito.tipo) {
            document.getElementById('categoria').value = habito.tipo.categoriaId;
        }

        document.getElementById('tituloForm').innerHTML =
            '<i class="fas fa-edit me-2 text-primary"></i>Editar hábito';
        document.getElementById('btnGuardar').innerHTML =
            '<i class="fas fa-save me-2"></i>Actualizar hábito';
        document.getElementById('btnEliminar').classList.remove('d-none');
    } catch (error) {
        console.error('Error cargando hábito:', error);
    }
}

// ── Guardar (crear o actualizar) ──────────────────────
async function guardarHabito() {
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
        const url = esEdicion ? `${API}/habitos/${habitoId}` : `${API}/habitos`;
        const method = esEdicion ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method,
            headers,
            body: JSON.stringify(habito)
        });

        if (response.ok) {
            errorDiv.classList.add('d-none');
            okDiv.textContent = esEdicion ? '¡Hábito actualizado!' : '¡Hábito creado correctamente!';
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

// ── Eliminar hábito ────────────────────────────────────
async function eliminarHabito() {
    if (!confirm('¿Seguro que quieres eliminar este hábito? Esta acción no se puede deshacer.')) {
        return;
    }

    try {
        const response = await fetch(`${API}/habitos/${habitoId}`, {
            method: 'DELETE',
            headers
        });

        if (response.ok) {
            window.location.href = '/index.html';
        } else {
            const mensaje = await response.text();
            document.getElementById('errorMsg').textContent = mensaje;
            document.getElementById('errorMsg').classList.remove('d-none');
        }
    } catch (error) {
        document.getElementById('errorMsg').textContent = 'Error de conexión con el servidor';
        document.getElementById('errorMsg').classList.remove('d-none');
    }
}

// ── Inicializar ───────────────────────────────────────
cargarCategorias();