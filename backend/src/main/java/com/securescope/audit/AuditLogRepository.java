package com.securescope.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLog> findLatest();

    @Query("SELECT a FROM AuditLog a ORDER BY a.id ASC")
    List<AuditLog> findAllOrdered();
}
