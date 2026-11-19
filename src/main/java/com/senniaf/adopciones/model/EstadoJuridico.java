package com.senniaf.adopciones.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Etapa juridica del menor (HU-07.1, Sprint 8).
 *
 * No es lo mismo que el estado administrativo del expediente (Registrado,
 * En revision, Cerrado): ese dice si alguien esta trabajando el
 * expediente; esta dice en que punto del proceso legal esta el menor.
 *
 * Las transiciones solo avanzan (HU-07.2, criterio 2):
 *
 *   En proteccion -> En proceso judicial -> Declarado adoptable -> Adoptado
 *                                        \
 *                                         -> Reintegrado a su familia
 *
 * Adoptado y Reintegrado son etapas finales: no admiten mas cambios.
 * Volver atras no esta permitido (decision del PO): una correccion sin la
 * resolucion judicial del Sprint 9 dejaria un hueco legal.
 */
public enum EstadoJuridico {

    EN_PROTECCION("En protección"),
    EN_PROCESO_JUDICIAL("En proceso judicial"),
    DECLARADO_ADOPTABLE("Declarado adoptable"),
    ADOPTADO("Adoptado"),
    REINTEGRADO("Reintegrado a su familia");

    private static final Map<EstadoJuridico, Set<EstadoJuridico>> SIGUIENTES = Map.of(
            EN_PROTECCION, Set.of(EN_PROCESO_JUDICIAL),
            EN_PROCESO_JUDICIAL, Set.of(DECLARADO_ADOPTABLE, REINTEGRADO),
            DECLARADO_ADOPTABLE, Set.of(ADOPTADO),
            ADOPTADO, Set.of(),
            REINTEGRADO, Set.of());

    private final String etiqueta;

    EstadoJuridico(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Adoptado y Reintegrado cierran el proceso legal del menor. */
    public boolean esFinal() {
        return SIGUIENTES.get(this).isEmpty();
    }

    public boolean puedePasarA(EstadoJuridico otra) {
        return SIGUIENTES.get(this).contains(otra);
    }

    /** Etapas a las que se puede pasar desde esta, en el orden del enum. */
    public List<EstadoJuridico> siguientes() {
        return java.util.Arrays.stream(values()).filter(SIGUIENTES.get(this)::contains).toList();
    }

    /** Las etapas finales, para la consulta del cierre automatico. */
    public static List<EstadoJuridico> finales() {
        return java.util.Arrays.stream(values()).filter(EstadoJuridico::esFinal).toList();
    }
}
