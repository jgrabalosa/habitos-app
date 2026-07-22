package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Producto;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioProducto;
import com.joaquim.habitosapp.repository.IProductoDAO;
import com.joaquim.habitosapp.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import com.joaquim.habitosapp.model.dto.ResultadoExperienciaDTO;
import java.util.Map;

@Service
public class ProductoService {

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

    public List<UsuarioProducto> inventarioDeUsuario(Usuario usuario) {
        return usuarioProductoDAO.findByUsuario(usuario);
    }

    public void otorgarTemasBasicosGratis(Usuario usuario) {
        Producto claro = productoDAO.findByCodigo("TEMA_BASICO_CLARO");
        Producto oscuro = productoDAO.findByCodigo("TEMA_BASICO_OSCURO");
        if (claro == null || oscuro == null) {
            System.err.println("⚠️ Temas básicos no encontrados en catálogo; revisa DataInitializer.");
            return;
        }
        otorgarProducto(usuario, claro.getProductoId());
        otorgarProducto(usuario, oscuro.getProductoId());
        equiparProducto(usuario, oscuro.getProductoId());
    }

    public void comprarProducto(Usuario usuario, int productoId) {
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

        if (existente != null && "EQUIPABLE".equals(producto.getTipo())) {
            throw new RuntimeException("Ya tienes este producto");
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

    public void otorgarProducto(Usuario usuario, int productoId) {
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

        if (existente != null && "EQUIPABLE".equals(producto.getTipo())) {
            throw new RuntimeException("Ya tienes este producto");
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
}