package com.seoulink.backend.infrastructure.external.openroute;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * OpenRouteService Matrix API를 호출해 여러 장소 사이의 이동거리와 이동시간을 조회한다.
 *
 * <p>Matrix API를 사용하면 장소 두 개마다 Directions API를 반복 호출하지 않고,
 * 하루에 포함된 모든 장소의 거리·시간 조합을 한 번의 요청으로 받을 수 있다.</p>
 */
@Component
public class OpenRouteServiceClient {

    private static final List<String> MATRIX_METRICS = List.of("distance", "duration");
    private static final String DISTANCE_UNIT = "km";

    private final RestClient restClient;
    private final String apiKey;
    private final String profile;

    public OpenRouteServiceClient(
            @Qualifier("openRouteServiceRestClient") RestClient restClient,
            @Value("${external.openroute.api-key:}") String apiKey,
            @Value("${external.openroute.profile:foot-walking}") String profile
    ) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.profile = profile;
    }

    /**
     * API 키가 설정되어 실제 외부 요청을 보낼 수 있는지 확인한다.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 전달받은 모든 좌표 사이의 경로 거리와 이동시간 행렬을 조회한다.
     *
     * @param coordinates 경도·위도 순서로 구성된 좌표 목록
     * @return 거리(km)와 이동시간(분) 행렬
     */
    public RouteMatrixResult calculateMatrix(List<RouteCoordinate> coordinates) {
        validateRequest(coordinates);

        List<List<Double>> locations = coordinates.stream()
                .map(coordinate -> List.of(
                        coordinate.longitude(),
                        coordinate.latitude()
                ))
                .toList();

        MatrixRequest request = new MatrixRequest(
                locations,
                MATRIX_METRICS,
                DISTANCE_UNIT
        );

        try {
            MatrixResponse response = restClient.post()
                    .uri("/v2/matrix/{profile}", profile)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MatrixResponse.class);

            return convertResponse(response, coordinates.size());
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "OpenRouteService 경로 정보 요청에 실패했습니다.",
                    exception
            );
        }
    }

    private void validateRequest(List<RouteCoordinate> coordinates) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "OPENROUTESERVICE_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
        if (profile == null || !profile.matches("[a-z-]+")) {
            throw new IllegalStateException("OpenRouteService 이동 프로필 설정이 올바르지 않습니다.");
        }
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("경로 행렬 계산에는 좌표가 두 개 이상 필요합니다.");
        }
    }

    private RouteMatrixResult convertResponse(MatrixResponse response, int expectedSize) {
        if (response == null) {
            throw new IllegalStateException("OpenRouteService 응답이 비어 있습니다.");
        }

        double[][] distancesKm = copyMatrix(
                response.distances(),
                expectedSize,
                "distances",
                1.0
        );
        double[][] travelTimesMinutes = copyMatrix(
                response.durations(),
                expectedSize,
                "durations",
                60.0
        );

        return new RouteMatrixResult(distancesKm, travelTimesMinutes);
    }

    private double[][] copyMatrix(
            List<List<Double>> source,
            int expectedSize,
            String fieldName,
            double divisor
    ) {
        if (source == null || source.size() != expectedSize) {
            throw invalidResponse(fieldName);
        }

        double[][] result = new double[expectedSize][expectedSize];
        for (int row = 0; row < expectedSize; row++) {
            List<Double> sourceRow = source.get(row);
            if (sourceRow == null || sourceRow.size() != expectedSize) {
                throw invalidResponse(fieldName);
            }

            for (int column = 0; column < expectedSize; column++) {
                Double value = sourceRow.get(column);
                if (value == null || !Double.isFinite(value) || value < 0.0) {
                    throw invalidResponse(fieldName);
                }
                result[row][column] = value / divisor;
            }
        }

        return result;
    }

    private IllegalStateException invalidResponse(String fieldName) {
        return new IllegalStateException(
                "OpenRouteService 응답의 " + fieldName + " 행렬이 올바르지 않습니다."
        );
    }

    /** OpenRouteService 요청에 사용하는 좌표이다. API 규격에 맞게 경도를 먼저 둔다. */
    public record RouteCoordinate(double longitude, double latitude) {
    }

    /** OpenRouteService가 계산한 거리(km)·시간(분) 행렬이다. */
    public record RouteMatrixResult(
            double[][] distancesKm,
            double[][] travelTimesMinutes
    ) {
        public double getDistanceKm(int fromIndex, int toIndex) {
            return distancesKm[fromIndex][toIndex];
        }

        public double getTravelTimeMinutes(int fromIndex, int toIndex) {
            return travelTimesMinutes[fromIndex][toIndex];
        }
    }

    /** Matrix API 요청 본문이다. */
    public record MatrixRequest(
            List<List<Double>> locations,
            List<String> metrics,
            String units
    ) {
    }

    /** Matrix API 응답에서 최적화에 필요한 필드만 받는다. */
    public record MatrixResponse(
            List<List<Double>> distances,
            List<List<Double>> durations
    ) {
    }
}
