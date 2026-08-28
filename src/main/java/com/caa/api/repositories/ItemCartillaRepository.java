package com.caa.api.repositories;

import com.caa.api.models.ItemCartilla;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemCartillaRepository extends JpaRepository<ItemCartilla, UUID> {
}
