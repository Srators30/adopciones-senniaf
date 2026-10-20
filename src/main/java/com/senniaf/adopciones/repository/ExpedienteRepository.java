package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.Expediente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    Optional<Expediente> findByNumeroExpediente(String numeroExpediente);

    /**
     * Busqueda del listado (HU-03). Los tres filtros son opcionales: si
     * llegan nulos, esa condicion no filtra nada. Se compara en
     * minusculas para que buscar "maria" encuentre "María".
     */
    @Query("""
           SELECT e FROM Expediente e
           WHERE (:texto IS NULL
                  OR LOWER(e.nombres) LIKE :texto
                  OR LOWER(e.apellidos) LIKE :texto
                  OR LOWER(e.numeroExpediente) LIKE :texto
                  OR LOWER(e.numeroDocumento) LIKE :texto)
             AND (:estado IS NULL OR e.estado = :estado)
             AND (:modalidad IS NULL OR e.modalidadAcogida = :modalidad)
           """)
    Page<Expediente> buscar(@Param("texto") String texto,
                            @Param("estado") EstadoExpediente estado,
                            @Param("modalidad") String modalidad,
                            Pageable pageable);
}
