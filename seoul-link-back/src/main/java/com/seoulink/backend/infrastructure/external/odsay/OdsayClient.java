package com.seoulink.backend.infrastructure.external.odsay;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.seoulink.backend.domain.course.model.TransitPathType;
import com.seoulink.backend.infrastructure.external.openroute.OpenRouteServiceClient.RouteCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ODsay 대중교통 길찾기 API를 호출해 두 장소 사이의 거리와 이동시간을 조회한다.
 *
 * <p>Server API Key는 코드에 저장하지 않고 {@code ODSAY_API_KEY} 환경변수에서
 * 읽는다. 키는 URI 변수로 전달해 {@code +}, {@code /} 같은 특수문자가 요청 시
 * 안전하게 URL 인코딩되도록 한다.</p>
 */
@Component
public class OdsayClient {

    private static final Logger log = LoggerFactory.getLogger(
            OdsayClient.class
    );
    public static final String WITHIN_WALKING_DISTANCE_CODE = "-98";

    private static final String ROUTE_SEARCH_PATH = "/searchPubTransPathT";
    private static final int RECOMMENDED_ROUTE_ORDER = 0;
    private static final int INTRA_CITY_SEARCH = 0;
    private static final int ALL_TRANSIT_PATHS = 0;
    private static final ZoneId ODSAY_RESET_ZONE =
            ZoneId.of("Asia/Seoul");

    private final RestClient restClient;
    private final String apiKey;
    private final int dailyCallBudget;

    private LocalDate usageDate;
    private int reservedCalls;
    private LocalDate blockedDate;

    @Autowired
    public OdsayClient(
            @Qualifier("odsayRestClient") RestClient restClient,
            @Value("${external.odsay.api-key:}") String apiKey,
            @Value("${external.odsay.daily-call-budget:900}")
            int dailyCallBudget
    ) {
        if (dailyCallBudget < 0) {
            throw new IllegalArgumentException(
                    "ODsay 일일 호출 예산은 0 이상이어야 합니다."
            );
        }
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.dailyCallBudget = dailyCallBudget;
        this.usageDate = currentUsageDate();
    }

    /** 기존 단위 테스트와 수동 생성 코드에서 사용하는 호환 생성자이다. */
    public OdsayClient(RestClient restClient, String apiKey) {
        this(restClient, apiKey, 900);
    }

