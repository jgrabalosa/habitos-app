package com.norday.core.service;

import com.norday.core.model.Usuario;
import com.norday.core.model.dto.ResultadoLoginGoogle;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.gamificacion.service.MotorLogrosService;
import com.norday.gamificacion.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsuarioService.class);

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TextosService textosService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private MotorLogrosService motorLogrosService;

    /** Spring recolecta aquí todas las implementaciones registradas. */
    @Autowired
    private List<LimpiadorDatosUsuario> limpiadores;

    public void registrar(Usuario usuario) {
        if (usuarioDAO.findByEmail(usuario.getEmail()) != null) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }
        if (usuarioDAO.findByUsername(usuario.getUsername()) != null) {
            throw new RuntimeException("Ya existe un usuario con ese username");
        }
        usuario.setFechaRegistro(LocalDateTime.now(ZoneOffset.UTC));
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        usuarioDAO.save(usuario);
        productoService.otorgarTemasBasicosGratis(usuario);

        enviarBienvenidaSilenciosa(usuario);
    }

    // Envía el email de bienvenida sin bloquear el flujo si falla (SMTP caído,
    // credenciales mal configuradas, etc.). El fallo queda registrado como
    // advertencia para poder detectar problemas sistemáticos de envío.
    private void enviarBienvenidaSilenciosa(Usuario usuario) {
        try {
            // El motor dispara el envío; el texto lo pone el bundle de la app
            // (mensajes/habitos), no esta clase.
            emailService.enviarEmail(
                    usuario.getEmail(),
                    "email.bienvenida.asunto",
                    "email.bienvenida.cuerpo",
                    textosService.localeDe(usuario),
                    usuario.getNombre());
        } catch (Exception e) {
            log.warn("Error al enviar email de bienvenida a {}: {}", usuario.getEmail(), e.getMessage());
        }
    }

    public Usuario login(String email, String contrasena) {
        Usuario usuario = usuarioDAO.findByEmail(email);
        if (usuario == null) {
            throw new RuntimeException("Email no encontrado");
        }
        if ("GOOGLE".equals(usuario.getProveedorAuth())) {
            throw new RuntimeException("Esta cuenta usa Google para iniciar sesión. Pulsa \"Continuar con Google\".");
        }
        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {
            throw new RuntimeException("Contraseña incorrecta");
        }
        return usuario;
    }

    public ResultadoLoginGoogle loginConGoogle(String email, String nombre) {
        Usuario usuario = usuarioDAO.findByEmail(email);

        if (usuario != null) {
            if (!"GOOGLE".equals(usuario.getProveedorAuth())) {
                usuario.setProveedorAuth("GOOGLE");
                usuarioDAO.update(usuario);
            }
            motorLogrosService.evaluarTrasLoginGoogle(usuario);
            return new ResultadoLoginGoogle(usuario, false);
        }

        // Usuario nuevo vía Google
        String username = email.split("@")[0] + "_" + System.currentTimeMillis() % 10000;
        String contrasenaAleatoria = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

        Usuario nuevoUsuario = new Usuario(nombre, username, email, contrasenaAleatoria);
        nuevoUsuario.setProveedorAuth("GOOGLE");
        nuevoUsuario.setFechaRegistro(LocalDateTime.now(ZoneOffset.UTC));
        usuarioDAO.save(nuevoUsuario);
        productoService.otorgarTemasBasicosGratis(nuevoUsuario);

        enviarBienvenidaSilenciosa(nuevoUsuario);

        motorLogrosService.evaluarTrasLoginGoogle(nuevoUsuario);
        return new ResultadoLoginGoogle(nuevoUsuario, true);
    }

    public Usuario buscarPorId(int id) {
        return usuarioDAO.findById(id);
    }

    public void actualizarPerfil(Usuario usuario) {
        Usuario existente = usuarioDAO.findById(usuario.getUsuarioId());
        if (existente == null) {
            throw new RuntimeException("Usuario no encontrado");
        }

        // Solo se actualizan los campos editables del perfil.
        // El resto (contrasena, proveedorAuth, fcmToken, fechaRegistro,
        // idioma, zonaHoraria...) se conservan tal cual están en la BD;
        // las preferencias tienen su propio endpoint.
        if (usuario.getNombre() != null) {
            existente.setNombre(usuario.getNombre());
        }
        if (usuario.getUsername() != null) {
            existente.setUsername(usuario.getUsername());
        }
        if (usuario.getEmail() != null
                && !usuario.getEmail().equals(existente.getEmail())) {
            if ("GOOGLE".equals(existente.getProveedorAuth())) {
                throw new RuntimeException(
                        "Las cuentas de Google no pueden cambiar su email");
            }
            existente.setEmail(usuario.getEmail());
        }
        usuarioDAO.update(existente);
        motorLogrosService.evaluarTrasActualizarPerfil(existente);
    }

    public void cambiarContrasena(int id, String contrasenaActual, String contrasenaNueva) {
        Usuario usuario = usuarioDAO.findById(id);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado");
        }

        if ("GOOGLE".equals(usuario.getProveedorAuth())) {
            throw new RuntimeException("Las cuentas de Google no tienen contraseña propia");
        }

        if (contrasenaActual == null
                || !passwordEncoder.matches(contrasenaActual, usuario.getContrasena())) {
            throw new RuntimeException("La contraseña actual no es correcta");
        }

        if (contrasenaNueva == null || contrasenaNueva.length() < 6) {
            throw new RuntimeException("La nueva contraseña debe tener al menos 6 caracteres");
        }

        usuario.setContrasena(passwordEncoder.encode(contrasenaNueva));
        usuarioDAO.update(usuario);
    }

    public void actualizarFcmToken(int id, String fcmToken) {
        Usuario usuario = usuarioDAO.findById(id);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado");
        }
        usuario.setFcmToken(fcmToken);
        usuarioDAO.update(usuario);
    }

    @Transactional
    public void eliminarCuenta(int id) {
        Usuario usuario = usuarioDAO.findById(id);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado");
        }

        // Cada módulo borra lo suyo. El orden entre limpiadores da igual (son
        // árboles de FK independientes que solo apuntan a Usuario); lo que
        // importa es que todos terminen antes de borrar el propio Usuario.
        for (LimpiadorDatosUsuario limpiador : limpiadores) {
            limpiador.limpiar(usuario);
        }

        usuarioDAO.delete(id);
    }
}