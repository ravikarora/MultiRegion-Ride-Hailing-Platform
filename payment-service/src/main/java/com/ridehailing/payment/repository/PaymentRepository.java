package com.ridehailing.payment.repository;

import com.ridehailing.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByTripId(UUID tripId);

    List<Payment> findByRiderId(String riderId);

    List<Payment> findByStatus(String status);
}
