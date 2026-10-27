package com.senniaf.adopciones.model;

/**
 * Resultado del dictamen de idoneidad (HU-06.1).
 *
 * Es distinto de EstadoFamilia: el estado dice en que punto esta el
 * tramite (Registrada, En evaluacion, Cerrada); el dictamen dice que se
 * decidio sobre la familia. Mezclarlos en un solo campo habria obligado
 * a elegir entre "esta en evaluacion" y "ya fue evaluada".
 */
public enum ResultadoIdoneidad {
    IDONEA("Idónea"),
    NO_IDONEA("No idónea");

    private final String etiqueta;

    ResultadoIdoneidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
