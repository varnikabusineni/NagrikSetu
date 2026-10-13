package com.nagriksetu.reference;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WardRepository extends JpaRepository<Ward, Long> {

    Optional<Ward> findByCode(String code);

    List<Ward> findAllByOrderByNameAsc();

    List<Ward> findByBoundaryJsonIsNotNull();
}
