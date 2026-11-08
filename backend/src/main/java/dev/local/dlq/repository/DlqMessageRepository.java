package dev.local.dlq.repository;

import dev.local.dlq.model.DlqMessage;
import dev.local.dlq.model.DlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {
    List<DlqMessage> findAllByOrderByCreatedAtDesc();
    List<DlqMessage> findAllByEventIdAndStatus(String eventId, DlqStatus status);
}
