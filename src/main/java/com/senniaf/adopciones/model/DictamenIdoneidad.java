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
 * Dictamen de idoneidad de una familia solicitante (HU-06.1, HU-06.2 y HU-06.4).
 *
 * Es una tabla aparte y no un par de columnas en FamiliaSolicitante
 * porque un dictamen se puede sustituir (HU-06.4) y el anterior tiene que
 * conservarse. Con columnas en la familia, el dictamen nuevo pisaria al viejo.
 *
 * No tiene setters: una vez emitido, ni el resultado ni la observacion se
 * pueden cambiar desde el codigo. Lo unico que le puede pasar a un
 * dictamen es que lo sustituya otro (metodo sustituir), y eso solo va en
 * una direccion: de vigente a sustituido, nunca al reves.
 *
 * Sprint 7: cada dictamen guarda su periodo de vigencia. Desde
 * fechaDictamen hasta fechaSustitucion (vacia mientras este vigente). Asi
 * queda registrado, por ejemplo, desde cuando y hasta cuando una familia
 * fue No idonea antes de que el Administrador cambiara el dictamen.
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

    @Lob
    @Column(nullable = false, length = 2000)
    private String observacion;

    /**
     * Solo existe si el resultado es IDONEA. Si un dictamen cambia a
     * IDONEA, la fecha es la del cambio (decision del PO, Sprint 7): la
     * familia entra a la lista de espera ese dia, no antes.
     */
    @Column(name = "fecha_idoneidad")
    private LocalDate fechaIdoneidad;

    /** Inicio de la vigencia. */
    @Column(name = "fecha_dictamen", nullable = false)
    private LocalDateTime fechaDictamen;

    @Column(name = "emitido_por", nullable = false, length = 60)
    private String emitidoPor;

    @Column(name = "rol_emisor", nullable = false, length = 20)
    private String rolEmisor;

    /** Solo un dictamen vigente por familia. */
    @Column(nullable = false)
    private boolean vigente = true;

    // ------------------------- Sprint 7 · HU-06.4 -------------------------

    /**
     * Por que se cambio el dictamen anterior. Vacia en un dictamen
     * emitido por primera vez; obligatoria en uno que sustituye a otro.
     */
    @Lob
    @Column(name = "justificacion_cambio", length = 1000)
    private String justificacionCambio;

    /** Fin de la vigencia: cuando otro dictamen lo sustituyo. */
    @Column(name = "fecha_sustitucion")
    private LocalDateTime fechaSustitucion;

    /** Quien lo sustituyo (siempre un Administrador). */
    @Column(name = "sustituido_por", length = 60)
    private String sustituidoPor;

    protected DictamenIdoneidad() {
        // Requerido por JPA
    }

    /** Primer dictamen de una familia (HU-06.1). */
    public DictamenIdoneidad(FamiliaSolicitante familia, ResultadoIdoneidad resultado, String observacion,
                             String emitidoPor, String rolEmisor) {
        this(familia, resultado, observacion, emitidoPor, rolEmisor, null);
    }

    /** Dictamen que sustituye a otro (HU-06.4): lleva la justificacion del cambio. */
    public DictamenIdoneidad(FamiliaSolicitante familia, ResultadoIdoneidad resultado, String observacion,
                             String emitidoPor, String rolEmisor, String justificacionCambio) {
        this.familia = familia;
        this.resultado = resultado;
        this.observacion = observacion;
        this.emitidoPor = emitidoPor;
        this.rolEmisor = rolEmisor;
        this.justificacionCambio = justificacionCambio;
        this.fechaDictamen = LocalDateTime.now();
        // HU-06.1, criterio 3: la fecha de idoneidad la pone el sistema, no el usuario.
        this.fechaIdoneidad = resultado == ResultadoIdoneidad.IDONEA ? LocalDate.now() : null;
        this.vigente = true;
    }

    /**
     * HU-06.4, criterio 1: el dictamen no se borra ni se reescribe, se
     * marca como sustituido y conserva todo lo que decia.
     */
    public void sustituir(String usuario) {
        if (!vigente) {
            throw new IllegalStateException("El dictamen ya estaba sustituido");
        }
        this.vigente = false;
        this.fechaSustitucion = LocalDateTime.now();
        this.sustituidoPor = usuario;
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
    public String getJustificacionCambio() { return justificacionCambio; }
    public LocalDateTime getFechaSustitucion() { return fechaSustitucion; }
    public String getSustituidoPor() { return sustituidoPor; }
}
