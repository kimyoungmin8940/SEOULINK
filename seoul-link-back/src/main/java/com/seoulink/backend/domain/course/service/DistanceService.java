package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteMatrixResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장소 사이의 거리와 이동시간을 계산하는 서비스이다.
 *
 * <p>OpenRouteService 키가 설정되어 있으면 실제 도보 경로의 거리·시간 행렬을 사용한다.
 * 키가 없거나 외부 API 요청이 실패하면 기존 Haversine 직선거리와 평균 도보 속도로
 * 계산한 예상시간을 사용하므로 코스 추천 기능 자체는 중단되지 않는다.</p>
 */
@Service
public class DistanceService {

    // 외부 경로 API를 사용할 수 없을 때 Haversine 거리와 시속 4.5km로 예상시간을 계산한다.
    private static final Logger log = LoggerFactory.getLogger(DistanceService.class);
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final double FALLBACK_WALKING_SPEED_KM_PER_HOUR = 4.5;

    private final OpenRouteServiceClient openRouteServiceClient;

    /** Spring 실행 시 OpenRouteService 클라이언트를 주입한다. */
    @Autowired
    public DistanceService(OpenRouteServiceClient openRouteServiceClient) {
        this.openRouteServiceClient = openRouteServiceClient;
    }

    /** 외부 API 없이 실행하는 순수 단위 테스트에서 사용한다. */
    DistanceService() {
        this.openRouteServiceClient = null;
    }

    /**
     * 하루 장소 전체의 경로 거리·시간 행렬을 계산한다.
     *
     * @param candidates 같은 날짜에 방문할 장소 후보
     * @return 장소 목록 인덱스와 동일한 순서의 거리·시간 행렬
     */
    public RouteMatrix calculateRouteMatrix(List<PlaceCandidateDto> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("장소 후보 목록은 null일 수 없습니다.");
        }

        validateCoordinates(candidates);
        if (candidates.size() < 2) {
            return createFallbackMatrix(candidates);
        }

        // API 키가 있을 때만 실제 도보 경로 행렬을 요청한다.
        if (openRouteServiceClient != null && openRouteServiceClient.isConfigured()) {
            try {
                List<RouteCoordinate> coordinates = candidates.stream()
                        .map(candidate -> new RouteCoordinate(
                                candidate.getLongitude(),
                                candidate.getLatitude()
                        ))
                        .toList();

                RouteMatrixResult apiResult =
                        openRouteServiceClient.calculateMatrix(coordinates);
                return new RouteMatrix(
                        apiResult.distancesKm(),
                        apiResult.travelTimesMinutes()
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "OpenRouteService 호출 실패로 직선거리 계산을 사용합니다: {}",
                        exception.getMessage()
                );
            }
        }

        // 키가 없거나 요청이 실패해도 추천 흐름은 중단하지 않고 직선거리로 대체한다.
        return createFallbackMatrix(candidates);
    }

    /**
     * 두 위도·경도 좌표 사이의 직선거리를 Haversine 공식으로 계산한다.
     *
     * @return 두 좌표 사이의 직선거리(km)
     */
    public double calculateDistanceKm(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        validateLatitude(startLatitude);
        validateLongitude(startLongitude);
        validateLatitude(endLatitude);
        validateLongitude(endLongitude);

        double startLatitudeRadians = Math.toRadians(startLatitude);
        double endLatitudeRadians = Math.toRadians(endLatitude);
        double latitudeDifference = Math.toRadians(endLatitude - startLatitude);
        double longitudeDifference = Math.toRadians(endLongitude - startLongitude);

        double haversine = Math.pow(Math.sin(latitudeDifference / 2), 2)
                + Math.cos(startLatitudeRadians)
                * Math.cos(endLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifference / 2), 2);

        // 부동소수점 오차로 값이 0~1 범위를 벗어나 제곱근이 NaN이 되는 것을 막는다.
        double normalizedHaversine = Math.max(0.0, Math.min(1.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(normalizedHaversine),
                Math.sqrt(1 - normalizedHaversine)
        );

        return EARTH_RADIUS_KM * centralAngle;
    }

    /** 모든 장소 쌍의 직선거리와 평균 도보 속도 기반 이동시간 행렬을 만든다. */
    private RouteMatrix createFallbackMatrix(List<PlaceCandidateDto> candidates) {
        int size = candidates.size();
        double[][] distancesKm = new double[size][size];
        double[][] travelTimesMinutes = new double[size][size];

        for (int fromIndex = 0; fromIndex < size; fromIndex++) {
            PlaceCandidateDto from = candidates.get(fromIndex);

            for (int toIndex = 0; toIndex < size; toIndex++) {
                PlaceCandidateDto to = candidates.get(toIndex);
                double distanceKm = calculateDistanceKm(
                        from.getLatitude(),
                        from.getLongitude(),
                        to.getLatitude(),
                        to.getLongitude()
                );

                distancesKm[fromIndex][toIndex] = distanceKm;
                travelTimesMinutes[fromIndex][toIndex] =
                        distanceKm / FALLBACK_WALKING_SPEED_KM_PER_HOUR * 60.0;
            }
        }

        return new RouteMatrix(distancesKm, travelTimesMinutes);
    }

    /** 행렬 계산 전에 모든 장소에 유효 범위의 위도·경도가 있는지 확인한다. */
    private void validateCoordinates(List<PlaceCandidateDto> candidates) {
        for (PlaceCandidateDto candidate : candidates) {
            if (candidate == null
                    || candidate.getLatitude() == null
                    || candidate.getLongitude() == null) {
                throw new IllegalArgumentException("장소의 위도와 경도는 필수입니다.");
            }
            validateLatitude(candidate.getLatitude());
            validateLongitude(candidate.getLongitude());
        }
    }

    /** 위도 범위(-90~90)와 유한값 여부를 검증한다. */
    private void validateLatitude(double latitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("위도는 -90 이상 90 이하의 유한한 숫자여야 합니다.");
        }
    }

    /** 경도 범위(-180~180)와 유한값 여부를 검증한다. */
    private void validateLongitude(double longitude) {
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("경도는 -180 이상 180 이하의 유한한 숫자여야 합니다.");
        }
    }

    /** 장소 인덱스를 기준으로 거리(km)와 이동시간(분)을 조회하는 행렬이다. */
    public record RouteMatrix(
            double[][] distancesKm,
            double[][] travelTimesMinutes
    ) {
        public RouteMatrix {
            validateMatrix(distancesKm, travelTimesMinutes);
        }

        public int size() {
            return distancesKm.length;
        }

        public double getDistanceKm(int fromIndex, int toIndex) {
            return distancesKm[fromIndex][toIndex];
        }

        public double getTravelTimeMinutes(int fromIndex, int toIndex) {
            return travelTimesMinutes[fromIndex][toIndex];
        }

        /** 거리와 시간 배열이 같은 크기의 정사각 행렬인지 확인한다. */
        private static void validateMatrix(
                double[][] distancesKm,
                double[][] travelTimesMinutes
        ) {
            if (distancesKm == null
                    || travelTimesMinutes == null
                    || distancesKm.length != travelTimesMinutes.length) {
                throw new IllegalArgumentException("거리·시간 행렬 크기가 올바르지 않습니다.");
            }

            int size = distancesKm.length;
            for (int row = 0; row < size; row++) {
                if (distancesKm[row] == null
                        || travelTimesMinutes[row] == null
                        || distancesKm[row].length != size
                        || travelTimesMinutes[row].length != size) {
                    throw new IllegalArgumentException("거리·시간 행렬은 정사각형이어야 합니다.");
                }
            }
        }
    }
}
