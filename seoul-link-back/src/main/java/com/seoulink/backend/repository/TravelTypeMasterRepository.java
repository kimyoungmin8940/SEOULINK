package com.seoulink.backend.repository;

import com.seoulink.backend.entity.TravelTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelTypeMasterRepository extends JpaRepository<TravelTypeMaster, String> {
}