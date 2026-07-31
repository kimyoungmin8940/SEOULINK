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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Locale;

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

    private static final String DEFAULT_BASE_URL = "https://api.odsay.com/v1/api";
    private static final String ROUTE_SEARCH_PATH = "/searchPubTransPathT";
    private static final int RECOMMENDED_ROUTE_ORDER = 0;
    private static final int INTRA_CITY_SEARCH = 0;
    private static final int ALL_TRANSIT_PATHS = 0;
    private static final ZoneId ODSAY_RESET_ZONE =
            ZoneId.of("Asia/Seoul");
    // ODsay는 초당 호출 제한이 있으므로 요청 시작 간격을 직렬로 보장한다.
    private static final long MINIMUM_REQUEST_INTERVAL_MILLIS = 1_100L;
    // 최초 추천 직후 여러 DAY를 자동 선조회해도 짧은 시간에 호출이 몰리지 않도록
    // 60초 구간에서 최대 50건만 시작한다. 창이 가득 차면 예상값으로 포기하지 않고
    // 가장 오래된 호출이 만료될 때까지만 기다렸다가 실제 경로 조회를 이어간다.
    private static final int REQUEST_WINDOW_MAX_CALLS = 50;
    private static final long REQUEST_WINDOW_MILLIS = 60_000L;
    private static final long REQUEST_WINDOW_WAKEUP_MARGIN_MILLIS = 25L;

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final int dailyCallBudget;

    private LocalDate usageDate;
    private int reservedCalls;
    private LocalDate blockedDate;
    private String blockedErrorCode;
    private String blockedErrorMessage;
    private long lastRequestStartedAtMillis;
    private final Deque<Long> requestStartedAtMillis = new ArrayDeque<>();

    @Autowired
    public OdsayClient(
            @Qualifier("odsayRestClient") RestClient restClient,
            @Value("${external.odsay.api-key:}") String apiKey,
            @Value("${external.odsay.daily-call-budget:900}")
            int dailyCallBudget,
            @Value("${external.odsay.base-url:https://api.odsay.com/v1/api}")
            String baseUrl
    ) {
        if (dailyCallBudget < 0) {
            throw new IllegalArgumentException(
                    "ODsay 일일 호출 예산은 0 이상이어야 합니다."
            );
        }
        this.restClient = restClient;
        this.apiKey = normalizeApiKey(apiKey);
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.dailyCallBudget = dailyCallBudget;
        this.usageDate = currentUsageDate();
        log.info(
                "ODsay 설정 로드: configured={}, keyLength={}, keyFingerprint={}, uriTemplateEncoding=true, dailyCallBudget={}, minimumRequestIntervalMillis={}, requestWindow={}/{}ms",
                !this.apiKey.isBlank(),
                this.apiKey.length(),
                apiKeyFingerprint(this.apiKey),
                this.dailyCallBudget,
                MINIMUM_REQUEST_INTERVAL_MILLIS,
                REQUEST_WINDOW_MAX_CALLS,
                REQUEST_WINDOW_MILLIS
        );
    }

    /** 기존 단위 테스트와 수동 생성 코드에서 사용하는 호환 생성자이다. */
    public OdsayClient(RestClient restClient, String apiKey) {
        this(restClient, apiKey, 900, DEFAULT_BASE_URL);
    }

    /** 일일 예산을 직접 지정하던 기존 테스트용 호환 생성자이다. */
    public OdsayClient(RestClient restClient, String apiKey, int dailyCallBudget) {
        this(restClient, apiKey, dailyCallBudget, DEFAULT_BASE_URL);
    }

    /** 실제 ODsay 요청을 보낼 수 있도록 API 키가 설정되었는지 확인한다. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * 현재 날짜의 로컬 호출 예산과 차단 상태를 확인한다.
     * PublicTransitRouteCalculator의 기존 사전 검사와 호환하기 위한 메서드다.
     */
    public synchronized boolean canAttemptRequest() {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);
        long now = System.currentTimeMillis();
        removeExpiredRequestStarts(now);
        return isConfigured()
                && !today.equals(blockedDate)
                && reservedCalls < dailyCallBudget;
    }

    /**
     * 출발지에서 도착지까지의 도시 내 대중교통 경로 중 가장 빠른 결과를 반환한다.
     *
     * @param from 출발지 경도·위도
     * @param to 도착지 경도·위도
     * @return 총 이동거리(km), 총 소요시간(분), 지하철·버스·혼합 경로 종류
     */
    public synchronized TransitRouteResult calculateRoute(
            RouteCoordinate from,
            RouteCoordinate to
    ) {
        validateRequest(from, to);
        if (sameCoordinate(from, to)) {
            return new TransitRouteResult(0.0, 0.0, null);
        }

        ensureRequestCanStart();
        waitForRequestWindowCapacity();
        waitForMinimumRequestInterval();
        reserveDailyCall();

        try {
            OdsayResponse response = restClient.get()
                    .uri(uriBuilder -> createRequestUri(uriBuilder, from, to))
                    .retrieve()
                    .body(OdsayResponse.class);
            TransitRouteResult result = convertResponse(response);
            log.info(
                    "ODsay 경로 조회 성공: distanceKm={}, travelMinutes={}, pathType={}",
                    result.distanceKm(),
                    result.travelTimeMinutes(),
                    result.transitPathType()
            );
            return result;
        } catch (OdsayApiException exception) {
            // 인증 오류 한 번으로 뒤의 모든 구간을 로컬에서 차단하지 않는다.
            // ODsay 쪽의 순간적인 인증/호출창 오류일 수 있으므로 다음 구간은
            // 기존 호출 간격과 60초 창 제한을 지키며 다시 실제 요청한다.
            blockForTodayIfQuotaFailure(exception);
            throw exception;
        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();
            if (statusCode == 429) {
                OdsayApiException apiException = new OdsayApiException(
                        "HTTP_429",
                        "ODsay 요청 한도에 도달했습니다."
                );
                blockForToday(apiException);
                throw apiException;
            }
            if (statusCode == 401 || statusCode == 403) {
                throw new OdsayApiException(
                        "HTTP_" + statusCode,
                        "ODsay API 키 또는 등록 플랫폼·IP를 확인해주세요."
                );
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

    /** 인증 회로·60초 호출 창·일일 예산을 실제 외부 요청 전에 다시 확인한다. */
    private void ensureRequestCanStart() {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);
        long now = System.currentTimeMillis();
        removeExpiredRequestStarts(now);

        if (today.equals(blockedDate)) {
            throw new OdsayApiException(
                    blockedErrorCode,
                    blockedErrorMessage
            );
        }
        if (reservedCalls >= dailyCallBudget) {
            OdsayApiException exception = new OdsayApiException(
                    "LOCAL_DAILY_LIMIT",
                    "서버에서 정한 ODsay 일일 호출 예산에 도달했습니다."
            );
            blockForToday(exception);
            throw exception;
        }
    }

    /**
     * 60초 호출 창이 가득 찼으면 가장 오래된 요청이 만료될 때까지만 기다린다.
     * 이전 구현은 canAttemptRequest()에서 false를 반환해 이후 구간을 전부 예상값으로
     * 처리했기 때문에, 5일 일정 자동 선조회에서 실제 ODsay 경로가 대량 누락될 수 있었다.
     */
    private void waitForRequestWindowCapacity() {
        while (true) {
            long now = System.currentTimeMillis();
            removeExpiredRequestStarts(now);
            if (requestStartedAtMillis.size() < REQUEST_WINDOW_MAX_CALLS) {
                return;
            }

            Long oldestStartedAt = requestStartedAtMillis.peekFirst();
            if (oldestStartedAt == null) {
                return;
            }
            long waitMillis = Math.max(
                    1L,
                    oldestStartedAt + REQUEST_WINDOW_MILLIS
                            - now + REQUEST_WINDOW_WAKEUP_MARGIN_MILLIS
            );
            log.info(
                    "ODsay 60초 호출 창이 가득 차 {}ms 대기 후 실제 경로 조회를 계속합니다. currentCalls={}/{}",
                    waitMillis,
                    requestStartedAtMillis.size(),
                    REQUEST_WINDOW_MAX_CALLS
            );
            sleep(waitMillis);
        }
    }

    /** 초당 제한을 피하기 위해 요청 시작 간격을 직렬로 보장한다. */
    private void waitForMinimumRequestInterval() {
        long now = System.currentTimeMillis();
        long waitMillis = Math.max(
                0L,
                MINIMUM_REQUEST_INTERVAL_MILLIS
                        - (now - lastRequestStartedAtMillis)
        );
        if (waitMillis > 0L) {
            sleep(waitMillis);
        }
        long startedAt = System.currentTimeMillis();
        requestStartedAtMillis.addLast(startedAt);
        lastRequestStartedAtMillis = startedAt;
    }

    private void removeExpiredRequestStarts(long now) {
        long threshold = now - REQUEST_WINDOW_MILLIS;
        while (!requestStartedAtMillis.isEmpty()
                && requestStartedAtMillis.peekFirst() <= threshold) {
            requestStartedAtMillis.removeFirst();
        }
    }

    private void sleep(long waitMillis) {
        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "ODsay 호출 간격 대기 중 스레드가 중단되었습니다.",
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

        if (today.equals(blockedDate)) {
            throw new OdsayApiException(
                    blockedErrorCode,
                    blockedErrorMessage
            );
        }
        if (reservedCalls >= dailyCallBudget) {
            OdsayApiException exception = new OdsayApiException(
                    "LOCAL_DAILY_LIMIT",
                    "서버에서 정한 ODsay 일일 호출 예산에 도달했습니다."
            );
            blockForToday(exception);
            throw exception;
        }
        reservedCalls++;
        log.info(
                "외부 경로 API 호출: provider=ODsay, dailyCalls={}/{}, date={}",
                reservedCalls,
                dailyCallBudget,
                today
        );
    }

    private synchronized void blockForToday(OdsayApiException exception) {
        LocalDate today = currentUsageDate();
        resetDailyStateIfNeeded(today);
        blockedDate = today;
        blockedErrorCode = exception.getErrorCode();
        blockedErrorMessage = exception.getApiMessage();
    }

    private synchronized void resetDailyStateIfNeeded(LocalDate today) {
        if (!today.equals(usageDate)) {
            usageDate = today;
            reservedCalls = 0;
            blockedDate = null;
            blockedErrorCode = null;
            blockedErrorMessage = null;
            requestStartedAtMillis.clear();
            lastRequestStartedAtMillis = 0L;
        }
    }

    private LocalDate currentUsageDate() {
        return LocalDate.now(ODSAY_RESET_ZONE);
    }

    /**
     * 실제 쿼터 초과·앱 정지 오류만 당일 차단한다.
     * 인증 오류는 일시적인 플랫폼 전파·복수 출구 IP 문제일 수도 있으므로
     * 한 번의 실패로 남은 모든 구간을 당일 차단하지 않는다.
     */
    private void blockForTodayIfQuotaFailure(
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
                || message.contains("호출 한도")
                || message.contains("사용량")
                || message.contains("초과")
                || message.contains("제한")
                || message.contains("정지")) {
            blockForToday(exception);
        }
    }


    /**
     * ODsay 콘솔에서 복사한 키가 이미 URL 인코딩된 형태여도 내부에서는 원문 키로 통일한다.
     * 이후 URI 템플릿이 정확히 한 번만 인코딩하므로 {@code %2B -> %252B} 같은
     * 이중 인코딩으로 인한 {@code ApiKeyAuthFailed}를 방지한다.
     */
    private static String normalizeApiKey(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.length() >= 2
                && ((normalized.startsWith("\"")
                && normalized.endsWith("\""))
                || (normalized.startsWith("'")
                && normalized.endsWith("'")))) {
            normalized = normalized.substring(1, normalized.length() - 1)
                    .trim();
        }
        if (!normalized.contains("%")) {
            return normalized;
        }

        try {
            // URLDecoder가 원래 키의 '+'를 공백으로 바꾸지 않도록 먼저 보호한다.
            return URLDecoder.decode(
                    normalized.replace("+", "%2B"),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            // 잘못된 퍼센트 표기가 섞였으면 원문을 유지해 설정 오류를 숨기지 않는다.
            return normalized;
        }
    }


    /** 실제 키를 노출하지 않고 실행 설정이 바뀌었는지 확인할 수 있는 짧은 지문을 만든다. */
    private static String apiKeyFingerprint(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    /**
     * 정상 동작하던 버전과 동일하게 Spring URI 변수 확장에 원문 키를 전달한다.
     * 키를 직접 URLEncoder로 가공한 뒤 URI.create()에 붙이면 RestClient의 기존
     * 인코딩 경로와 달라져 같은 키도 ODsay에서 인증 실패로 처리될 수 있다.
     */
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

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? DEFAULT_BASE_URL
                : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
            // 한 장소쌍에서 경로가 없다는 응답은 API 전체 장애가 아니다.
            // 호출부가 이 구간만 도보/추정값으로 보완하고 다음 장소쌍은 계속 조회한다.
            throw new OdsayApiException(
                    "NO_ROUTE",
                    "ODsay 응답에 대중교통 경로가 없습니다."
            );
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
                    && List.of(
                            "3", "4", "5", "6",
                            "-98", "-99", "NO_ROUTE"
                    ).contains(errorCode);
        }

        private static String safeValue(String value) {
            return value == null || value.isBlank() ? "UNKNOWN" : value;
        }
    }
}
