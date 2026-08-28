package com.caa.api.repositories;

import com.caa.api.models.Categoria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {
}
