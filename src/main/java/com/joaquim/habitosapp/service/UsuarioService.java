package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.ICategoriaDAO;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsuarioService {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private MotorLogrosService motorLogrosService;

    public void registrar(Usuario usuario) {
        if (usuarioDAO.findByEmail(usuario.getEmail()) != null) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }
        if (usuarioDAO.findByUsername(usuario.getUsername()) != null) {
            throw new RuntimeException("Ya existe un usuario con ese username");
        }
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));
        usuarioDAO.save(usuario);

        try {
            emailService.enviarEmailBienvenida(usuario.getEmail(), usuario.getNombre());
        } catch (Exception e) {
            System.out.println("Error al enviar email de bienvenida: " + e.getMessage());
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

    public Usuario loginConGoogle(String email, String nombre) {
        Usuario usuario = usuarioDAO.findByEmail(email);

        if (usuario != null) {
            if (!"GOOGLE".equals(usuario.getProveedorAuth())) {
                usuario.setProveedorAuth("GOOGLE");
                usuarioDAO.update(usuario);
            }
            motorLogrosService.evaluarTrasLoginGoogle(usuario);
            return usuario;
        }

        // Usuario nuevo vía Google
        String username = email.split("@")[0] + "_" + System.currentTimeMillis() % 10000;
        String contrasenaAleatoria = passwordEncoder.encode(java.util.UUID.randomUUID().toString());

        Usuario nuevoUsuario = new Usuario(nombre, username, email, contrasenaAleatoria);
        nuevoUsuario.setProveedorAuth("GOOGLE");
        nuevoUsuario.setFechaRegistro(java.time.LocalDateTime.now());
        usuarioDAO.save(nuevoUsuario);

        try {
            emailService.enviarEmailBienvenida(nuevoUsuario.getEmail(), nuevoUsuario.getNombre());
        } catch (Exception e) {
            System.out.println("Error al enviar email de bienvenida: " + e.getMessage());
        }

        motorLogrosService.evaluarTrasLoginGoogle(nuevoUsuario);
        return nuevoUsuario;
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
        // El resto (contrasena, proveedorAuth, fcmToken, fechaRegistro...)
        // se conservan tal cual están en la BD.
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

    public void actualizarFcmToken(int id, String fcmToken) {
        Usuario usuario = usuarioDAO.findById(id);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado");
        }
        usuario.setFcmToken(fcmToken);
        usuarioDAO.update(usuario);
    }

    public void eliminarCuenta(int id) {
        Usuario usuario = usuarioDAO.findById(id);
        if (usuario == null) {
            throw new RuntimeException("Usuario no encontrado");
        }

        // 1. Borrar registros y rachas de todos sus hábitos
        List<Habito> habitos = habitoDAO.findByPropietario(usuario);
        for (Habito habito : habitos) {
            registroDAO.deleteByHabito(habito.getHabitoId());
            rachaDAO.deleteByHabito(habito.getHabitoId());
        }

        // 2. Borrar los hábitos
        for (Habito habito : habitos) {
            habitoDAO.delete(habito.getHabitoId());
        }

        // 3. Borrar categorías personalizadas del usuario
        List<Categoria> categorias = categoriaDAO.findByCreador(usuario);
        for (Categoria categoria : categorias) {
            categoriaDAO.delete(categoria.getCategoriaId());
        }

        // 4. Borrar el usuario
        usuarioDAO.delete(id);
    }
}