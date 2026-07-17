package com.seoulink.backend.domain.survey.repository;

import com.seoulink.backend.domain.survey.entity.TravelTypeMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelTypeMasterRepository
        extends JpaRepository<TravelTypeMaster, String> {

    /**
     * 여행 유형 코드로 유형 정보를 조회한다.
     */
    Optional<TravelTypeMaster> findByTravelCode(
            String travelCode
    );

    /**
     * 유형 제목에 검색어가 포함된 여행 유형을 조회한다.
     */
    List<TravelTypeMaster>
    findByTypeTitleContainingIgnoreCase(
            String keyword
    );
}
