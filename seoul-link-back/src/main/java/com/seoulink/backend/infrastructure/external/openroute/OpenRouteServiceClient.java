package com.seoulink.backend.infrastructure.external.openroute;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * OpenRouteService Matrix API를 호출해 여러 장소 사이의 이동거리와 이동시간을 조회한다.
 *
 * <p>Matrix API를 사용하면 장소 두 개마다 Directions API를 반복 호출하지 않고,
 * 하루에 포함된 모든 장소의 거리·시간 조합을 한 번의 요청으로 받을 수 있다.</p>
 */
@Component
public class OpenRouteServiceClient {

    private static final Logger log = LoggerFactory.getLogger(
            OpenRouteServiceClient.class
    );

    // Matrix API에 거리와 시간을 함께 요청하고 거리 응답 단위는 km로 고정한다.
    private static final List<String> MATRIX_METRICS = List.of("distance", "duration");
    private static final String DISTANCE_UNIT = "km";
    private static final ZoneId DAILY_RESET_ZONE = ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final String apiKey;
    private final int dailyCallBudget;
    private final long minimumRequestIntervalMillis;
    private final long rateLimitCooldownMillis;

    private LocalDate usageDate;
    private int reservedCalls;
    private LocalDate authenticationBlockedDate;
    private long rateLimitedUntilMillis;
    private long lastRequestStartedAtMillis;

    @Autowired
    public OpenRouteServiceClient(
            @Qualifier("openRouteServiceRestClient") RestClient restClient,
            @Value("${external.openroute.api-key:}") String apiKey,
            @Value("${external.openroute.daily-call-budget:450}")
            int dailyCallBudget,
            @Value("${external.openroute.minimum-request-interval-millis:1100}")
            long minimumRequestIntervalMillis,
            @Value("${external.openroute.rate-limit-cooldown-millis:3000}")
            long rateLimitCooldownMillis
    ) {
        if (dailyCallBudget < 0) {
            throw new IllegalArgumentException(
                    "OpenRouteService 일일 호출 예산은 0 이상이어야 합니다."
            );
        }
        if (minimumRequestIntervalMillis < 0L) {
            throw new IllegalArgumentException(
                    "OpenRouteService 최소 요청 간격은 0 이상이어야 합니다."
            );
        }
        if (rateLimitCooldownMillis < 0L) {
            throw new IllegalArgumentException(
                    "OpenRouteService 호출 제한 대기시간은 0 이상이어야 합니다."
            );
        }
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.dailyCallBudget = dailyCallBudget;
        this.minimumRequestIntervalMillis = minimumRequestIntervalMillis;
        this.rateLimitCooldownMillis = rateLimitCooldownMillis;
        this.usageDate = currentUsageDate();
    }

    /** 기존 단위 테스트와 수동 생성 코드에서 사용하는 호환 생성자이다. */
    public OpenRouteServiceClient(
            RestClient restClient,
            String apiKey
    ) {
        this(restClient, apiKey, 450);
    }

    /** 호출 예산을 직접 지정하는 기존 테스트용 호환 생성자이다. */
    public OpenRouteServiceClient(
            RestClient restClient,
            String apiKey,
            int dailyCallBudget
    ) {
        this(restClient, apiKey, dailyCallBudget, 0L, 0L);
    }

    /**
     * API 키가 설정되어 실제 외부 요청을 보낼 수 있는지 확인한다.
     */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 요청한 이동수단 프로필로 모든 좌표 사이의 경로 거리와 이동시간 행렬을 조회한다.
     *
     * @param requestedProfile OpenRouteService 이동 프로필
     * @param coordinates 경도·위도 순서로 구성된 좌표 목록
     * @return 거리(km)와 이동시간(분) 행렬
     */
    public RouteMatrixResult calculateMatrix(
            String requestedProfile,
            List<RouteCoordinate> coordinates
    ) {
        validateRequest(requestedProfile, coordinates);
        reserveDailyCallAndAwaitSlot();

        // OpenRouteService 규격은 일반적인 위도·경도 표기와 반대로 [경도, 위도] 순서이다.
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
            // 선택한 이동 프로필에 대해 하루 좌표 전체를 한 번에 요청한다.
            MatrixResponse response = restClient.post()
                    .uri("/v2/matrix/{profile}", requestedProfile)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MatrixResponse.class);

