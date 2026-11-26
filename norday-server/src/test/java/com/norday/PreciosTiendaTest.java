package com.norday;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rebalanceo de precios del 24-nov-2026 (D-71): el catálogo de una base nueva
 * sale con los precios nuevos, y la V34, ejecutada tal cual está en el fichero,
 * pone al día una base con los viejos sin pisar lo que alguien cambió a mano.
 */
@SpringBootTest
@Transactional
class PreciosTiendaTest {

    @PersistenceContext
    private EntityManager em;

    private static final Map<String, Integer> NUEVOS = Map.of(
            "TEMA_PROFUNDIDAD", 1250,
            "TEMA_NEOTOKYO_PLUS", 1250,
            "TEMA_DULCE", 1250,
            "PRENDA_GORRO_LANA", 400,
            "PRENDA_BUFANDA", 400,
            "PRENDA_TRAJE_NOEL", 500,
            "PAUSA_HABITO", 350,
            "COMIDA_BASICA", 100);

    private static final Map<String, Integer> VIEJOS = Map.of(
            "TEMA_PROFUNDIDAD", 1000,
            "TEMA_NEOTOKYO_PLUS", 1000,
            "TEMA_DULCE", 1000,
            "PRENDA_GORRO_LANA", 200,
            "PRENDA_BUFANDA", 250,
            "PRENDA_TRAJE_NOEL", 400,
            "PAUSA_HABITO", 300,
            "COMIDA_BASICA", 50);

    private static final Map<String, Integer> OFERTAS_NUEVAS = Map.of(
            "PRENDA_GORRO_LANA", 400,
            "PRENDA_BUFANDA", 400,
            "PRENDA_TRAJE_NOEL", 500,
            "PRENDA_GORRO_NOEL", 0);

    @Test
    void unaBaseNueva_sale_conLosPreciosNuevos() {
        NUEVOS.forEach((codigo, precio) -> assertEquals(precio, precioProducto(codigo), codigo));
        OFERTAS_NUEVAS.forEach((codigo, precio) -> assertEquals(List.of(precio), preciosOferta(codigo), "oferta de " + codigo));
        assertEquals(0, precioProducto("PRENDA_GORRO_NOEL"));
    }

    @Test
    void laV34_poneAlDiaLosViejos_sinPisarLoCambiadoAMano_yEsIdempotente() throws Exception {
        VIEJOS.forEach((codigo, precio) -> em.createNativeQuery("UPDATE producto SET precio = :p WHERE codigo = :c")
                .setParameter("p", precio).setParameter("c", codigo).executeUpdate());
        ofertaA("PRENDA_GORRO_LANA", 200);
        ofertaA("PRENDA_BUFANDA", 250);
        ofertaA("PRENDA_TRAJE_NOEL", 400);
        // Cambiada a mano: la migración no la pisa.
        em.createNativeQuery("UPDATE producto SET precio = 900 WHERE codigo = 'TEMA_NEOTOKYO_PLUS'").executeUpdate();

        ejecutarV34();
        comprobar();
        ejecutarV34();
        comprobar();
    }

    private void comprobar() {
        NUEVOS.forEach((codigo, precio) -> {
            int esperado = codigo.equals("TEMA_NEOTOKYO_PLUS") ? 900 : precio;
            assertEquals(esperado, precioProducto(codigo), codigo);
        });
        OFERTAS_NUEVAS.forEach((codigo, precio) -> assertEquals(List.of(precio), preciosOferta(codigo), "oferta de " + codigo));
        assertEquals(0, precioProducto("PRENDA_GORRO_NOEL"));
    }

    private void ejecutarV34() throws Exception {
        String sql = new ClassPathResource("db/migration/V34__precios_tienda.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> sentencias = Arrays.stream(sql.replaceAll("(?m)^--.*$", "").split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        assertEquals(11, sentencias.size(), "ocho productos y tres ofertas");
        for (String sentencia : sentencias) {
            em.createNativeQuery(sentencia).executeUpdate();
        }
        em.clear();
    }

    private void ofertaA(String codigo, int precio) {
        em.createNativeQuery("UPDATE oferta SET precio = :p WHERE producto_id = "
                        + "(SELECT producto_id FROM producto WHERE codigo = :c)")
                .setParameter("p", precio).setParameter("c", codigo).executeUpdate();
    }

    private int precioProducto(String codigo) {
        return ((Number) em.createNativeQuery("SELECT precio FROM producto WHERE codigo = :c")
                .setParameter("c", codigo).getSingleResult()).intValue();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> preciosOferta(String codigo) {
        List<Number> filas = em.createNativeQuery("SELECT o.precio FROM oferta o JOIN producto p "
                        + "ON p.producto_id = o.producto_id WHERE p.codigo = :c")
                .setParameter("c", codigo).getResultList();
        return filas.stream().map(Number::intValue).toList();
    }
}
