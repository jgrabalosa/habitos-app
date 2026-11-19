package com.norday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El bloque JSON-LD de la landing y la sección «Preguntas» visible tienen que
 * decir lo mismo: si alguien cambia una pregunta en el HTML y no en el bloque,
 * Google leería una respuesta que la página ya no da. No levanta Spring.
 */
class LandingDatosEstructuradosTest {

    private static final Pattern BLOQUE = Pattern.compile(
            "<script type=\"application/ld\\+json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern DETAILS = Pattern.compile(
            "<details[^>]*>\\s*<summary>(.*?)</summary>\\s*<p>(.*?)</p>\\s*</details>", Pattern.DOTALL);

    private String html() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/index.html")) {
            assertNotNull(in, "Falta static/index.html");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode grafo(String html) throws IOException {
        Matcher m = BLOQUE.matcher(html);
        assertTrue(m.find(), "No hay bloque application/ld+json");
        String json = m.group(1);
        assertFalse(m.find(), "Debe haber un único bloque application/ld+json");
        JsonNode raiz = new ObjectMapper().readTree(json);
        JsonNode graph = raiz.get("@graph");
        assertNotNull(graph, "Falta @graph");
        return graph;
    }

    private JsonNode nodo(JsonNode graph, String tipo) {
        for (JsonNode n : graph) {
            if (tipo.equals(n.path("@type").asText())) {
                return n;
            }
        }
        return fail("Falta el nodo " + tipo);
    }

    @Test
    void elBloqueEsJsonValidoConLosTresTipos() throws IOException {
        JsonNode graph = grafo(html());
        assertEquals(3, graph.size());

        JsonNode app = nodo(graph, "SoftwareApplication");
        JsonNode persona = nodo(graph, "Person");
        nodo(graph, "FAQPage");

        assertEquals("Norday Habits", app.get("name").asText());
        assertEquals("0", app.get("offers").get("price").asText());
        assertEquals(persona.get("@id").asText(), app.get("author").get("@id").asText());

        Matcher meta = Pattern.compile("<meta name=\"description\" content=\"(.*?)\">").matcher(html());
        assertTrue(meta.find());
        assertEquals(meta.group(1), app.get("description").asText());
    }

    @Test
    void laPersonaEnlazaAlLinkedInQueYaEstaEnLaPagina() throws IOException {
        JsonNode persona = nodo(grafo(html()), "Person");
        String linkedin = persona.get("sameAs").get(0).asText();
        assertTrue(html().contains("href=\"" + linkedin + "\""), "El LinkedIn no aparece en la página");
    }

    @Test
    void lasPreguntasDelBloqueSonLasVisibles() throws IOException {
        String html = html();
        JsonNode faq = nodo(grafo(html), "FAQPage");

        List<String[]> visibles = new ArrayList<>();
        Matcher m = DETAILS.matcher(html);
        while (m.find()) {
            visibles.add(new String[]{m.group(1).trim(), m.group(2).trim()});
        }
        int totalDetails = html.split("<details", -1).length - 1;
        assertEquals(totalDetails, visibles.size(), "Hay un <details> con otra forma que el test no lee");

        JsonNode preguntas = faq.get("mainEntity");
        assertEquals(visibles.size(), preguntas.size(), "Distinto número de preguntas");
        for (int i = 0; i < visibles.size(); i++) {
            JsonNode q = preguntas.get(i);
            assertEquals("Question", q.get("@type").asText());
            assertEquals(visibles.get(i)[0], q.get("name").asText(), "Pregunta " + (i + 1));
            assertEquals(visibles.get(i)[1], q.get("acceptedAnswer").get("text").asText(), "Respuesta " + (i + 1));
        }
    }
}
