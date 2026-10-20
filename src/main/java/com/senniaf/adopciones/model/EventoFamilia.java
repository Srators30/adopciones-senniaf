package com.senniaf.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Una linea del historial de la solicitud (HU-05, trazabilidad).
 *
 * Mismo diseno que EventoExpediente del Sprint 3: sin setters, para que
 * una vez escrita la fila el codigo no pueda cambiarla. Reutiliza
 * TipoEvento, que ya existe.
 *
 * Se eligio una tabla aparte y no reutilizar eventos_expediente porque
 * esa tiene una llave foranea obligatoria al expediente. Unificar las
 * dos en una sola tabla de auditoria es una mejora razonable para mas
 * adelante, cuando haya una tercera entidad que auditar.
 */
@Entity
@Table(name = "eventos_familia",
       indexes = @Index(name = "idx_evento_familia", columnList = "familia_id"))
public class EventoFamilia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private FamiliaSolicitante familia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    @Lob
    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(nullable = false, length = 20)
    private String rol;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected EventoFamilia() {
        // Requerido por JPA
    }

    public EventoFamilia(FamiliaSolicitante familia, TipoEvento tipo, String descripcion,
                         String usuario, String rol) {
        this.familia = familia;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.usuario = usuario;
        this.rol = rol;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public FamiliaSolicitante getFamilia() { return familia; }
    public TipoEvento getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getUsuario() { return usuario; }
    public String getRol() { return rol; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
