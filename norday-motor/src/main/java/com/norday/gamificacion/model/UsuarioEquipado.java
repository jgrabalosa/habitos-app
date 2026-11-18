package com.norday.gamificacion.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Lo que lleva puesto un usuario: una fila por sitio (la categoría del
 * producto: «Tema», «Cabeza», «Cuerpo»...), con clave primaria (usuario,
 * categoría). Es la fuente de verdad de lo equipado (V29, Nori vestida N-5):
 * la base de datos impide dos cosas puestas en el mismo sitio.
 *
 * Una tabla y no un índice único parcial sobre usuario_producto: cambiar de
 * prenda con un índice deja un instante dos equipadas, y Hibernate escribe
 * los cambios en el orden en que cargó las filas. Aquí cambiar de prenda es
 * actualizar la fila del sitio, nunca dos filas.
 *
 * usuario_producto.equipado se sigue escribiendo, coherente con esta tabla,
 * porque el inventario y la exportación lo devuelven y los clientes lo leen.
 */
@Entity
@Table(name = "usuario_equipado")
public class UsuarioEquipado {

    @EmbeddedId
    private Clave id;

    @MapsId("usuarioId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_usuario_equipado_usuario"))
    private Usuario usuario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_producto_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_usuario_equipado_usuario_producto"))
    private UsuarioProducto usuarioProducto;

    public UsuarioEquipado() {}

    public Clave getId() { return id; }
    public UsuarioProducto getUsuarioProducto() { return usuarioProducto; }

    @Embeddable
    public static class Clave implements Serializable {

        @Column(name = "usuario_id")
        private int usuarioId;

        @Column(name = "categoria", length = 50)
        private String categoria;

        public Clave() {}

        public Clave(int usuarioId, String categoria) {
            this.usuarioId = usuarioId;
            this.categoria = categoria;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Clave otra)) return false;
            return usuarioId == otra.usuarioId && Objects.equals(categoria, otra.categoria);
        }

        @Override
        public int hashCode() {
            return Objects.hash(usuarioId, categoria);
        }
    }
}
