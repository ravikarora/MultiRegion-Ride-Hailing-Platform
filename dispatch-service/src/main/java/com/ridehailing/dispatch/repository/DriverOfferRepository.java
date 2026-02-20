package com.ridehailing.dispatch.repository;

import com.ridehailing.dispatch.entity.DriverOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {

    List<DriverOffer> findByDispatchRequestIdOrderByAttemptNumberDesc(UUID dispatchRequestId);

    Optional<DriverOffer> findByDispatchRequestIdAndDriverIdAndResponseIsNull(
            UUID dispatchRequestId, String driverId);
}
