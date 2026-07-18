package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.CodigoRecuperacion;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.ICodigoRecuperacionDAO;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class RecuperacionService {

    private static final int MINUTOS_EXPIRACION = 15;
    private static final SecureRandom random = new SecureRandom();

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private ICodigoRecuperacionDAO codigoRecuperacionDAO;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Genera y envía un código de recuperación.
     * Si el email no existe o es una cuenta Google, no hace nada
     * (el controller responderá igual en todos los casos para no
     * revelar qué emails están registrados).
     */
    public void solicitarCodigo(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        Usuario usuario = usuarioDAO.findByEmail(email.trim());
        if (usuario == null || "GOOGLE".equals(usuario.getProveedorAuth())) {
            return;
        }

        // Invalidar códigos anteriores: solo hay uno vigente por email
        codigoRecuperacionDAO.invalidarCodigosDeEmail(usuario.getEmail());

        String codigo = String.format("%06d", random.nextInt(1000000));
        CodigoRecuperacion registro = new CodigoRecuperacion(
                usuario.getEmail(),
                codigo,
                LocalDateTime.now().plusMinutes(MINUTOS_EXPIRACION)
        );
        codigoRecuperacionDAO.save(registro);

        emailService.enviarEmailRecuperacion(usuario.getEmail(), codigo);
    }

    /**
     * Valida el código y establece la nueva contraseña.
     */
    public void restablecerContrasena(String email, String codigo, String contrasenaNueva) {
        if (email == null || codigo == null) {
            throw new RuntimeException("El código no es válido o ha caducado");
        }

        if (contrasenaNueva == null || contrasenaNueva.length() < 6) {
            throw new RuntimeException("La nueva contraseña debe tener al menos 6 caracteres");
        }

        String emailNormalizado = email.trim();

        CodigoRecuperacion registro =
                codigoRecuperacionDAO.findVigenteByEmailYCodigo(emailNormalizado, codigo.trim());
        if (registro == null) {
            throw new RuntimeException("El código no es válido o ha caducado");
        }

        Usuario usuario = usuarioDAO.findByEmail(emailNormalizado);
        if (usuario == null || "GOOGLE".equals(usuario.getProveedorAuth())) {
            throw new RuntimeException("El código no es válido o ha caducado");
        }

        // Marcar el código como usado (un solo uso)
        registro.setUsado(true);
        codigoRecuperacionDAO.update(registro);

        usuario.setContrasena(passwordEncoder.encode(contrasenaNueva));
        usuarioDAO.update(usuario);
    }
}