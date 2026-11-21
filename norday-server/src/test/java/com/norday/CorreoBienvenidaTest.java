package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.CorreoBienvenidaService;
import com.norday.core.service.EmailService;
import com.norday.core.service.TextosService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El correo de bienvenida (T-044): plantilla HTML con Nori, en el idioma del
 * usuario, con las imágenes dentro del correo. Vive en norday-server porque
 * necesita juntos el motor, el bundle de la app y la plantilla.
 */
class CorreoBienvenidaTest {

    private JavaMailSender mailSender;
    private CorreoBienvenidaService servicio;
    private TextosService textos;

    @BeforeEach
    void preparar() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames("classpath:mensajes/habitos", "classpath:mensajes/core");
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);

        textos = new TextosService();
        ReflectionTestUtils.setField(textos, "messageSource", ms);

        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));

        EmailService email = new EmailService();
        ReflectionTestUtils.setField(email, "mailSender", mailSender);
        ReflectionTestUtils.setField(email, "textosService", textos);
        ReflectionTestUtils.setField(email, "remitente", "hola@norday.test");

        servicio = new CorreoBienvenidaService(email, textos);
    }

    private static Usuario usuario(String nombre, String idioma) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setEmail("ana@ejemplo.test");
        u.setIdioma(idioma);
        return u;
    }

    private void comprobarHtmlGeneral(String html) {
        assertTrue(html.contains("cid:nori"));
        assertTrue(html.contains("cid:instagram"));
        assertTrue(html.contains("cid:tiktok"));
        assertFalse(html.contains("{{"), "marcador sin resolver");
        assertFalse(html.contains("}}"), "marcador sin resolver");
        assertFalse(html.toLowerCase().contains("base64"));
        assertFalse(html.matches("(?s).*<img[^>]*src=\"(?!cid:).*"), "imagen que no es cid:");
        assertTrue(html.getBytes(StandardCharsets.UTF_8).length < 30 * 1024, "pesa demasiado");
        assertTrue(html.contains("href=\"https://play.google.com/store/apps/details?id=com.norday.habitos\""));
        assertTrue(html.contains("href=\"https://www.instagram.com/nordayapp/\""));
        assertTrue(html.contains("href=\"https://www.tiktok.com/@nordayapp\""));
    }

    @Test
    void html_es() {
        String html = servicio.html(usuario("Ana", "es"));
        comprobarHtmlGeneral(html);
        assertTrue(html.contains("<html lang=\"es\""));
        assertTrue(html.contains("role=\"article\" aria-roledescription=\"email\" lang=\"es\""));
        assertTrue(html.contains("Hola, Ana. Soy Nori."));
        assertTrue(html.contains("Empieza por uno pequeño, como beber un vaso de agua. Te espero dentro."));
        assertTrue(html.contains("Abrir Norday Habits"));
        assertTrue(html.contains("Nori, un huevo con un brote, sonriendo"));
    }

    @Test
    void html_en() {
        String html = servicio.html(usuario("Ana", "en"));
        comprobarHtmlGeneral(html);
        assertTrue(html.contains("<html lang=\"en\""));
        assertTrue(html.contains("lang=\"en\">"));
        assertTrue(html.contains("Hi Ana, I&#39;m Nori."));
        assertTrue(html.contains("If you miss a day, that&#39;s okay"));
        assertTrue(html.contains("Open Norday Habits"));
    }

    @Test
    void html_pt() {
        String html = servicio.html(usuario("Ana", "pt"));
        comprobarHtmlGeneral(html);
        assertTrue(html.contains("<html lang=\"pt-PT\""));
        assertTrue(html.contains("role=\"article\" aria-roledescription=\"email\" lang=\"pt-PT\""));
        assertTrue(html.contains("Olá, Ana. Sou o Nori."));
        assertTrue(html.contains("Abrir o Norday Habits"));
    }

    @Test
    void elNombreSeEscapa() {
        String html = servicio.html(usuario("<b>Ana&Co", "es"));
        assertTrue(html.contains("Hola, &lt;b&gt;Ana&amp;Co. Soy Nori."), html);
        assertFalse(html.contains("<b>"));
    }

    @Test
    void soloLaPrimeraPalabraDelNombre() {
        assertTrue(servicio.html(usuario("  Ana María López ", "es")).contains("Hola, Ana. Soy Nori."));
        assertTrue(servicio.texto(usuario("Ana María López", "es")).startsWith("Hola, Ana. Soy Nori."));
    }

    @Test
    void nombreDeEspaciosDaElTitularSinNombre() {
        String html = servicio.html(usuario("   ", "es"));
        assertTrue(html.contains(">Hola, soy Nori.<"));
        assertEquals("Hi, I'm Nori.", servicio.texto(usuario(null, "en")).lines().findFirst().orElseThrow());
    }

    @Test
    void enIngles_elApostrofoSobrevive() {
        String texto = servicio.texto(usuario("Ana", "en"));
        assertTrue(texto.startsWith("Hi Ana, I'm Nori."), texto);
        assertFalse(texto.contains("Im Nori"));
        assertFalse(servicio.html(usuario("Ana", "en")).contains("Im Nori"));
    }

    @Test
    void textoPlano_mismosTextosEnOrden() {
        String t = servicio.texto(usuario("Ana", "es"));
        List<String> orden = List.of(
                "Hola, Ana. Soy Nori.",
                "Te doy la bienvenida a Norday Habits.",
                "Si un día no llegas",
                "Empieza por uno pequeño",
                "Abrir Norday Habits: https://play.google.com/store/apps/details?id=com.norday.habitos",
                "Nori 🌱",
                "Me tienes también en Instagram y TikTok.",
                "https://www.instagram.com/nordayapp/",
                "https://www.tiktok.com/@nordayapp",
                "Te llega este correo porque acabas de crear tu cuenta en Norday Habits.");
        int desde = 0;
        for (String fragmento : orden) {
            int pos = t.indexOf(fragmento, desde);
            assertTrue(pos >= 0, "Falta o desordenado: " + fragmento + "\n" + t);
            desde = pos;
        }
    }

    @Test
    void mensajeMontado() throws Exception {
        servicio.enviar(usuario("Ana", "en"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage m = captor.getValue();
        m.saveChanges();

        assertEquals("Welcome! I'm Nori 🌱", m.getSubject());
        InternetAddress from = (InternetAddress) m.getFrom()[0];
        assertEquals("hola@norday.test", from.getAddress());
        assertEquals("Nori from Norday Habits", from.getPersonal());
        assertEquals("ana@ejemplo.test", m.getAllRecipients()[0].toString());

        List<Part> hojas = new ArrayList<>();
        recoger(m, hojas);
        long plano = hojas.stream().filter(p -> esTipo(p, "text/plain")).count();
        long html = hojas.stream().filter(p -> esTipo(p, "text/html")).count();
        assertEquals(1, plano);
        assertEquals(1, html);

        List<String> cids = new ArrayList<>();
        for (Part p : hojas) {
            if (esTipo(p, "image/png")) {
                cids.add(p.getHeader("Content-ID")[0]);
            }
        }
        assertEquals(3, cids.size());
        assertTrue(cids.containsAll(List.of("<nori>", "<instagram>", "<tiktok>")), cids.toString());

        assertTrue(m.getContentType().toLowerCase().startsWith("multipart/related"), m.getContentType());
    }

    /**
     * En staging y producción {@code norday.mail.from} lleva nombre
     * ({@code Norday <hola@norday.app>}). El correo de bienvenida usa solo la
     * dirección y le pone su propio nombre; antes salía
     * {@code Nori de Norday Habits <Norday <hola@norday.app>>} y no se enviaba.
     */
    @Test
    void remitenteConNombre_seUsaSoloLaDireccion() throws Exception {
        EmailService email = new EmailService();
        ReflectionTestUtils.setField(email, "mailSender", mailSender);
        ReflectionTestUtils.setField(email, "textosService", textos);
        ReflectionTestUtils.setField(email, "remitente", "Norday <hola@norday.test>");
        new CorreoBienvenidaService(email, textos).enviar(usuario("Ana", "es"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        InternetAddress from = (InternetAddress) captor.getValue().getFrom()[0];
        assertEquals("hola@norday.test", from.getAddress());
        assertEquals("Nori de Norday Habits", from.getPersonal());
    }

    private static boolean esTipo(Part p, String tipo) {
        try {
            return p.isMimeType(tipo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void recoger(Part p, List<Part> hojas) throws Exception {
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart b = mp.getBodyPart(i);
                recoger(b, hojas);
            }
        } else {
            hojas.add(p);
        }
    }
}
