package com.rutaexpress.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, UUID> {

    @Query("select t from PersonalAccessToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PersonalAccessToken> findByTokenHash(String hash);

    List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
