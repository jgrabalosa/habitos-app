package com.norday.core.controller;

import com.norday.core.repository.IVersionAppDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Map<String, Integer> versionMinima(@PathVariable String appId) {
        return Map.of("versionMinima", versionAppDAO.versionMinima(appId));
    }
}
