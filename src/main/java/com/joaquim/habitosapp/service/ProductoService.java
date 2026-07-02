package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Producto;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioProducto;
import com.joaquim.habitosapp.repository.IProductoDAO;
import com.joaquim.habitosapp.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProductoService {

    @Autowired
    private IProductoDAO productoDAO;

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    public List<Producto> catalogoActivo() {
        return productoDAO.findActivos();
    }

    public List<UsuarioProducto> inventarioDeUsuario(Usuario usuario) {
        return usuarioProductoDAO.findByUsuario(usuario);
    }

    public void comprarProducto(Usuario usuario, int productoId) {
        Producto producto = productoDAO.findById(productoId);
        if (producto == null || !producto.isActivo()) {
            throw new RuntimeException("Producto no disponible");
        }

        int saldoActual = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
        if (saldoActual < producto.getPrecio()) {
            throw new RuntimeException("Saldo insuficiente");
        }

        UsuarioProducto existente = usuarioProductoDAO.findByUsuarioYProducto(
                usuario.getUsuarioId(), productoId);

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
}