package com.norday;

import com.norday.core.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Los 24 endpoints que llevan el id del usuario en la URL, contra la cadena
 * de seguridad de verdad: filtro JWT incluido.
 *
 * No siembra datos y no los necesita. JwtFilter construye el principal a
 * partir de los claims del token sin consultar la base de datos, y el
 * control de autorizacion compara el id de la URL con el del token antes de
 * tocar nada. Un token firmado para un usuario que no existe vale igual.
 *
 * Son dos afirmaciones, no una. La primera es la obvia: con el id de otro,
 * 403. La segunda es la que impide que la primera pase por casualidad — un
 * refactor que denegara a todo el mundo dejaria los 24 primeros en verde y
 * la aplicacion inservible.
 *
 * Vive en norday-server y no en los modulos de los controladores por lo
 * mismo que UsuarioProductoDAOTest: necesita la aplicacion ensamblada, y
 * este es el unico modulo que la tiene.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AutorizacionPorUsuarioTest {

    private static final int ID_DEL_TOKEN = 1;
    private static final int ID_AJENO = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void generarToken() {
        token = jwtUtil.generateToken(ID_DEL_TOKEN, "titular@norday.local");
    }

    /**
     * Metodo y plantilla de ruta; el %d es donde va el id del usuario. El
     * segundo id de las rutas de gamificacion es el del producto y da igual
     * cual sea: la comprobacion de autorizacion es lo primero que ocurre.
     */
    static Stream<Arguments> endpointsConElUsuarioEnLaUrl() {
        return Stream.of(
                Arguments.of("GET",    "/api/usuarios/%d"),
                Arguments.of("GET",    "/api/usuarios/%d/exportar"),
                Arguments.of("GET",    "/api/usuarios/%d/preferencias"),
                Arguments.of("PUT",    "/api/usuarios/%d/preferencias"),
                Arguments.of("PUT",    "/api/usuarios/%d"),
                Arguments.of("PUT",    "/api/usuarios/%d/contrasena"),
                Arguments.of("PUT",    "/api/usuarios/%d/fcm-token"),
                Arguments.of("DELETE", "/api/usuarios/%d"),

                Arguments.of("GET",    "/api/mascota/%d"),
                Arguments.of("PUT",    "/api/mascota/%d/nombre"),

                Arguments.of("GET",    "/api/gamificacion/saldo/%d"),
                Arguments.of("GET",    "/api/gamificacion/logros/usuario/%d"),
                Arguments.of("GET",    "/api/gamificacion/productos/usuario/%d"),
                Arguments.of("POST",   "/api/gamificacion/productos/comprar/%d/1"),
                Arguments.of("POST",   "/api/gamificacion/productos/otorgar/%d/1"),
                Arguments.of("POST",   "/api/gamificacion/identidad/elegir/%d/1"),
                Arguments.of("POST",   "/api/gamificacion/productos/equipar/%d/1"),
                Arguments.of("POST",   "/api/gamificacion/productos/desequipar/%d/1"),
                Arguments.of("POST",   "/api/gamificacion/productos/usar/%d/1"),

                Arguments.of("GET",    "/api/habitos/usuario/%d/activos"),
                Arguments.of("GET",    "/api/habitos/usuario/%d/resumen"),
                Arguments.of("GET",    "/api/habitos/usuario/%d/dashboard"),
                Arguments.of("GET",    "/api/habitos/usuario/%d/semana"),

                Arguments.of("GET",    "/api/categorias/usuario/%d")
        );
    }

    /**
     * Todo lo que no es GET lleva cuerpo JSON vacio: cinco de estos
     * endpoints declaran @RequestBody y sin cuerpo Spring devolveria 400
     * antes de llegar a la comprobacion de autorizacion, que es justo lo que
     * se quiere medir. En los que no lo declaran, el cuerpo se ignora.
     */
    private MvcResult lanzar(String metodo, String plantilla, int id) throws Exception {
        MockHttpServletRequestBuilder peticion =
                request(HttpMethod.valueOf(metodo), String.format(plantilla, id))
                        .header("Authorization", "Bearer " + token);

        if (!"GET".equals(metodo)) {
            peticion = peticion.contentType(MediaType.APPLICATION_JSON).content("{}");
        }

        return mockMvc.perform(peticion).andReturn();
    }

    @ParameterizedTest(name = "{0} {1} con el id de otro usuario da 403")
    @MethodSource("endpointsConElUsuarioEnLaUrl")
    void conElIdDeOtroUsuario_devuelve403(String metodo, String plantilla) throws Exception {
        assertEquals(403, lanzar(metodo, plantilla, ID_AJENO).getResponse().getStatus());
    }

    /**
     * Sin try/catch a proposito. GlobalExceptionHandler tiene un
     * @ExceptionHandler(Exception.class) que convierte cualquier fallo en un
     * 500, asi que de mockMvc.perform nunca sale una excepcion: siempre hay
     * un codigo que mirar. Con la base de datos de test vacia estos
     * endpoints devuelven 404, 400 o 500, y todos valen — lo que se afirma
     * es solo que no fue la autorizacion quien corto la peticion. Tragarse
     * una excepcion aqui convertiria cualquier fallo futuro en un aprobado.
     */
    @ParameterizedTest(name = "{0} {1} con el id propio no da 403")
    @MethodSource("endpointsConElUsuarioEnLaUrl")
    void conElIdPropio_noDevuelve403(String metodo, String plantilla) throws Exception {
        assertNotEquals(403, lanzar(metodo, plantilla, ID_DEL_TOKEN).getResponse().getStatus());
    }
}
