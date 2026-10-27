package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DictamenIdoneidadRepository extends JpaRepository<DictamenIdoneidad, Long> {

    Optional<DictamenIdoneidad> findFirstByFamiliaIdAndVigenteTrue(Long familiaId);

    boolean existsByFamiliaIdAndVigenteTrue(Long familiaId);
}
