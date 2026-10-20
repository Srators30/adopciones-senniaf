package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FamiliaRepository extends JpaRepository<FamiliaSolicitante, Long> {

    Optional<FamiliaSolicitante> findByNumeroSolicitud(String numeroSolicitud);

    /**
     * Busqueda con filtros opcionales, igual que en el Sprint 3: cada
     * condicion se apaga sola cuando su parametro llega en null, asi una
     * sola consulta sirve para todas las combinaciones y no hay que
     * concatenar SQL a mano.
     */
    @Query("""
           SELECT f FROM FamiliaSolicitante f
           WHERE (:texto IS NULL
                  OR LOWER(f.nombres) LIKE :texto
                  OR LOWER(f.apellidos) LIKE :texto
                  OR LOWER(f.segundoNombres) LIKE :texto
                  OR LOWER(f.segundoApellidos) LIKE :texto
                  OR LOWER(f.numeroSolicitud) LIKE :texto
                  OR LOWER(f.numeroDocumento) LIKE :texto)
             AND (:estado IS NULL OR f.estado = :estado)
             AND (:modalidad IS NULL OR f.modalidadSolicitada = :modalidad)
             AND (:provincia IS NULL OR f.provinciaResidencia = :provincia)
           """)
    Page<FamiliaSolicitante> buscar(@Param("texto") String texto,
                                     @Param("estado") EstadoFamilia estado,
                                     @Param("modalidad") String modalidad,
                                     @Param("provincia") String provincia,
                                     Pageable pageable);

    boolean existsByNumeroDocumento(String numeroDocumento);
}
