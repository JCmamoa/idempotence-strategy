package dev.local.dlq.repository;

import dev.local.dlq.model.ReplayAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReplayAuditRepository extends JpaRepository<ReplayAudit, UUID> {
}
