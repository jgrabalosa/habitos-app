package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class UsuarioService {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    }

    public Usuario login(String email, String contrasena) {
        Usuario usuario = usuarioDAO.findByEmail(email);
        if (usuario == null) {
            throw new RuntimeException("Email no encontrado");
        }
        if (!passwordEncoder.matches(contrasena, usuario.getContrasena())) {
            throw new RuntimeException("Contraseña incorrecta");
        }
        return usuario;
    }

    public Usuario buscarPorId(int id) {
        return usuarioDAO.findById(id);
    }

    public void actualizarPerfil(Usuario usuario) {
        usuarioDAO.update(usuario);
    }

    public void eliminarCuenta(int id) {
        usuarioDAO.delete(id);
    }
}