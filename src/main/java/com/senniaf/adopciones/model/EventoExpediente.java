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
 * Una linea del historial del expediente (HU-03, trazabilidad).
 *
 * Cada vez que alguien crea, consulta o modifica un expediente se
 * escribe una fila aqui con quien fue, con que rol y cuando. Las filas
 * no se editan ni se borran: un historial que se puede cambiar no sirve
 * como evidencia.
 */
@Entity
@Table(name = "eventos_expediente",
       indexes = @Index(name = "idx_evento_expediente", columnList = "expediente_id"))
public class EventoExpediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    /** Que paso, en palabras: "Cambio motivo_ingreso de abandono a orfandad". */
    @Lob
    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 60)
    private String usuario;

    @Column(nullable = false, length = 20)
    private String rol;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora = LocalDateTime.now();

    protected EventoExpediente() {
        // Requerido por JPA
    }

    public EventoExpediente(Expediente expediente, TipoEvento tipo, String descripcion,
                            String usuario, String rol) {
        this.expediente = expediente;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.usuario = usuario;
        this.rol = rol;
        this.fechaHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Expediente getExpediente() { return expediente; }
    public TipoEvento getTipo() { return tipo; }
    public String getDescripcion() { return descripcion; }
    public String getUsuario() { return usuario; }
    public String getRol() { return rol; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
