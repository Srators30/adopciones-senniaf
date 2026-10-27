package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Sprint 6 (HU-06.1). Trae la familia bloqueando su fila hasta que
     * termine la transaccion.
     *
     * Sin el bloqueo, dos tecnicos que dictaminan la misma familia en el
     * mismo segundo pasarian los dos la comprobacion de "no tiene dictamen"
     * y quedarian dos dictamenes vigentes. Con el bloqueo, el segundo
     * espera, ve el dictamen del primero y recibe 409.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FamiliaSolicitante f WHERE f.numeroSolicitud = :numero")
    Optional<FamiliaSolicitante> buscarParaDictamen(@Param("numero") String numero);
}
