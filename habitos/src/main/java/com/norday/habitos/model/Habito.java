package com.norday.habitos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "habito")
public class Habito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "habito_id")
    private int habitoId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "frecuencia", nullable = false)
    private Frecuencia frecuencia;

    @Column(name = "meta")
    private int meta;

    // Días planificados (solo SEMANAL): números ISO separados por comas,
    // 1=lunes ... 7=domingo (ej. "2,4,6" = martes, jueves y sábado).
    // Coincide con DayOfWeek.getValue() en Java y DateTime.weekday en Dart.
    // null o vacío = semanal flexible (sin días concretos).
    @Column(name = "dias_semana", length = 20)
    private String diasSemana;

    // En qué se mide el objetivo (ver Unidad). Con su minutos_objetivo, es
    // parte de la copia del objetivo vigente hoy, como frecuencia y meta.
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'VECES'")
    @Column(name = "unidad", nullable = false, length = 10)
    private Unidad unidad = Unidad.VECES;

    // Minutos por día de un hábito de minutos; null en uno de veces.
    @Column(name = "minutos_objetivo")
    private Integer minutosObjetivo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    // Cuándo se creó el hábito (UTC, como Usuario.fechaRegistro). Lo rellena
    // @PrePersist para cubrir cualquier camino de creación.
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // Si true, el hábito participa en el barrido de recordatorios push.
    // Default true: todo hábito nuevo empieza con recordatorio activado.

    @Column(name = "recordatorio_activo", nullable = false)
    private boolean recordatorioActivo = true;

    // Hora del día a la que debe llegar el recordatorio (redondeada a
    // múltiplos de 5 minutos por el propio formulario/selector de hora).
    // null = sin hora elegida todavía; no se envía recordatorio aunque
    // recordatorioActivo sea true.

    @Column(name = "recordatorio_hora")
    private java.time.LocalTime recordatorioHora;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    // El propietario no tiene por qué viajar dentro de cada hábito: el cliente
    // ya sabe de quién son, los pidió con su propio usuarioId. Además, al ser
    // LAZY, serializarlo lanzaría el mismo fallo de proxy que tuvo `tipo`.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id", nullable = false, foreignKey = @ForeignKey(name = "FK_habito_usuario"))
    private Usuario propietario;

    // LAZY: la entidad ya no sale a JSON. Los endpoints devuelven HabitoDTO,
    // que copia de aquí categoriaId, codigo y nombre.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_id", foreignKey = @ForeignKey(name = "FK_habito_categoria"))
    private Categoria tipo;

    // Constructor vacío — obligatorio para JPA
    public Habito() {}

    // Constructor con parámetros. La fecha de inicio la aporta quien crea el
    // hábito: es el "hoy" del usuario en su zona, no el del servidor.
    public Habito(String nombre, String descripcion, Frecuencia frecuencia, int meta,
                  Usuario propietario, Categoria tipo, LocalDate fechaInicio) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.frecuencia = frecuencia;
        this.meta = meta;
        this.propietario = propietario;
        this.tipo = tipo;
        this.fechaInicio = fechaInicio;
        this.activo = true;
    }

    public int getHabitoId() { return habitoId; }
    public void setHabitoId(int habitoId) { this.habitoId = habitoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }

    public Unidad getUnidad() { return unidad; }
    public void setUnidad(Unidad unidad) { this.unidad = unidad; }

    public Integer getMinutosObjetivo() { return minutosObjetivo; }
    public void setMinutosObjetivo(Integer minutosObjetivo) { this.minutosObjetivo = minutosObjetivo; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Usuario getPropietario() { return propietario; }
    public void setPropietario(Usuario propietario) { this.propietario = propietario; }

    public Categoria getTipo() { return tipo; }
    public void setTipo(Categoria tipo) { this.tipo = tipo; }

    public boolean isRecordatorioActivo() { return recordatorioActivo; }
    public void setRecordatorioActivo(boolean recordatorioActivo) { this.recordatorioActivo = recordatorioActivo; }

    public java.time.LocalTime getRecordatorioHora() { return recordatorioHora; }
    public void setRecordatorioHora(java.time.LocalTime recordatorioHora) { this.recordatorioHora = recordatorioHora; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    /**
     * El hábito tal como era con otro objetivo: una copia con la unidad, la
     * frecuencia, la meta, los minutos y los días fijos dados y todo lo demás
     * igual. Sirve para juzgar
     * un día pasado con las reglas que tenía ({@link HistorialObjetivos}).
     *
     * Es una vista y no se guarda nunca: no tiene contexto de persistencia y
     * pasarla a un DAO pisaría el objetivo vigente. Si el objetivo es el
     * mismo, devuelve este mismo hábito.
     */
    public Habito conObjetivo(Objetivo objetivo) {
        if (objetivo.equals(Objetivo.de(this))) return this;
        Habito vista = new Habito();
        vista.habitoId = habitoId;
        vista.nombre = nombre;
        vista.descripcion = descripcion;
        vista.unidad = objetivo.unidad();
        vista.minutosObjetivo = objetivo.minutosObjetivo();
        vista.frecuencia = objetivo.frecuencia();
        vista.meta = objetivo.meta();
        vista.diasSemana = objetivo.diasSemana();
        vista.fechaInicio = fechaInicio;
        vista.fechaCreacion = fechaCreacion;
        vista.recordatorioActivo = recordatorioActivo;
        vista.recordatorioHora = recordatorioHora;
        vista.activo = activo;
        vista.propietario = propietario;
        vista.tipo = tipo;
        return vista;
    }

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public String toString() {
        return "Habito{habitoId=" + habitoId + ", nombre='" + nombre + "', activo=" + activo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Habito habito = (Habito) o;
        return habitoId == habito.habitoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(habitoId);
    }
}