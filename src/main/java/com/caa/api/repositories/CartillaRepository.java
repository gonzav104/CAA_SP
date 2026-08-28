package com.caa.api.repositories;

import com.caa.api.models.Cartilla;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartillaRepository extends JpaRepository<Cartilla, UUID> {
}
