package com.norday.habitos.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * El registro de avisos que la app ha mandado a un usuario: una fila por aviso
 * enviado. Sirve para no pasarse del tope semanal y para no repetir texto.
 *
 * {@code variante} es cuál de los textos del tipo se mandó. {@code enviadoEn}
 * va en UTC (sello de auditoría, no un día de usuario); lo rellena
 * {@code @PrePersist} si no se ha puesto.
 */
@Entity
@Table(name = "aviso_app",
        indexes = @Index(name = "IX_aviso_app_usuario_enviado", columnList = "usuario_id, enviado_en"))
public class AvisoApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_aviso_app_usuario"))
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoAviso tipo;

    @Column(name = "variante", nullable = false)
    private int variante;

    @Column(name = "enviado_en", nullable = false)
    private LocalDateTime enviadoEn;

    // Constructor vacío — obligatorio para JPA
    public AvisoApp() {}

    public AvisoApp(Usuario usuario, TipoAviso tipo, int variante) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.variante = variante;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public TipoAviso getTipo() { return tipo; }
    public void setTipo(TipoAviso tipo) { this.tipo = tipo; }

    public int getVariante() { return variante; }
    public void setVariante(int variante) { this.variante = variante; }

    public LocalDateTime getEnviadoEn() { return enviadoEn; }
    public void setEnviadoEn(LocalDateTime enviadoEn) { this.enviadoEn = enviadoEn; }

    @PrePersist
    void alCrear() {
        if (enviadoEn == null) {
            enviadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public String toString() {
        return "AvisoApp{id=" + id + ", tipo=" + tipo + ", variante=" + variante + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvisoApp that = (AvisoApp) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
