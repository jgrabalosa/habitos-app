package com.norday.core.controller;

import com.norday.core.repository.IVersionAppDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Público: la app lo consulta al arrancar y al volver a primer plano, antes
 * de tener sesión.
 */
@RestController
@RequestMapping("/api/version")
public class VersionController {

    @Autowired
    private IVersionAppDAO versionAppDAO;

    @GetMapping("/{appId}")
    public Map<String, Integer> versiones(@PathVariable String appId) {
        IVersionAppDAO.Versiones v = versionAppDAO.versiones(appId);
        // Map.of no admite nulos y la recomendada puede serlo: la clave sale
        // siempre, con null cuando no hay aviso. versionMinima no cambia.
        Map<String, Integer> respuesta = new LinkedHashMap<>();
        respuesta.put("versionMinima", v.minima());
        respuesta.put("versionRecomendada", v.recomendada());
        return respuesta;
    }
}
