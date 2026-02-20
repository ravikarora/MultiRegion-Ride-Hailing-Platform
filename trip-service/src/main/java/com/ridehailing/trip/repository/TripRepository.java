package com.ridehailing.trip.repository;

import com.ridehailing.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByDispatchRequestId(UUID dispatchRequestId);

    List<Trip> findByRiderId(String riderId);

    List<Trip> findByDriverId(String driverId);
}
