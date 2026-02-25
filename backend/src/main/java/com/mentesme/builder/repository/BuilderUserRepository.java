package com.mentesme.builder.repository;

import com.mentesme.builder.entity.BuilderUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BuilderUserRepository extends JpaRepository<BuilderUser, Long> {

    Optional<BuilderUser> findByUsernameAndActiveTrue(String username);

    Optional<BuilderUser> findByUsername(String username);

    List<BuilderUser> findAllByOrderByUsernameAsc();

    long countByActiveTrue();
}
