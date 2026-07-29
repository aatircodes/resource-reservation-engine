package com.project.resource_reservation_engine.repository;

import com.project.resource_reservation_engine.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
}