            return convertResponse(response, coordinates.size());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 401 || status == 403) {
                blockAuthenticationForToday(status);
            } else if (status == 429) {
                activateRateLimitCooldown(exception);
            }
            log.warn(
                    "OpenRouteService 응답 오류: status={}, body={}",
                    status,
                    abbreviate(exception.getResponseBodyAsString())
            );
            throw new IllegalStateException(
                    "OpenRouteService 경로 정보 요청에 실패했습니다. HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "OpenRouteService 경로 정보 요청에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * 무료 Matrix 한도를 모두 쓰기 전에 서버 자체 예산에서 선차단한다.
     * 도보·자동차 요청이 동시에 몰려 429가 발생하지 않도록 호출 시작 시각도 직렬로 조정한다.
     */
    private synchronized void reserveDailyCallAndAwaitSlot() {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);

        if (today.equals(authenticationBlockedDate)) {
            throw new IllegalStateException(
                    "OpenRouteService 인증 오류로 오늘 실제 경로 호출을 중단했습니다. "
                            + "OPENROUTESERVICE_API_KEY를 확인해주세요."
            );
        }
        if (reservedCalls >= dailyCallBudget) {
            throw new IllegalStateException(
                    "서버에서 정한 OpenRouteService 일일 호출 예산에 도달했습니다. "
                            + "dailyCalls=" + reservedCalls + "/" + dailyCallBudget
            );
        }

        while (true) {
            long now = System.currentTimeMillis();
            long earliestByInterval = lastRequestStartedAtMillis
                    + minimumRequestIntervalMillis;
            long earliestStart = Math.max(
                    earliestByInterval,
                    rateLimitedUntilMillis
            );
            long waitMillis = earliestStart - now;
            if (waitMillis <= 0L) {
                break;
            }
            try {
                wait(waitMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "OpenRouteService 호출 대기 중 요청이 중단되었습니다.",
                        exception
                );
            }
        }

        reservedCalls++;
        lastRequestStartedAtMillis = System.currentTimeMillis();
        log.info(
                "외부 경로 API 호출: provider=OpenRouteService, dailyCalls={}/{}, date={}",
                reservedCalls,
                dailyCallBudget,
                today
        );
    }

    /** 401·403은 키 자체의 문제이므로 같은 날 반복 요청하지 않는다. */
    private synchronized void blockAuthenticationForToday(int status) {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);
        authenticationBlockedDate = today;
        log.error(
                "OpenRouteService 인증 오류로 오늘 실제 경로 호출을 중단합니다: status={}, date={}",
                status,
                today
        );
    }

    /**
     * 429는 일일 예산 소진으로 간주하지 않는다. Retry-After 또는 기본 대기시간 뒤
     * 다음 요청부터 다시 시도해 순간 호출 제한이 도보·자동차 전체를 하루 동안 막지 않게 한다.
     */
    private synchronized void activateRateLimitCooldown(
            RestClientResponseException exception
    ) {
        long cooldownMillis = resolveRateLimitCooldownMillis(exception);
        long nextAllowedAt = System.currentTimeMillis() + cooldownMillis;
        rateLimitedUntilMillis = Math.max(rateLimitedUntilMillis, nextAllowedAt);
        log.warn(
                "OpenRouteService 순간 호출 제한으로 잠시 대기합니다: cooldownMillis={}, dailyCalls={}/{}",
                cooldownMillis,
                reservedCalls,
                dailyCallBudget
        );
    }

    private long resolveRateLimitCooldownMillis(
            RestClientResponseException exception
    ) {
        if (exception.getResponseHeaders() != null) {
            String retryAfter = exception.getResponseHeaders().getFirst(
                    HttpHeaders.RETRY_AFTER
            );
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds >= 0L) {
                        return Math.max(rateLimitCooldownMillis, seconds * 1000L);
                    }
                } catch (NumberFormatException ignored) {
                    // HTTP 날짜 형식은 기본 대기시간을 사용한다.
                }
            }
        }
        return rateLimitCooldownMillis;
    }

    private synchronized void resetDailyStateIfNeeded(LocalDate today) {
        if (!today.equals(usageDate)) {
            usageDate = today;
            reservedCalls = 0;
            authenticationBlockedDate = null;
            rateLimitedUntilMillis = 0L;
            lastRequestStartedAtMillis = 0L;
        }
    }

    private LocalDate currentUsageDate() {
        return LocalDate.now(DAILY_RESET_ZONE);
    }

    /** API 키·이동 프로필·최소 좌표 개수를 외부 요청 전에 검증한다. */
    private void validateRequest(
            String requestedProfile,
            List<RouteCoordinate> coordinates
    ) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "OPENROUTESERVICE_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
        if (requestedProfile == null || !requestedProfile.matches("[a-z-]+")) {
            throw new IllegalStateException("OpenRouteService 이동 프로필 설정이 올바르지 않습니다.");
        }
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("경로 행렬 계산에는 좌표가 두 개 이상 필요합니다.");
        }
    }

    /** API 응답 행렬을 서비스가 사용하는 km·분 단위 결과로 변환한다. */
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
        // durations는 초 단위이므로 60으로 나누어 분 단위로 변환한다.
        double[][] travelTimesMinutes = copyMatrix(
                response.durations(),
                expectedSize,
                "durations",
                60.0
        );

        return new RouteMatrixResult(distancesKm, travelTimesMinutes);
    }

    /** 응답 행렬의 크기와 값을 검증하면서 primitive 배열로 복사하고 단위를 변환한다. */
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
                // 경로를 만들 수 없는 일부 셀은 null로 올 수 있다. 전체 행렬을
                // 버리지 않고 NaN으로 보존해 상위 계산기가 그 구간만 추정값으로 대체한다.
                if (value == null) {
                    result[row][column] = Double.NaN;
                    continue;
                }
                if (!Double.isFinite(value) || value < 0.0) {
                    throw invalidResponse(fieldName);
                }
                result[row][column] = value / divisor;
            }
        }

        return result;
    }

    /** 잘못된 외부 응답을 동일한 예외 형식으로 변환한다. */
    private IllegalStateException invalidResponse(String fieldName) {
        return new IllegalStateException(
                "OpenRouteService 응답의 " + fieldName + " 행렬이 올바르지 않습니다."
        );
    }

    /** 외부 오류 본문 전체가 로그를 과도하게 채우지 않도록 길이만 제한한다. */
    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String singleLine = value.replaceAll("[\\r\\n]+", " ");
        return singleLine.length() <= 500
                ? singleLine
                : singleLine.substring(0, 500) + "...";
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
