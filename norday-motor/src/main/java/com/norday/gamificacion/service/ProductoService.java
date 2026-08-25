package com.norday.gamificacion.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class ProductoService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ProductoService.class);

    /** Las cuatro identidades comparten esta categoría en el catálogo. */
    private static final String CATEGORIA_IDENTIDAD = "Tema";

    /** La que se otorga si algo falla y el usuario se queda sin ninguna. */
    private static final String CODIGO_IDENTIDAD_POR_DEFECTO = "TEMA_PROFUNDIDAD";

    @Autowired
    private IProductoDAO productoDAO;

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

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

    public List<UsuarioProducto> inventarioDeUsuario(Usuario usuario) {
        return usuarioProductoDAO.findByUsuario(usuario);
    }

    public Producto buscarPorId(int productoId) {
        return productoDAO.findById(productoId);
    }

    @Transactional
    public void comprarProducto(Usuario usuario, int productoId) {
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

        if (existente != null && "EQUIPABLE".equals(producto.getTipo())) {
            throw new ConflictoException("Ya tienes este producto");
        }

        int saldoActual = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
        if (saldoActual < producto.getPrecio()) {
            throw new RuntimeException("Saldo insuficiente");
        }

        if (existente != null && "CONSUMIBLE".equals(producto.getTipo())) {
            existente.setCantidad(existente.getCantidad() + 1);
            usuarioProductoDAO.update(existente);
        } else {
            UsuarioProducto nuevo = new UsuarioProducto(usuario, producto, 1);
            usuarioProductoDAO.save(nuevo);
        }

        usuarioMonedaService.registrarMovimiento(
                usuario, -producto.getPrecio(), "COMPRA", productoId,
                "Compra: " + producto.getNombre()
        );
    }

    @Transactional
    public void otorgarProducto(Usuario usuario, int productoId) {
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
    }

    @Transactional
    public void equiparProducto(Usuario usuario, int productoId) {
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }

        Producto producto = poseido.getProducto();
        if (!"EQUIPABLE".equals(producto.getTipo())) {
            throw new RuntimeException("Este producto no es equipable");
        }

        UsuarioProducto equipadoActual = usuarioProductoDAO.findEquipadoPorCategoria(
                usuario.getUsuarioId(), producto.getCategoria());
        if (equipadoActual != null
                && equipadoActual.getUsuarioProductoId() != poseido.getUsuarioProductoId()) {
            equipadoActual.setEquipado(false);
            usuarioProductoDAO.update(equipadoActual);
        }

        poseido.setEquipado(true);
        usuarioProductoDAO.update(poseido);
    }

    public void desequiparProducto(Usuario usuario, int productoId) {
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }
        poseido.setEquipado(false);
        usuarioProductoDAO.update(poseido);
    }

    @Transactional
    public Map<String, Object> usarProducto(Usuario usuario, int productoId) {
        UsuarioProducto poseido = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);
        if (poseido == null) {
            throw new RuntimeException("No posees este producto");
        }

        Producto producto = poseido.getProducto();
        if (!"CONSUMIBLE".equals(producto.getTipo())) {
            throw new RuntimeException("Este producto no es consumible");
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
        if (codigo.startsWith("COMIDA_")) {
            ResultadoExperienciaDTO resultadoXp = mascotaService.ganarExperiencia(usuario.getUsuarioId(), 10);
            mascotaService.registrarComida(usuario.getUsuarioId());
            subioNivel = resultadoXp.isSubioNivel();
            nivelNuevo = resultadoXp.getNivelNuevo();
        }

        return Map.of(
                "codigoConsumido", codigo,
                "subioNivel", subioNivel,
                "nivelNuevo", nivelNuevo
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
    public void otorgarIdentidadElegida(Usuario usuario, int productoId) {
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

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", productoId,
                "Identidad de bienvenida: " + producto.getNombre()
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

        usuarioMonedaService.registrarMovimiento(
                usuario, 0, "REGALO", porDefecto.getProductoId(),
                "Identidad por defecto (red de seguridad)"
        );

        log.warn("Red de seguridad: el usuario {} llegó al login sin identidad, "
                + "se le otorga {}", usuario.getUsuarioId(), CODIGO_IDENTIDAD_POR_DEFECTO);
    }
}