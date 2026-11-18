package com.norday.gamificacion.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.model.dto.OfertaDTO;
import com.norday.gamificacion.model.dto.ProductoCatalogoDTO;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioEquipadoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ProductoService.class);

    /** Las cuatro identidades comparten esta categoría en el catálogo. */
    private static final String CATEGORIA_IDENTIDAD = "Tema";

    /** XP que da consumir una comida. El cliente lo recibe en la respuesta
     *  de `usarProducto`, no lo cablea: ver `xpGanada`. */
    private static final int XP_POR_COMIDA = 15;

    /** La que se otorga si algo falla y el usuario se queda sin ninguna. */
    private static final String CODIGO_IDENTIDAD_POR_DEFECTO = "TEMA_PROFUNDIDAD";

    /** La que se regala al elegir identidad en el onboarding. */
    private static final String CODIGO_COMIDA_BIENVENIDA = "COMIDA_BASICA";

    @Autowired
    private IProductoDAO productoDAO;

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private LogroService logroService;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private IOfertaDAO ofertaDAO;

    @Autowired
    private IUsuarioEquipadoDAO usuarioEquipadoDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private RelojTienda reloj;

    /**
     * Bloquea al usuario hasta el final de la transacción (ver
     * IUsuarioDAO.bloquear). Es la primera línea de todo lo que toca su saldo,
     * su inventario o sus logros (equipar una identidad concede el suyo): comprar lee el saldo y lo gasta, los consumibles se
     * leen y se escriben menos uno, y quien mide «saldo después − saldo
     * antes» de algo del usuario (el completado de una app) no puede tener
     * una compra en medio. Sin el bloqueo, dos compras a la vez pasaban las
     * dos con saldo para una, y una compra en medio de un completado salía
     * gratis al deshacerlo.
     *
     * Va aquí y no en UsuarioMonedaService.registrarMovimiento: ahí llegaría
     * tarde (después de leer) y en desorden (quien bloquea al usuario lo hace
     * antes que nada más). Si la fila ya no está (una baja), 404.
     */
    private void bloquearUsuario(Usuario usuario) {
        if (!usuarioDAO.bloquear(usuario.getUsuarioId())) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
    }

    /**
     * El logro que corresponde a un tema, derivado de su código:
     * TEMA_PROFUNDIDAD → IDENTIDAD_PROFUNDIDAD. Se deriva y no se escribe a
     * mano para que un quinto tema traiga su logro sin tocar código.
     *
     * No hace nada si el producto no es de categoría Tema. Es deliberado:
     * los cuatro caminos que otorgan productos —compra, regalo, elección del
     * onboarding y la red de seguridad de `asegurarIdentidad`— pasan por
     * aquí y sólo las identidades tienen logro.
     */
    private List<String> otorgarLogroDeIdentidad(Usuario usuario, Producto producto) {
        if (!CATEGORIA_IDENTIDAD.equals(producto.getCategoria())) {
            return List.of();
        }
        String codigo = producto.getCodigo();
        if (codigo == null || !codigo.startsWith("TEMA_")) {
            return List.of();
        }
        String codigoLogro = "IDENTIDAD_" + codigo.substring("TEMA_".length());
        // otorgarSiNoTiene devuelve false si el usuario ya lo tenía: sólo se
        // celebra lo que se acaba de conseguir.
        return logroService.otorgarSiNoTiene(usuario, codigoLogro)
                ? List.of(codigoLogro)
                : List.of();
    }

    public List<Producto> catalogoActivo() {
        return productoDAO.findActivos();
    }

    /**
     * Catálogo visible para una app: lo compartido más lo suyo.
     *
     * Sin [appId] devuelve el catálogo entero, que es lo que hacía antes de
     * que existiera el alcance. Así una app que todavía no manda la cabecera
     * —la que está en test cerrado— sigue viendo lo mismo que veía.
     */
    public List<Producto> catalogoActivo(String appId) {
        if (appId == null || appId.isBlank()) {
            return catalogoActivo();
        }
        return productoDAO.findActivosParaApp(appId);
    }

    /**
     * El catálogo que ve el cliente: cada producto con sus ofertas que aún no
     * han terminado (hasta >= hoy en UTC), ordenadas por desde. Un producto
     * con ofertas y todas terminadas no sale; uno sin ofertas sale con la
     * lista vacía, como siempre. Las ofertas de todos los productos se leen en
     * una sola consulta.
     *
     * Cuenta en UTC y la compra en la zona del usuario: cerca de medianoche
     * alguien puede ver una oferta y recibir «Fuera de temporada» (03,
     * «Ofertas»).
     */
    public List<ProductoCatalogoDTO> catalogoConOfertas(String appId) {
        return catalogoConOfertas(appId, null);
    }

    /**
     * Igual, para un cliente con el core [versionCore] (cabecera
     * X-Norday-Core): un producto con versión mínima solo sale si la cabecera
     * se lee y la alcanza (VersionCore.alcanza). Sin cabecera, no sale.
     */
    public List<ProductoCatalogoDTO> catalogoConOfertas(String appId, String versionCore) {
        List<Producto> productos = catalogoActivo(appId).stream()
                .filter(p -> VersionCore.alcanza(versionCore, p.getVersionMinimaCore()))
                .toList();
        Map<Integer, List<OfertaDTO>> ofertas = ofertaDAO.deProductos(
                        productos.stream().map(Producto::getProductoId).toList())
                .stream()
                .collect(Collectors.groupingBy(OfertaDTO::productoId));
        LocalDate hoyUtc = ofertas.isEmpty() ? null : LocalDate.ofInstant(reloj.ahora(), ZoneOffset.UTC);
        return productos.stream()
                .<ProductoCatalogoDTO>mapMulti((producto, salida) -> {
                    List<OfertaDTO> suyas = ofertas.getOrDefault(producto.getProductoId(), List.of());
                    if (suyas.isEmpty()) {
                        salida.accept(new ProductoCatalogoDTO(producto, List.of()));
                        return;
                    }
                    List<OfertaDTO> abiertas = suyas.stream()
                            .filter(o -> !o.hasta().isBefore(hoyUtc))
                            .sorted(Comparator.comparing(OfertaDTO::desde))
                            .toList();
                    if (!abiertas.isEmpty()) {
                        salida.accept(new ProductoCatalogoDTO(producto, abiertas));
                    }
                })
                .toList();
    }

    /**
     * La oferta vigente de una lista para un día: la que lo incluye y, si hay
     * más de una, la de desde más reciente.
     */
    static Optional<OfertaDTO> vigente(List<OfertaDTO> ofertas, LocalDate dia) {
        return ofertas.stream()
                .filter(o -> o.incluye(dia))
                .max(Comparator.comparing(OfertaDTO::desde));
    }

    public List<UsuarioProducto> inventarioDeUsuario(Usuario usuario) {
        return usuarioProductoDAO.findByUsuario(usuario);
    }

    public Producto buscarPorId(int productoId) {
        return productoDAO.findById(productoId);
    }

    /** Comprar sin decir la versión del core: como una app sin X-Norday-Core. */
    @Transactional
    public List<String> comprarProducto(Usuario usuario, int productoId) {
        return comprarProducto(usuario, productoId, null);
    }

    /**
     * Comprar desde un cliente con el core [versionCore] (cabecera
     * X-Norday-Core). Lo que el catálogo no le daría tampoco se le vende:
     * «Actualiza la app».
     */
    @Transactional
    public List<String> comprarProducto(Usuario usuario, int productoId, String versionCore) {
        bloquearUsuario(usuario);
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }
        if (!VersionCore.alcanza(versionCore, producto.getVersionMinimaCore())) {
            throw new ConflictoException("Actualiza la app");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

        if (existente != null && "EQUIPABLE".equals(producto.getTipo())) {
            throw new ConflictoException("Ya tienes este producto");
        }

        // Con ofertas, solo dentro de una vigente (el día en la zona del
        // usuario) y a su precio; sin ofertas, como siempre.
        int precio = producto.getPrecio();
        boolean regalo = false;
        List<OfertaDTO> ofertas = ofertaDAO.deProducto(productoId);
        if (!ofertas.isEmpty()) {
            LocalDate hoy = LocalDate.ofInstant(reloj.ahora(), zonaUsuarioService.zonaDe(usuario));
            OfertaDTO oferta = vigente(ofertas, hoy)
                    .orElseThrow(() -> new ConflictoException("Fuera de temporada"));
            precio = oferta.precio();
            regalo = precio == 0;
        }

        // Un regalo no mira el saldo: puede ser negativo (deshacer un
        // completado lo compensa con un movimiento de signo contrario) y
        // «saldo < 0» lo rechazaría.
        if (!regalo) {
            int saldoActual = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
            if (saldoActual < precio) {
                throw new RuntimeException("Saldo insuficiente");
            }
        }

        if (existente != null && "CONSUMIBLE".equals(producto.getTipo())) {
            existente.setCantidad(existente.getCantidad() + 1);
            usuarioProductoDAO.update(existente);
        } else {
            UsuarioProducto nuevo = new UsuarioProducto(usuario, producto, 1);
            usuarioProductoDAO.save(nuevo);
        }

        if (regalo) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, 0, "REGALO", productoId,
                    "Regalo: " + producto.getNombre()
            );
        } else {
            usuarioMonedaService.registrarMovimiento(
                    usuario, -precio, "COMPRA", productoId,
                    "Compra: " + producto.getNombre()
            );
        }

        return List.of();
    }

    @Transactional
    public void otorgarProducto(Usuario usuario, int productoId) {
        bloquearUsuario(usuario);
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

        if (existente != null && "EQUIPABLE".equals(producto.getTipo())) {
            throw new ConflictoException("Ya tienes este producto");
        }

        if (existente != null && "CONSUMIBLE".equals(producto.getTipo())) {
            existente.setCantidad(existente.getCantidad() + 1);
            usuarioProductoDAO.update(existente);
        } else {
            UsuarioProducto nuevo = new UsuarioProducto(usuario, producto, 1);
            usuarioProductoDAO.save(nuevo);
        }

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", productoId,
                "Regalo: " + producto.getNombre()
        );

        // Hoy esta llamada no se ejecuta nunca con una identidad:
        // GamificacionController rechaza cualquier categoría que no sea
        // "Avatar" antes de llegar aquí. Se conserva a propósito. Este método
        // es público y si el controlador dejara pasar un Tema, quitarla
        // significaría dejar de otorgar el logro en silencio, que es
        // exactamente el tipo de fallo que no da la cara.
        otorgarLogroDeIdentidad(usuario, producto);
    }

    @Transactional
    public List<String> equiparProducto(Usuario usuario, int productoId) {
        // Equipar una identidad concede su logro: como el resto de la tienda.
        bloquearUsuario(usuario);
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }

        Producto producto = poseido.getProducto();
        if (!"EQUIPABLE".equals(producto.getTipo())) {
            throw new RuntimeException("Este producto no es equipable");
        }

        // Lo puesto vive en usuario_equipado, una fila por sitio: cambiar de
        // prenda es actualizar esa fila. usuario_producto.equipado se deja
        // coherente porque lo leen los clientes.
        UsuarioProducto equipadoActual = usuarioProductoDAO.findEquipadoPorCategoria(
                usuario.getUsuarioId(), producto.getCategoria());
        if (equipadoActual != null
                && equipadoActual.getUsuarioProductoId() != poseido.getUsuarioProductoId()) {
            equipadoActual.setEquipado(false);
            usuarioProductoDAO.update(equipadoActual);
        }

        poseido.setEquipado(true);
        usuarioProductoDAO.update(poseido);
        usuarioEquipadoDAO.poner(usuario.getUsuarioId(), producto.getCategoria(), poseido.getUsuarioProductoId());

        return otorgarLogroDeIdentidad(usuario, producto);
    }

    /**
     * Quita lo puesto: deja vacío su sitio en usuario_equipado (solo si lo
     * puesto es esto) y equipado a false. Bloquea al usuario primero, como el
     * resto de la tienda.
     */
    @Transactional
    public void desequiparProducto(Usuario usuario, int productoId) {
        bloquearUsuario(usuario);
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }
        usuarioEquipadoDAO.quitar(usuario.getUsuarioId(), poseido.getProducto().getCategoria(),
                poseido.getUsuarioProductoId());
        poseido.setEquipado(false);
        usuarioProductoDAO.update(poseido);
    }

    /**
     * Gasta una unidad de un consumible que una app usa por su cuenta, no
     * desde Mascota. Lo llama la app en la misma operación que hace lo que
     * el producto da, para que no quede una cosa sin la otra.
     *
     * Lanza ConflictoException si no le queda ninguna. La app lo comprueba
     * antes y manda a la tienda, así que aquí solo se llega por una carrera
     * o desde una versión vieja.
     */
    @Transactional
    public void gastarUnidad(Usuario usuario, String codigo) {
        // Quien llama ya suele tener al usuario bloqueado (la pausa de un
        // hábito lo bloquea antes que nada); volver a pedirlo en la misma
        // transacción no espera. Si no, lo bloquea aquí, antes de leer.
        bloquearUsuario(usuario);
        Producto producto = productoDAO.findByCodigo(codigo);
        UsuarioProducto poseido = producto == null ? null
                : usuarioProductoDAO.findByUsuarioYProducto(
                        usuario.getUsuarioId(), producto.getProductoId());
        if (poseido == null || poseido.getCantidad() <= 0) {
            throw new ConflictoException("No te quedan unidades de " + codigo);
        }
        poseido.setCantidad(poseido.getCantidad() - 1);
        usuarioProductoDAO.update(poseido);
    }

    @Transactional
    public Map<String, Object> usarProducto(Usuario usuario, int productoId) {
        bloquearUsuario(usuario);
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }

        Producto producto = poseido.getProducto();
        if (!"CONSUMIBLE".equals(producto.getTipo())) {
            throw new RuntimeException("Este producto no es consumible");
        }

        // Desde aquí (Mascota) solo se usa la comida. Los consumibles con uso
        // propio los gasta su app con gastarUnidad, en la misma operación que
        // hacen: usados aquí se perderían sin hacer nada.
        if (!producto.getCodigo().startsWith("COMIDA_")) {
            throw new ConflictoException("Este producto no se usa desde aquí");
        }

        if (poseido.getCantidad() <= 0) {
            throw new RuntimeException("No te quedan unidades de este producto");
        }

        poseido.setCantidad(poseido.getCantidad() - 1);
        usuarioProductoDAO.update(poseido);

        String codigo = producto.getCodigo();
        boolean subioNivel = false;
        int nivelNuevo = 0;

        // Por código, no por categoría: mañana puede haber consumibles que no den XP
        int xpGanada = 0;
        if (codigo.startsWith("COMIDA_")) {
            ResultadoExperienciaDTO resultadoXp = mascotaService.ganarExperiencia(
                    usuario.getUsuarioId(), XP_POR_COMIDA);
            mascotaService.registrarComida(usuario.getUsuarioId());
            subioNivel = resultadoXp.isSubioNivel();
            nivelNuevo = resultadoXp.getNivelNuevo();
            xpGanada = XP_POR_COMIDA;
        }

        return Map.of(
                "codigoConsumido", codigo,
                "subioNivel", subioNivel,
                "nivelNuevo", nivelNuevo,
                "xpGanada", xpGanada
        );
    }

    /**
     * Identidad gratuita del onboarding. NO reutiliza otorgarProducto a
     * propósito: aquella solo impide repetir el mismo producto, así que
     * llamándola cuatro veces con códigos distintos se conseguían las cuatro
     * identidades gratis. Aquí la guarda es "¿ya tiene ALGUNA?", que es la
     * regla real: una y solo una, la primera vez.
     *
     * Equipa además de otorgar: si solo otorgara, el usuario elegiría en el
     * onboarding y la app seguiría con el aspecto por defecto. Se marca
     * equipado directamente en vez de llamar a equiparProducto porque la
     * guarda de arriba garantiza que no hay otra identidad que desequipar.
     */
    @Transactional
    public List<String> otorgarIdentidadElegida(Usuario usuario, int productoId) {
        bloquearUsuario(usuario);
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RecursoNoEncontradoException("Identidad no disponible");
        }
        if (!CATEGORIA_IDENTIDAD.equals(producto.getCategoria())) {
            throw new ConflictoException("Ese producto no es una identidad");
        }
        if (usuarioProductoDAO.poseeAlgunoDeCategoria(
                usuario.getUsuarioId(), CATEGORIA_IDENTIDAD)) {
            throw new ConflictoException("Ya tienes una identidad");
        }

        UsuarioProducto nueva = new UsuarioProducto(usuario, producto, 1);
        nueva.setEquipado(true);
        usuarioProductoDAO.save(nueva);
        usuarioEquipadoDAO.poner(usuario.getUsuarioId(), producto.getCategoria(), nueva.getUsuarioProductoId());

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", productoId,
                "Identidad de bienvenida: " + producto.getNombre()
        );

        regalarComidaDeBienvenida(usuario);

        return otorgarLogroDeIdentidad(usuario, producto);
    }

    /**
     * Una comida gratis al acabar el onboarding: su último paso señala el
     * botón de alimentar, que sin comida sale apagado. Va aquí y no en el
     * alta porque este método ya garantiza "una sola vez por cuenta" y lo
     * recorren tanto el registro por email como el de Google.
     *
     * Nunca puede tumbar la elección de identidad: si la comida no está en
     * el catálogo activo, se avisa en el log y se sigue, igual que
     * asegurarIdentidad. No reutiliza otorgarProducto porque ése lanza
     * excepción y marcaría la transacción entera.
     *
     * La red de seguridad (asegurarIdentidad) no la regala a propósito:
     * quien llega por ahí no ha visto el onboarding.
     */
    private void regalarComidaDeBienvenida(Usuario usuario) {
        Producto comida = productoDAO.findByCodigo(CODIGO_COMIDA_BIENVENIDA);
        if (comida == null || !comida.isActivo()) {
            log.warn("Comida de bienvenida: {} no está en el catálogo activo, "
                            + "el usuario {} se queda sin ella",
                    CODIGO_COMIDA_BIENVENIDA, usuario.getUsuarioId());
            return;
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), comida.getProductoId());
        if (existente != null) {
            existente.setCantidad(existente.getCantidad() + 1);
            usuarioProductoDAO.update(existente);
        } else {
            usuarioProductoDAO.save(new UsuarioProducto(usuario, comida, 1));
        }

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", comida.getProductoId(),
                "Comida de bienvenida"
        );
    }

    /**
     * Red de seguridad. Desde V10 nadie recibe tema al registrarse: si el
     * onboarding falló o se cerró a mitad, el usuario se queda sin ninguna
     * identidad y la app tira del aspecto por defecto sin que él lo posea.
     * Esto lo corrige. No debe llamarse mientras el onboarding sigue vivo
     * (ver la ventana de gracia en UsuarioService) o le robaría la elección.
     *
     * Si el código por defecto no está en el catálogo activo se avisa en el
     * log y se sigue. Es el mismo fallo silencioso que tuvo
     * otorgarTemasBasicosGratis, pero esta vez deja rastro.
     */
    @Transactional
    public void asegurarIdentidad(Usuario usuario) {
        bloquearUsuario(usuario);
        if (usuarioProductoDAO.poseeAlgunoDeCategoria(
                usuario.getUsuarioId(), CATEGORIA_IDENTIDAD)) {
            return;
        }

        Producto porDefecto = productoDAO.findByCodigo(CODIGO_IDENTIDAD_POR_DEFECTO);
        if (porDefecto == null || !porDefecto.isActivo()) {
            log.warn("Red de seguridad: {} no está en el catálogo activo, "
                            + "el usuario {} se queda sin identidad",
                    CODIGO_IDENTIDAD_POR_DEFECTO, usuario.getUsuarioId());
            return;
        }

        UsuarioProducto nueva = new UsuarioProducto(usuario, porDefecto, 1);
        nueva.setEquipado(true);
        usuarioProductoDAO.save(nueva);
        usuarioEquipadoDAO.poner(usuario.getUsuarioId(), porDefecto.getCategoria(), nueva.getUsuarioProductoId());

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", porDefecto.getProductoId(),
                "Identidad por defecto (red de seguridad)"
        );

        otorgarLogroDeIdentidad(usuario, porDefecto);

        log.warn("Red de seguridad: el usuario {} llegó al login sin identidad, "
                + "se le otorga {}", usuario.getUsuarioId(), CODIGO_IDENTIDAD_POR_DEFECTO);
    }
}