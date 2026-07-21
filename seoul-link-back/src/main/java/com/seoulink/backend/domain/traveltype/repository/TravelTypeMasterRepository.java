package com.seoulink.backend.domain.traveltype.repository;

import com.seoulink.backend.domain.traveltype.entity.TravelTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelTypeMasterRepository extends JpaRepository<TravelTypeMaster, String> {
}