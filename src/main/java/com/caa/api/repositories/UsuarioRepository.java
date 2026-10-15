package com.caa.api.repositories;

import com.caa.api.models.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    // Lock as the FIRST read of every credential-mutating transaction; never load then lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.email = :email")
    Optional<Usuario> findLockedByEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.resetToken = :hash")
    Optional<Usuario> findLockedByResetToken(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.googleRecoveryTokenHash = :hash")
    Optional<Usuario> findLockedByGoogleRecoveryTokenHash(@Param("hash") String hash);

    Optional<Usuario> findByGoogleSub(String googleSub);
    Optional<Usuario> findByEmail(String email);
}
