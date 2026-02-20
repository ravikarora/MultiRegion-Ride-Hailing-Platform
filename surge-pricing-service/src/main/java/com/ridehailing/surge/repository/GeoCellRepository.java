package com.ridehailing.surge.repository;

import com.ridehailing.surge.entity.GeoCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeoCellRepository extends JpaRepository<GeoCell, String> {
}
