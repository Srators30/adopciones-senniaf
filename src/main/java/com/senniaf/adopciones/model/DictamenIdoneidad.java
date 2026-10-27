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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dictamen de idoneidad de una familia solicitante (HU-06.1 y HU-06.2).
 *
 * Es una tabla aparte y no un par de columnas en FamiliaSolicitante
 * porque un dictamen se va a poder sustituir (HU-06.4, Sprint 7) y el
 * anterior tiene que conservarse. Con columnas en la familia, el
 * dictamen nuevo pisaria al viejo.
 *
 * No tiene setters salvo el de "vigente": una vez emitido, ni el
 * resultado ni la observacion se pueden cambiar desde el codigo.
 */
@Entity
@Table(name = "dictamenes_idoneidad",
       indexes = @Index(name = "idx_dictamen_familia", columnList = "familia_id"))
public class DictamenIdoneidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private FamiliaSolicitante familia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoIdoneidad resultado;

    /** HU-06.2: obligatoria siempre; al menos 30 caracteres si es NO_IDONEA. */
    @Lob
    @Column(nullable = false, length = 2000)
    private String observacion;

    /** Fecha en que la familia quedo idonea. Solo existe si el resultado es IDONEA. */
    @Column(name = "fecha_idoneidad")
    private LocalDate fechaIdoneidad;

    @Column(name = "fecha_dictamen", nullable = false)
    private LocalDateTime fechaDictamen;

    @Column(name = "emitido_por", nullable = false, length = 60)
    private String emitidoPor;

    @Column(name = "rol_emisor", nullable = false, length = 20)
    private String rolEmisor;

    /**
     * Solo un dictamen vigente por familia. En este sprint no hay forma de
     * sustituirlo (el servicio responde 409); el campo existe desde ya
     * para que HU-06.4 no tenga que migrar la tabla.
     */
    @Column(nullable = false)
    private boolean vigente = true;

    protected DictamenIdoneidad() {
        // Requerido por JPA
    }

    public DictamenIdoneidad(FamiliaSolicitante familia, ResultadoIdoneidad resultado, String observacion,
                             String emitidoPor, String rolEmisor) {
        this.familia = familia;
        this.resultado = resultado;
        this.observacion = observacion;
        this.emitidoPor = emitidoPor;
        this.rolEmisor = rolEmisor;
        this.fechaDictamen = LocalDateTime.now();
        // HU-06.1, criterio 3: la fecha de idoneidad la pone el sistema, no el usuario.
        this.fechaIdoneidad = resultado == ResultadoIdoneidad.IDONEA ? LocalDate.now() : null;
        this.vigente = true;
    }

    public Long getId() { return id; }
    public FamiliaSolicitante getFamilia() { return familia; }
    public ResultadoIdoneidad getResultado() { return resultado; }
    public String getObservacion() { return observacion; }
    public LocalDate getFechaIdoneidad() { return fechaIdoneidad; }
    public LocalDateTime getFechaDictamen() { return fechaDictamen; }
    public String getEmitidoPor() { return emitidoPor; }
    public String getRolEmisor() { return rolEmisor; }
    public boolean isVigente() { return vigente; }
}
