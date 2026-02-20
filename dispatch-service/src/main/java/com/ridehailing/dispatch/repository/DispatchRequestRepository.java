package com.ridehailing.dispatch.repository;

import com.ridehailing.dispatch.entity.DispatchRequest;
import com.ridehailing.shared.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchRequestRepository extends JpaRepository<DispatchRequest, UUID> {

    Optional<DispatchRequest> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE DispatchRequest d SET d.status = :status, d.assignedDriverId = :driverId WHERE d.id = :id")
    int updateStatusAndDriver(UUID id, RideStatus status, String driverId);

    @Modifying
    @Query("UPDATE DispatchRequest d SET d.status = :status WHERE d.id = :id")
    int updateStatus(UUID id, RideStatus status);
}
