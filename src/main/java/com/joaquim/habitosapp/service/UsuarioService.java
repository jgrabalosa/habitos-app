package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    public void registrar(Usuario usuario) {
        Usuario existente = usuarioDAO.findByEmail(usuario.getEmail());
        if (existente != null) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }
        Usuario existenteUsername = usuarioDAO.findByUsername(usuario.getUsername());
        if (existenteUsername != null) {
            throw new RuntimeException("Ya existe un usuario con ese username");
        }
        usuarioDAO.save(usuario);
    }

    public Usuario login(String email, String contrasena) {
        Usuario usuario = usuarioDAO.findByEmail(email);
        if (usuario == null) {
            throw new RuntimeException("Email no encontrado");
        }
        if (!usuario.getContrasena().equals(contrasena)) {
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