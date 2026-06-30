const API = '/api';

// ── Tabs ──────────────────────────────────────────────
document.getElementById('loginTab').addEventListener('click', () => {
    document.getElementById('formLogin').classList.remove('d-none');
    document.getElementById('formRegistro').classList.add('d-none');
    document.getElementById('loginTab').classList.add('active');
    document.getElementById('registroTab').classList.remove('active');
});

document.getElementById('registroTab').addEventListener('click', () => {
    document.getElementById('formRegistro').classList.remove('d-none');
    document.getElementById('formLogin').classList.add('d-none');
    document.getElementById('registroTab').classList.add('active');
    document.getElementById('loginTab').classList.remove('active');
});

// ── Comprobar si ya hay sesión activa ─────────────────
const token = localStorage.getItem('token');
if (token) {
    window.location.href = '/index.html';
}

// ── Login ─────────────────────────────────────────────
async function login() {
    const email = document.getElementById('loginEmail').value;
    const contrasena = document.getElementById('loginContrasena').value;
    const errorDiv = document.getElementById('loginError');

    if (!email || !contrasena) {
        mostrarError(errorDiv, 'Por favor rellena todos los campos');
        return;
    }

    try {
        const response = await fetch(`${API}/usuarios/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, contrasena })
        });

        if (response.ok) {
            const data = await response.json();
            localStorage.setItem('token', data.token);
            localStorage.setItem('usuario', JSON.stringify({
                usuarioId: data.usuarioId,
                nombre: data.nombre,
                username: data.username,
                email: data.email
            }));
            window.location.href = '/index.html';
        } else {
            const mensaje = await response.text();
            mostrarError(errorDiv, mensaje);
        }
    } catch (error) {
        mostrarError(errorDiv, 'Error de conexión con el servidor');
    }
}



// ── Registro ──────────────────────────────────────────
async function registro() {
    const nombre = document.getElementById('regNombre').value;
    const username = document.getElementById('regUsername').value;
    const email = document.getElementById('regEmail').value;
    const contrasena = document.getElementById('regContrasena').value;
    const errorDiv = document.getElementById('registroError');
    const okDiv = document.getElementById('registroOk');

    if (!nombre || !username || !email || !contrasena) {
        mostrarError(errorDiv, 'Por favor rellena todos los campos');
        return;
    }

    try {
        const response = await fetch(`${API}/usuarios/registro`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nombre, username, email, contrasena })
        });

        if (response.ok) {
            errorDiv.classList.add('d-none');
            okDiv.textContent = '¡Cuenta creada! Ya puedes iniciar sesión.';
            okDiv.classList.remove('d-none');
            setTimeout(() => {
                document.getElementById('loginTab').click();
            }, 2000);
        } else {
            const mensaje = await response.text();
            mostrarError(errorDiv, mensaje);
        }
    } catch (error) {
        mostrarError(errorDiv, 'Error de conexión con el servidor');
    }
}

// ── Utilidades ────────────────────────────────────────
function mostrarError(div, mensaje) {
    div.textContent = mensaje;
    div.classList.remove('d-none');
}

function togglePassword(inputId, button) {
    const input = document.getElementById(inputId);
    const icon = button.querySelector('i');
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.remove('fa-eye');
        icon.classList.add('fa-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.remove('fa-eye-slash');
        icon.classList.add('fa-eye');
    }
}