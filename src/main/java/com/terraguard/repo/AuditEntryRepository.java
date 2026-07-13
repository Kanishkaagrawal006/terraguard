package com.terraguard.repo;

import com.terraguard.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
}
