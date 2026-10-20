package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.DocumentoExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoExpedienteRepository extends JpaRepository<DocumentoExpediente, Long> {

    List<DocumentoExpediente> findByExpedienteIdOrderByFechaSubidaDesc(Long expedienteId);

    /**
     * Busca el documento comprobando ADEMAS que pertenezca al expediente
     * indicado. Sin esa comprobacion, cambiar el numero en la URL dejaria
     * descargar el documento de otro menor.
     */
    Optional<DocumentoExpediente> findByIdAndExpedienteId(Long id, Long expedienteId);
}
