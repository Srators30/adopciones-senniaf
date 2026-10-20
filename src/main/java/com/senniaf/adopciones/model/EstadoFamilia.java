package com.senniaf.adopciones.model;

/**
 * Estado administrativo de la solicitud de una familia.
 *
 * Es el estado del TRAMITE, no un veredicto sobre la familia. Decir si
 * una familia es apta o no corresponde a la evaluacion tecnica, que es
 * otra historia de usuario: aqui solo se sabe si la solicitud esta
 * recien registrada, en manos del equipo evaluador, o cerrada.
 */
public enum EstadoFamilia {
    REGISTRADA("Registrada"),
    EN_EVALUACION("En evaluación"),
    CERRADA("Cerrada");

    private final String etiqueta;

    EstadoFamilia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Como se escribe el estado para una persona. El historial lo lee
     * gente, no el sistema: "Registrada -> En evaluación" se entiende,
     * "REGISTRADA -> EN_EVALUACION" hay que traducirlo mentalmente.
     */
    public String getEtiqueta() {
        return etiqueta;
    }
}