    /** 실제 ODsay 요청을 보낼 수 있도록 API 키가 설정되었는지 확인한다. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 출발지에서 도착지까지의 도시 내 대중교통 경로 중 가장 빠른 결과를 반환한다.
     *
     * @param from 출발지 경도·위도
     * @param to 도착지 경도·위도
     * @return 총 이동거리(km), 총 소요시간(분), 지하철·버스·혼합 경로 종류
     */
    public TransitRouteResult calculateRoute(
            RouteCoordinate from,
            RouteCoordinate to
    ) {
        validateRequest(from, to);
        if (sameCoordinate(from, to)) {
            return new TransitRouteResult(0.0, 0.0, null);
        }
        reserveDailyCall();

        try {
            OdsayResponse response = restClient.get()
                    .uri(uriBuilder -> createRequestUri(uriBuilder, from, to))
                    .retrieve()
                    .body(OdsayResponse.class);
            return convertResponse(response);
        } catch (OdsayApiException exception) {
            blockForTodayIfQuotaOrAuthenticationFailure(exception);
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429
                    || exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 403) {
                OdsayApiException apiException = new OdsayApiException(
                        "HTTP_" + exception.getStatusCode().value(),
                        "ODsay 요청 한도 또는 인증 상태를 확인해주세요."
                );
                blockForToday();
                throw apiException;
            }
            throw new IllegalStateException(
                    "ODsay 대중교통 경로 요청에 실패했습니다. HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "ODsay 대중교통 경로 요청에 실패했습니다.",
                    exception
            );
        }
    }

    /**
     * ODsay Basic 1,000건을 전부 쓰기 전에 서버 자체 예산에서 호출을 차단한다.
     * 날짜가 바뀌면 카운터와 외부 오류 차단 상태를 함께 초기화한다.
     */
    private synchronized void reserveDailyCall() {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);

        if (today.equals(blockedDate)
                || reservedCalls >= dailyCallBudget) {
            blockedDate = today;
            throw new OdsayApiException(
                    "LOCAL_DAILY_LIMIT",
                    "서버에서 정한 ODsay 일일 호출 예산에 도달했습니다."
            );
        }
        reservedCalls++;
        log.info(
                "외부 경로 API 호출: provider=ODsay, dailyCalls={}/{}, date={}",
                reservedCalls,
                dailyCallBudget,
                today
        );
    }

    private synchronized void blockForToday() {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);
        blockedDate = today;
    }

    private synchronized void resetDailyStateIfNeeded(LocalDate today) {
        if (!today.equals(usageDate)) {
            usageDate = today;
            reservedCalls = 0;
            blockedDate = null;
        }
    }

    private LocalDate currentUsageDate() {
        return LocalDate.now(ODSAY_RESET_ZONE);
    }

    /**
     * 쿼터 초과·앱 정지·키 인증 오류는 다른 장소 쌍에서도 반복되므로 당일 재호출하지 않는다.
     * 단순 ODsay 서버 내부 오류는 일시적일 수 있어 현재 요청에서만 중단한다.
     */
    private void blockForTodayIfQuotaOrAuthenticationFailure(
            OdsayApiException exception
    ) {
        if (exception.isPairSpecific()) {
            return;
        }

        String message = exception.getApiMessage()
                .toLowerCase(Locale.ROOT);
        if ("LOCAL_DAILY_LIMIT".equals(exception.getErrorCode())
                || message.contains("quota")
                || message.contains("limit")
                || message.contains("exceed")
                || message.contains("suspend")
                || message.contains("blocked")
                || message.contains("authentication")
                || message.contains("api key")
                || message.contains("apikey")
                || message.contains("호출 한도")
                || message.contains("사용량")
                || message.contains("초과")
                || message.contains("제한")
                || message.contains("정지")) {
            blockForToday();
        }
    }

    /** API 키만 엄격하게 인코딩되도록 URI 변수 확장 방식으로 요청 주소를 만든다. */
    private URI createRequestUri(
            UriBuilder uriBuilder,
            RouteCoordinate from,
            RouteCoordinate to
    ) {
        return uriBuilder.path(ROUTE_SEARCH_PATH)
                .queryParam("SX", "{startLongitude}")
                .queryParam("SY", "{startLatitude}")
                .queryParam("EX", "{endLongitude}")
                .queryParam("EY", "{endLatitude}")
                .queryParam("OPT", "{routeOrder}")
                .queryParam("SearchType", "{searchType}")
                .queryParam("SearchPathType", "{searchPathType}")
                .queryParam("apiKey", "{apiKey}")
                .build(Map.of(
                        "startLongitude", from.longitude(),
                        "startLatitude", from.latitude(),
                        "endLongitude", to.longitude(),
                        "endLatitude", to.latitude(),
                        "routeOrder", RECOMMENDED_ROUTE_ORDER,
                        "searchType", INTRA_CITY_SEARCH,
                        "searchPathType", ALL_TRANSIT_PATHS,
                        "apiKey", apiKey
                ));
    }

    /** ODsay 오류 또는 경로 목록을 검증하고 최단시간 결과의 단위를 변환한다. */
    private TransitRouteResult convertResponse(OdsayResponse response) {
        if (response == null) {
            throw new IllegalStateException("ODsay 응답이 비어 있습니다.");
        }
        if (response.error() != null && !response.error().isEmpty()) {
            OdsayError error = response.error().get(0);
            throw new OdsayApiException(
                    error == null ? null : error.code(),
                    error == null ? null : error.message()
            );
        }
        if (response.result() == null
                || response.result().path() == null
                || response.result().path().isEmpty()) {
            throw new IllegalStateException("ODsay 응답에 대중교통 경로가 없습니다.");
        }

        // 응답 배열 순서에 의존하지 않고 최단시간을 우선하며, 동률이면 더 짧은 경로를 고른다.
        OdsayPath selectedPath = response.result().path().stream()
                .filter(this::hasValidRouteInfo)
                .min(Comparator
                        .comparingDouble((OdsayPath path) -> path.info().totalTime())
                        .thenComparingDouble(path -> path.info().totalDistance()))
                .orElseThrow(() -> new IllegalStateException(
                        "ODsay 경로의 거리·이동시간 값이 올바르지 않습니다."
                ));

        // totalDistance는 미터, totalTime은 분 단위로 반환된다.
        return new TransitRouteResult(
                selectedPath.info().totalDistance() / 1_000.0,
                selectedPath.info().totalTime(),
                TransitPathType.fromOdsayPathType(selectedPath.pathType())
        );
    }

    private boolean hasValidRouteInfo(OdsayPath path) {
        if (path == null
                || path.pathType() == null
                || path.pathType() < 1
                || path.pathType() > 3
                || path.info() == null
                || path.info().totalDistance() == null
                || path.info().totalTime() == null) {
            return false;
        }
        double distanceMeters = path.info().totalDistance();
        double travelMinutes = path.info().totalTime();
        return Double.isFinite(distanceMeters)
                && Double.isFinite(travelMinutes)
                && distanceMeters >= 0.0
                && travelMinutes >= 0.0;
    }

    private void validateRequest(RouteCoordinate from, RouteCoordinate to) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "ODSAY_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
        validateCoordinate(from, "출발지");
        validateCoordinate(to, "도착지");
    }

    private void validateCoordinate(RouteCoordinate coordinate, String name) {
        if (coordinate == null
                || !Double.isFinite(coordinate.longitude())
                || !Double.isFinite(coordinate.latitude())
                || coordinate.longitude() < -180.0
                || coordinate.longitude() > 180.0
                || coordinate.latitude() < -90.0
                || coordinate.latitude() > 90.0) {
            throw new IllegalArgumentException(
                    "ODsay " + name + " 좌표가 올바르지 않습니다."
            );
        }
    }

    private boolean sameCoordinate(RouteCoordinate from, RouteCoordinate to) {
        return Double.compare(from.longitude(), to.longitude()) == 0
                && Double.compare(from.latitude(), to.latitude()) == 0;
    }

    /** ODsay가 계산한 한 방향의 대중교통 경로 값이다. */
    public record TransitRouteResult(
            double distanceKm,
            double travelTimeMinutes,
            TransitPathType transitPathType
    ) {
        /** 경로 종류를 사용하지 않던 기존 단위 테스트·호출부용 호환 생성자이다. */
        public TransitRouteResult(double distanceKm, double travelTimeMinutes) {
            this(distanceKm, travelTimeMinutes, null);
        }
    }

    /** ODsay 성공·오류 응답에서 필요한 최상위 필드만 매핑한다. */
    public record OdsayResponse(
            OdsayResult result,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<OdsayError> error
    ) {
    }

    public record OdsayResult(List<OdsayPath> path) {
    }

    public record OdsayPath(
            Integer pathType,
            OdsayRouteInfo info
    ) {
    }

    public record OdsayRouteInfo(
            Double totalTime,
            Double totalDistance
    ) {
    }

    public record OdsayError(
            String code,
            @JsonAlias("msg") String message
    ) {
    }

    /** HTTP 성공 응답 본문에 포함된 ODsay 논리 오류를 코드와 함께 전달한다. */
    public static class OdsayApiException extends IllegalStateException {

        private final String errorCode;
        private final String apiMessage;

        public OdsayApiException(String errorCode, String apiMessage) {
            super("ODsay 오류(code=" + safeValue(errorCode)
                    + "): " + safeValue(apiMessage));
            this.errorCode = errorCode;
            this.apiMessage = safeValue(apiMessage);
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getApiMessage() {
            return apiMessage;
        }

        /** 정류장·서비스지역·검색결과 문제는 다른 장소 쌍에서 재시도할 수 있다. */
        public boolean isPairSpecific() {
            return errorCode != null
                    && List.of("3", "4", "5", "6", "-98", "-99")
                    .contains(errorCode);
        }

        private static String safeValue(String value) {
            return value == null || value.isBlank() ? "UNKNOWN" : value;
        }
    }
}
