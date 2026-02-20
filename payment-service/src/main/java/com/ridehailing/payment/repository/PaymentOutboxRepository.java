package com.ridehailing.payment.repository;

import com.ridehailing.payment.entity.PaymentOutbox;
import com.ridehailing.payment.entity.PaymentOutbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, UUID> {

    /**
     * Fetch up to batchSize PENDING entries ordered by creation time.
     * Used by the OutboxPublisher poll loop.
     */
    @Query("SELECT o FROM PaymentOutbox o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT 50")
    List<PaymentOutbox> findPendingBatch();

    List<PaymentOutbox> findByStatus(OutboxStatus status);
}
