package com.norday.core;

import com.norday.core.model.AppNorday;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La cabecera X-Norday-App decide a qué token de notificaciones va cada
 * aviso. Sin ella es la APK de Norday Habits ya instalada; una app que el
 * backend no conoce no se acepta.
 */
class AppNordayTest {

    @Test
    void sinCabecera_esHabits() {
        assertEquals("habitos", AppNorday.deCabecera(null));
    }

    @Test
    void cabeceraVaciaOEnBlanco_esHabits() {
        assertEquals("habitos", AppNorday.deCabecera(""));
        assertEquals("habitos", AppNorday.deCabecera("   "));
    }

    @Test
    void lasTresAppsConocidas_sonEllasMismas() {
        assertEquals("habitos", AppNorday.deCabecera("habitos"));
        assertEquals("conocimiento", AppNorday.deCabecera("conocimiento"));
        assertEquals("enfoque", AppNorday.deCabecera("enfoque"));
        assertEquals("enfoque", AppNorday.deCabecera(" enfoque "));
    }

    @Test
    void unaAppDesconocida_daNull() {
        assertNull(AppNorday.deCabecera("otra"));
        assertNull(AppNorday.deCabecera("Enfoque"));
    }
}
