package com.senniaf.adopciones.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HU-07.2, criterios 2 y 3: las transiciones de la etapa juridica. */
class EstadoJuridicoTest {

    @Test
    @DisplayName("HU-07.2 c2: las transiciones permitidas avanzan")
    void transicionesPermitidas() {
        assertTrue(EstadoJuridico.EN_PROTECCION.puedePasarA(EstadoJuridico.EN_PROCESO_JUDICIAL));
        assertTrue(EstadoJuridico.EN_PROCESO_JUDICIAL.puedePasarA(EstadoJuridico.DECLARADO_ADOPTABLE));
        assertTrue(EstadoJuridico.EN_PROCESO_JUDICIAL.puedePasarA(EstadoJuridico.REINTEGRADO));
        assertTrue(EstadoJuridico.DECLARADO_ADOPTABLE.puedePasarA(EstadoJuridico.ADOPTADO));
    }

    @Test
    @DisplayName("HU-07.2 c2: saltarse etapas o volver atras no esta permitido")
    void saltosYRetrocesosNoPermitidos() {
        assertFalse(EstadoJuridico.EN_PROTECCION.puedePasarA(EstadoJuridico.ADOPTADO));
        assertFalse(EstadoJuridico.EN_PROTECCION.puedePasarA(EstadoJuridico.DECLARADO_ADOPTABLE));
        assertFalse(EstadoJuridico.DECLARADO_ADOPTABLE.puedePasarA(EstadoJuridico.EN_PROCESO_JUDICIAL));
        assertFalse(EstadoJuridico.DECLARADO_ADOPTABLE.puedePasarA(EstadoJuridico.REINTEGRADO));
        assertFalse(EstadoJuridico.EN_PROCESO_JUDICIAL.puedePasarA(EstadoJuridico.EN_PROCESO_JUDICIAL));
    }

    @Test
    @DisplayName("HU-07.2 c3: Adoptado y Reintegrado son finales y no tienen siguientes")
    void etapasFinales() {
        assertEquals(List.of(EstadoJuridico.ADOPTADO, EstadoJuridico.REINTEGRADO), EstadoJuridico.finales());
        assertTrue(EstadoJuridico.ADOPTADO.siguientes().isEmpty());
        assertTrue(EstadoJuridico.REINTEGRADO.siguientes().isEmpty());
        assertFalse(EstadoJuridico.DECLARADO_ADOPTABLE.esFinal());
    }

    @Test
    @DisplayName("Desde En proceso judicial se ofrecen dos salidas, en orden")
    void siguientesEnOrden() {
        assertEquals(List.of(EstadoJuridico.DECLARADO_ADOPTABLE, EstadoJuridico.REINTEGRADO),
                EstadoJuridico.EN_PROCESO_JUDICIAL.siguientes());
    }
}
