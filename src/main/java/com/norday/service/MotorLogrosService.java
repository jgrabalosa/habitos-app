package com.norday.service;

import com.norday.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Motor genérico de logros: solo eventos que cualquier app del ecosistema
 * puede disparar (cuenta, perfil, reseña). Los logros ligados a un dominio
 * concreto viven en el servicio de logros de ese dominio.
 */
@Service
public class MotorLogrosService {

    @Autowired
    private LogroService logroService;

    // ── Evento: login con Google ─────────────────────────────
    public void evaluarTrasLoginGoogle(Usuario usuario) {
        logroService.otorgarSiNoTiene(usuario, "LOGIN_GOOGLE");
    }

    // ── Evento: actualizar perfil ────────────────────────────
    public void evaluarTrasActualizarPerfil(Usuario usuario) {
        logroService.otorgarSiNoTiene(usuario, "BIENVENIDO");
    }

    // ── Evento: interacción con reseña (llamado desde Flutter) ──
    public void evaluarTrasInteraccionResena(Usuario usuario) {
        logroService.otorgarSiNoTiene(usuario, "INTERACCION_RESENA");
    }
}
