package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.response.CourseBuilderPlaceResponse;
import com.seoulink.backend.domain.course.dto.request.CourseRouteRequest;
import com.seoulink.backend.domain.course.dto.response.CourseRouteResponse;
import com.seoulink.backend.domain.course.dto.request.CourseBuilderSaveRequest;
import com.seoulink.backend.domain.course.dto.response.CourseSaveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CourseBuilderService {

    private static final String ORS_WALKING_DIRECTIONS_URL =
            "https://api.openrouteservice.org/v2/directions/foot-walking";

    private static final String ORS_DRIVING_DIRECTIONS_URL =
            "https://api.openrouteservice.org/v2/directions/driving-car";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    @Value("${openrouteservice.api-key:}")
    private String openRouteServiceApiKey;

    public CourseBuilderService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
    }

    public List<CourseBuilderPlaceResponse> findPlaces(String theme, String region, Integer limit) {
        String normalizedTheme = defaultValue(theme, "ALL").toUpperCase(Locale.ROOT);
        String normalizedRegion = defaultValue(region, "서울");
        int normalizedLimit = limit == null ? 120 : Math.max(1, Math.min(limit, 700));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", normalizedLimit);

        List<String> whereConditions = new ArrayList<>();
        whereConditions.add("NVL(IS_ACTIVE, 'Y') = 'Y'");

        if (!"서울".equals(normalizedRegion)) {
            whereConditions.add("(REGION = :region OR ADDRESS LIKE :regionLike OR ROAD_ADDRESS LIKE :regionLike)");
            params.addValue("region", normalizedRegion);
            params.addValue("regionLike", "%" + normalizedRegion + "%");
        }

        String themeCondition = buildThemeCondition(normalizedTheme, params);

        if (!themeCondition.isBlank()) {
            whereConditions.add(themeCondition);
        }

        String sql = """
                SELECT *
                FROM (
                    SELECT
                        PLACE_ID,
                        API_PROVIDER,
                        API_PLACE_ID,
                        CONTENT_ID,
                        NAME,
                        CATEGORY,
                        API_CATEGORY,
                        REGION,
                        ADDRESS,
                        ROAD_ADDRESS,
                        LATITUDE,
                        LONGITUDE,
                        PHONE,
                        PLACE_URL,
                        RATING,
                        REVIEW_COUNT,
                        DESCRIPTION,
                        IMAGE_URL,
                        INDOOR_YN,
                        SOURCE_TYPE,
                        RECOMMEND_YN,
                        APPROVAL_STATUS
                    FROM PLACES
                    WHERE %s
                    ORDER BY
                        CASE WHEN RECOMMEND_YN = 'Y' THEN 0 ELSE 1 END,
                        NVL(RATING, 0) DESC,
                        NVL(REVIEW_COUNT, 0) DESC,
                        PLACE_ID DESC
                )
                WHERE ROWNUM <= :limit
                """.formatted(String.join(" AND ", whereConditions));

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> mapPlaceResponse(rs, normalizedTheme)
        );
    }

    private String buildThemeCondition(String theme, MapSqlParameterSource params) {
        return switch (theme) {
            case "PALACE_CULTURE" -> keywordCondition(
                    params,
                    "palaceCultureKeywords",
                    List.of("궁", "궁궐", "문화재", "박물관", "미술관", "전시", "공연", "종묘", "역사")
            );
            case "NATURE_HANGANG" -> keywordCondition(
                    params,
                    "natureHangangKeywords",
                    List.of("한강", "한강공원", "공원", "숲", "산책", "수목원", "남산", "서울숲", "월드컵공원")
            );
            case "DATE" -> "(" + categoryInCondition(params, "dateCategories", List.of("TOUR", "RESTAURANT", "CAFE"))
                    + " OR "
                    + keywordCondition(params, "dateKeywords", List.of("데이트", "전시", "미술관", "카페", "공원", "전망", "루프탑", "와인"))
                    + ")";
            case "FOOD_TOUR" -> categoryInCondition(params, "foodTourCategories", List.of("RESTAURANT"));
            case "CAFE_TOUR" -> categoryInCondition(params, "cafeTourCategories", List.of("CAFE"));
            case "SHOPPING_HOTPLACE" -> keywordCondition(
                    params,
                    "shoppingHotplaceKeywords",
                    List.of("쇼핑", "백화점", "몰", "시장", "편집샵", "핫플", "성수", "홍대", "익선", "연남")
            );
            case "NIGHT_VIEW" -> keywordCondition(
                    params,
                    "nightViewKeywords",
                    List.of("야경", "전망", "타워", "루프탑", "한강", "남산", "낙산", "응봉산")
            );
            case "HOTEL_STAY" -> categoryInCondition(params, "hotelStayCategories", List.of("HOTEL"));
            case "ALL" -> "";
            default -> "";
        };
    }

    private String categoryInCondition(
            MapSqlParameterSource params,
            String parameterName,
            List<String> categories
    ) {
        params.addValue(parameterName, categories);
        return "CATEGORY IN (:" + parameterName + ")";
    }

    private String keywordCondition(
            MapSqlParameterSource params,
            String parameterName,
            List<String> keywords
    ) {
        List<String> conditionParts = new ArrayList<>();

        for (int i = 0; i < keywords.size(); i++) {
            String key = parameterName + i;
            params.addValue(key, "%" + keywords.get(i) + "%");

            conditionParts.add("(" +
                    "NAME LIKE :" + key +
                    " OR API_CATEGORY LIKE :" + key +
                    " OR DESCRIPTION LIKE :" + key +
                    " OR ADDRESS LIKE :" + key +
                    " OR ROAD_ADDRESS LIKE :" + key +
                    ")");
        }

        return "(" + String.join(" OR ", conditionParts) + ")";
    }

    private CourseBuilderPlaceResponse mapPlaceResponse(ResultSet rs, String theme) throws SQLException {
        return new CourseBuilderPlaceResponse(
                rs.getLong("PLACE_ID"),
                rs.getString("API_PROVIDER"),
                rs.getString("API_PLACE_ID"),
                getNullableLong(rs, "CONTENT_ID"),
                rs.getString("NAME"),
                rs.getString("CATEGORY"),
                rs.getString("API_CATEGORY"),
                "ALL".equals(theme) ? null : theme,
                rs.getString("REGION"),
                rs.getString("ADDRESS"),
                rs.getString("ROAD_ADDRESS"),
                getNullableDouble(rs, "LATITUDE"),
                getNullableDouble(rs, "LONGITUDE"),
                rs.getString("PHONE"),
                rs.getString("PLACE_URL"),
                getNullableDouble(rs, "RATING"),
                getNullableInteger(rs, "REVIEW_COUNT"),
                rs.getString("DESCRIPTION"),
                rs.getString("IMAGE_URL"),
                rs.getString("INDOOR_YN"),
                rs.getString("SOURCE_TYPE"),
                rs.getString("RECOMMEND_YN"),
                rs.getString("APPROVAL_STATUS"),
                "DB"
        );
    }

    public CourseRouteResponse calculateRoutes(CourseRouteRequest request) {
        if (request == null || request.places() == null || request.places().size() < 2) {
            return new CourseRouteResponse(List.of());
        }

        List<CourseRouteRequest.RoutePlaceRequest> routePlaces = request.places().stream()
                .filter(this::hasValidCoordinate)
                .toList();

        if (routePlaces.size() < 2) {
            return new CourseRouteResponse(List.of());
        }

        List<CourseRouteResponse.RouteSegmentResponse> segments = new ArrayList<>();
        boolean includeWalkingRoute = !"DRIVING".equalsIgnoreCase(defaultValue(request.mode(), "BOTH"));

        for (int i = 0; i < routePlaces.size() - 1; i++) {
            CourseRouteRequest.RoutePlaceRequest fromPlace = routePlaces.get(i);
            CourseRouteRequest.RoutePlaceRequest toPlace = routePlaces.get(i + 1);

            int fromDayNo = fromPlace.dayNo() == null ? 1 : fromPlace.dayNo();
            int toDayNo = toPlace.dayNo() == null ? 1 : toPlace.dayNo();

            if (fromDayNo != toDayNo) {
                continue;
            }

            segments.add(calculateRouteSegment(
                    fromPlace,
                    toPlace,
                    i,
                    i + 1,
                    fromDayNo,
                    includeWalkingRoute
            ));
        }

        return new CourseRouteResponse(segments);
    }

    private boolean hasValidCoordinate(CourseRouteRequest.RoutePlaceRequest place) {
        return place != null
                && place.latitude() != null
                && place.longitude() != null;
    }

    private CourseRouteResponse.RouteSegmentResponse calculateRouteSegment(
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace,
            int fromIndex,
            int toIndex,
            int dayNo,
            boolean includeWalkingRoute
    ) {
        RouteSummary walkingRoute = null;
        RouteSummary drivingRoute = null;
        String walkingError = null;
        String drivingError = null;
        boolean drivingRouteEstimated = false;

        if (includeWalkingRoute) {
            try {
                walkingRoute = requestOpenRouteServiceRoute(
                        ORS_WALKING_DIRECTIONS_URL,
                        "도보",
                        fromPlace,
                        toPlace
                );
            } catch (Exception e) {
                walkingError = e.getMessage();
            }
        }

        try {
            drivingRoute = requestOpenRouteServiceRoute(
                    ORS_DRIVING_DIRECTIONS_URL,
                    "차량",
                    fromPlace,
                    toPlace
            );
        } catch (Exception e) {
            drivingError = e.getMessage();
        }

        if (drivingRoute == null && isUnroutablePointError(drivingError)) {
            drivingRoute = estimateDrivingRoute(fromPlace, toPlace);
            drivingError = null;
            drivingRouteEstimated = true;
        }

        List<CourseRouteResponse.RoutePointResponse> drivingRoutePoints =
                drivingRoute == null || drivingRoute.routePoints().isEmpty()
                        ? buildStraightLineRoutePoints(fromPlace, toPlace)
                        : drivingRoute.routePoints();

        String statusMessage = buildRouteStatusMessage(walkingError, drivingError);
        if (drivingRouteEstimated) {
            statusMessage = appendRouteNotice(
                    statusMessage,
                    "차량 도로와 연결되지 않은 좌표라 직선 거리 기준 예상 시간으로 표시합니다."
            );
        }

        return new CourseRouteResponse.RouteSegmentResponse(
                fromPlace.clientPlaceId(),
                toPlace.clientPlaceId(),
                fromPlace.name(),
                toPlace.name(),
                fromIndex,
                toIndex,
                dayNo,
                drivingRoute == null ? null : drivingRoute.distanceMeter(),
                drivingRoute == null ? null : drivingRoute.durationSecond(),
                drivingRoute == null ? null : secondsToMinutes(drivingRoute.durationSecond()),
                walkingRoute == null ? null : walkingRoute.distanceMeter(),
                walkingRoute == null ? null : walkingRoute.durationSecond(),
                walkingRoute == null ? null : secondsToMinutes(walkingRoute.durationSecond()),
                drivingRoutePoints,
                statusMessage
        );
    }

    private String buildRouteStatusMessage(String walkingError, String drivingError) {
        if (walkingError == null && drivingError == null) {
            return null;
        }

        if (walkingError != null && drivingError != null) {
            return "도보 계산 실패: " + walkingError + " / 차량 계산 실패: " + drivingError;
        }

        if (walkingError != null) {
            return "도보 계산 실패: " + walkingError;
        }

        return "차량 계산 실패: " + drivingError;
    }

    private boolean isUnroutablePointError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }

        return errorMessage.toLowerCase(Locale.ROOT)
                .contains("could not find routable point");
    }

    private RouteSummary estimateDrivingRoute(
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace
    ) {
        double latitudeDistance = Math.toRadians(toPlace.latitude() - fromPlace.latitude());
        double longitudeDistance = Math.toRadians(toPlace.longitude() - fromPlace.longitude());
        double fromLatitude = Math.toRadians(fromPlace.latitude());
        double toLatitude = Math.toRadians(toPlace.latitude());
        double haversine = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        double straightDistanceMeter = 6_371_008.8 * 2 * Math.atan2(
                Math.sqrt(haversine),
                Math.sqrt(1 - haversine)
        );
        int estimatedDistanceMeter = (int) Math.round(straightDistanceMeter * 1.25);
        int estimatedDurationSecond = Math.max(
                60,
                (int) Math.ceil((estimatedDistanceMeter / 1_000.0) / 25.0 * 3_600 + 180)
        );

        return new RouteSummary(
                estimatedDistanceMeter,
                estimatedDurationSecond,
                buildStraightLineRoutePoints(fromPlace, toPlace)
        );
    }

    private String appendRouteNotice(String currentMessage, String notice) {
        return currentMessage == null || currentMessage.isBlank()
                ? notice
                : currentMessage + " / " + notice;
    }

    private RouteSummary requestOpenRouteServiceRoute(
            String directionsUrl,
            String routeLabel,
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace
    ) {
        if (openRouteServiceApiKey == null || openRouteServiceApiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                    "OpenRouteService API 키가 없습니다. application.properties에 openrouteservice.api-key를 설정해주세요."
            );
        }

        Map<String, Object> requestBody = Map.of(
                "coordinates",
                List.of(
                        List.of(fromPlace.longitude(), fromPlace.latitude()),
                        List.of(toPlace.longitude(), toPlace.latitude())
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", openRouteServiceApiKey.trim());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    directionsUrl,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            return parseOpenRouteServiceRouteSummary(response.getBody());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    routeLabel + " OpenRouteService 길찾기 API 오류: " + e.getResponseBodyAsString()
            );
        }
    }

    private RouteSummary parseOpenRouteServiceRouteSummary(Map responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("OpenRouteService 응답이 비어 있습니다.");
        }

        Object routesObject = responseBody.get("routes");

        if (!(routesObject instanceof List<?> routes) || routes.isEmpty()) {
            Object errorObject = responseBody.get("error");

            if (errorObject != null) {
                throw new IllegalStateException("OpenRouteService 오류: " + errorObject);
            }

            throw new IllegalStateException("OpenRouteService 응답에서 경로 정보를 찾지 못했습니다.");
        }

        Object firstRouteObject = routes.get(0);

        if (!(firstRouteObject instanceof Map<?, ?> firstRoute)) {
            throw new IllegalStateException("OpenRouteService 경로 응답 형식이 올바르지 않습니다.");
        }

        Object summaryObject = firstRoute.get("summary");

        if (!(summaryObject instanceof Map<?, ?> summary)) {
            throw new IllegalStateException("OpenRouteService 응답에서 거리/시간 정보를 찾지 못했습니다.");
        }

        Integer distanceMeter = toRoundedInteger(summary.get("distance"));
        Integer durationSecond = toRoundedInteger(summary.get("duration"));

        if (distanceMeter == null || durationSecond == null) {
            throw new IllegalStateException("OpenRouteService 거리/시간 값이 올바르지 않습니다.");
        }

        return new RouteSummary(
                distanceMeter,
                durationSecond,
                parseOpenRouteServiceJsonRoutePoints(firstRoute.get("geometry"))
        );
    }

    private List<CourseRouteResponse.RoutePointResponse> parseOpenRouteServiceJsonRoutePoints(
            Object geometryObject
    ) {
        if (geometryObject instanceof String encodedGeometry && !encodedGeometry.isBlank()) {
            return decodeOpenRouteServicePolyline(encodedGeometry);
        }

        // 응답 설정에 따라 좌표 배열로 전달되는 경우도 처리합니다.
        if (geometryObject instanceof List<?> coordinates) {
            return parseCoordinateListRoutePoints(coordinates);
        }

        return List.of();
    }

    private List<CourseRouteResponse.RoutePointResponse> parseCoordinateListRoutePoints(
            List<?> coordinates
    ) {
        List<CourseRouteResponse.RoutePointResponse> routePoints = new ArrayList<>();

        for (Object coordinateObject : coordinates) {
            if (!(coordinateObject instanceof List<?> coordinate) || coordinate.size() < 2) {
                continue;
            }

            Double longitude = toDouble(coordinate.get(0));
            Double latitude = toDouble(coordinate.get(1));

            if (latitude == null || longitude == null) {
                continue;
            }

            routePoints.add(new CourseRouteResponse.RoutePointResponse(latitude, longitude));
        }

        return routePoints;
    }

    /**
     * ORS JSON 응답의 geometry는 Google encoded polyline 형식이며
     * 기본 좌표 정밀도는 1e-5입니다.
     */
    private List<CourseRouteResponse.RoutePointResponse> decodeOpenRouteServicePolyline(
            String encodedGeometry
    ) {
        List<CourseRouteResponse.RoutePointResponse> routePoints = new ArrayList<>();
        int index = 0;
        int latitudeValue = 0;
        int longitudeValue = 0;

        while (index < encodedGeometry.length()) {
            DecodeResult latitudeResult = decodePolylineValue(encodedGeometry, index);

            if (latitudeResult == null) {
                return List.of();
            }

            index = latitudeResult.nextIndex();
            latitudeValue += latitudeResult.delta();

            DecodeResult longitudeResult = decodePolylineValue(encodedGeometry, index);

            if (longitudeResult == null) {
                return List.of();
            }

            index = longitudeResult.nextIndex();
            longitudeValue += longitudeResult.delta();

            routePoints.add(new CourseRouteResponse.RoutePointResponse(
                    latitudeValue / 100000.0,
                    longitudeValue / 100000.0
            ));
        }

        return routePoints;
    }

    private DecodeResult decodePolylineValue(String encodedGeometry, int startIndex) {
        int result = 0;
        int shift = 0;
        int index = startIndex;
        int currentByte;

        do {
            if (index >= encodedGeometry.length() || shift > 30) {
                return null;
            }

            currentByte = encodedGeometry.charAt(index++) - 63;
            result |= (currentByte & 0x1F) << shift;
            shift += 5;
        } while (currentByte >= 0x20);

        int delta = (result & 1) != 0
                ? ~(result >> 1)
                : result >> 1;

        return new DecodeResult(delta, index);
    }

    private List<CourseRouteResponse.RoutePointResponse> buildStraightLineRoutePoints(
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace
    ) {
        return List.of(
                new CourseRouteResponse.RoutePointResponse(
                        fromPlace.latitude(),
                        fromPlace.longitude()
                ),
                new CourseRouteResponse.RoutePointResponse(
                        toPlace.latitude(),
                        toPlace.longitude()
                )
        );
    }

    private Integer toRoundedInteger(Object value) {
        Double doubleValue = toDouble(value);

        if (doubleValue == null) {
            return null;
        }

        return (int) Math.round(doubleValue);
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer secondsToMinutes(Integer seconds) {
        if (seconds == null) {
            return null;
        }

        return (int) Math.ceil(seconds / 60.0);
    }

    private record DecodeResult(
            int delta,
            int nextIndex
    ) {
    }

    private record RouteSummary(
            Integer distanceMeter,
            Integer durationSecond,
            List<CourseRouteResponse.RoutePointResponse> routePoints
    ) {
    }

    @Transactional
    public CourseSaveResponse saveCourse(CourseBuilderSaveRequest request) {
        validateRequest(request);

        Long courseId = insertTravelCourse(request);

        insertCourseDetails(courseId, request);
        updateCourseTotals(courseId);
        return getSavedCourseResponse(courseId);
    }

    /** 직접 만든 코스만 소유자가 기존 장소 목록까지 교체해 수정할 수 있다. */
    @Transactional
    public CourseSaveResponse updateCourse(Long courseId, CourseBuilderSaveRequest request) {
        if (courseId == null || courseId <= 0) {
            throw new IllegalArgumentException("코스 ID가 올바르지 않습니다.");
        }

        validateRequest(request);
        validateEditableCourse(courseId, request.memberId());
        updateTravelCourse(courseId, request);
        jdbcTemplate.update(
                "DELETE FROM COURSE_DETAILS WHERE COURSE_ID = :courseId",
                new MapSqlParameterSource().addValue("courseId", courseId)
        );

        insertCourseDetails(courseId, request);
        updateCourseTotals(courseId);
        return getSavedCourseResponse(courseId);
    }

    private void insertCourseDetails(Long courseId, CourseBuilderSaveRequest request) {
        for (int i = 0; i < request.places().size(); i++) {
            CourseBuilderSaveRequest.PlaceRequest place = request.places().get(i);
            CourseBuilderSaveRequest.PlaceRequest previousPlace = i == 0 ? null : request.places().get(i - 1);

            Long placeId = findOrInsertPlace(request.memberId(), place);

            int currentDayNo = place.dayNo() == null ? 1 : place.dayNo();
            int previousDayNo = previousPlace == null || previousPlace.dayNo() == null
                    ? 1
                    : previousPlace.dayNo();
            boolean sameDayAsPrevious = previousPlace != null && previousDayNo == currentDayNo;

            Integer incomingDistanceM = sameDayAsPrevious ? previousPlace.moveDistanceM() : null;
            Integer incomingDurationMin = sameDayAsPrevious ? previousPlace.moveDurationMin() : null;

            insertCourseDetail(
                    courseId,
                    placeId,
                    place,
                    i + 1,
                    incomingDistanceM,
                    incomingDurationMin
            );
        }

    }

    private void validateRequest(CourseBuilderSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("코스 요청 정보가 없습니다.");
        }
        if (request.memberId() == null) {
            throw new IllegalArgumentException("회원 ID가 없습니다.");
        }

        MapSqlParameterSource memberParams = new MapSqlParameterSource()
                .addValue("memberId", request.memberId());
        Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MEMBER WHERE MEMBER_ID = :memberId",
                memberParams,
                Integer.class
        );

        if (memberCount == null || memberCount == 0) {
            throw new IllegalArgumentException("로그인한 회원 정보를 DB에서 찾을 수 없습니다. 다시 로그인해주세요.");
        }

        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new IllegalArgumentException("코스 제목은 필수입니다.");
        }

        if (request.description() == null || request.description().trim().isEmpty()) {
            throw new IllegalArgumentException("코스 설명은 필수입니다.");
        }

        if (request.places() == null || request.places().isEmpty()) {
            throw new IllegalArgumentException("코스에는 장소가 1개 이상 필요합니다.");
        }

        for (CourseBuilderSaveRequest.PlaceRequest place : request.places()) {
            int dayNo = place.dayNo() == null ? 1 : place.dayNo();
            if (dayNo < 1 || dayNo > 7) {
                throw new IllegalArgumentException("여행 일차는 1일부터 7일까지만 저장할 수 있습니다.");
            }
            if (place.placeOrder() != null && place.placeOrder() < 1) {
                throw new IllegalArgumentException("장소 순서는 1 이상이어야 합니다.");
            }
        }
    }

    private void validateEditableCourse(Long courseId, Long memberId) {
        List<Map<String, Object>> courses = jdbcTemplate.queryForList(
                "SELECT MEMBER_ID, COURSE_TYPE FROM TRAVEL_COURSES WHERE COURSE_ID = :courseId",
                new MapSqlParameterSource().addValue("courseId", courseId)
        );
        if (courses.isEmpty()) {
            throw new IllegalArgumentException("수정할 코스를 찾을 수 없습니다.");
        }

        Map<String, Object> course = courses.get(0);
        Object ownerValue = course.get("MEMBER_ID");
        Long ownerId = ownerValue instanceof Number number ? number.longValue() : null;
        if (!memberId.equals(ownerId)) {
            throw new IllegalArgumentException("본인이 만든 코스만 수정할 수 있습니다.");
        }
        if (!"CUSTOM".equalsIgnoreCase(String.valueOf(course.get("COURSE_TYPE")))) {
            throw new IllegalArgumentException("직접 만든 코스만 수정할 수 있습니다.");
        }
    }

    private Long insertTravelCourse(CourseBuilderSaveRequest request) {
        String sql = """
                INSERT INTO TRAVEL_COURSES (
                    MEMBER_ID,
                    RESULT_ID,
                    PAYMENT_ID,
                    TITLE,
                    DESCRIPTION,
                    TRAVEL_CODE,
                    COURSE_TYPE,
                    REGION,
                    IS_PUBLIC
                )
                VALUES (
                    :memberId,
                    :resultId,
                    :paymentId,
                    :title,
                    :description,
                    :travelCode,
                    :courseType,
                    :region,
                    :isPublic
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("memberId", request.memberId())
                .addValue("resultId", request.resultId())
                .addValue("paymentId", request.paymentId())
                .addValue("title", request.title().trim())
                .addValue("description", request.description().trim())
                .addValue("travelCode", blankToNull(request.travelCode()))
                .addValue("courseType", defaultValue(request.courseType(), "CUSTOM"))
                .addValue("region", blankToNull(request.region()))
                .addValue("isPublic", defaultValue(request.isPublic(), "N"));

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(sql, params, keyHolder, new String[]{"COURSE_ID"});

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("COURSE_ID 생성에 실패했습니다.");
        }

        return key.longValue();
    }

    private void updateTravelCourse(Long courseId, CourseBuilderSaveRequest request) {
        String sql = """
                UPDATE TRAVEL_COURSES
                   SET TITLE = :title,
                       DESCRIPTION = :description,
                       REGION = :region,
                       IS_PUBLIC = :isPublic,
                       UPDATED_AT = SYSDATE
                 WHERE COURSE_ID = :courseId
                   AND MEMBER_ID = :memberId
                   AND COURSE_TYPE = 'CUSTOM'
                """;
        int updatedCount = jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("courseId", courseId)
                        .addValue("memberId", request.memberId())
                        .addValue("title", request.title().trim())
                        .addValue("description", request.description().trim())
                        .addValue("region", blankToNull(request.region()))
                        .addValue("isPublic", defaultValue(request.isPublic(), "N"))
        );
        if (updatedCount != 1) {
            throw new IllegalArgumentException("코스 수정 권한을 확인할 수 없습니다.");
        }
    }

    private Long findOrInsertPlace(Long memberId, CourseBuilderSaveRequest.PlaceRequest place) {
        if (place.placeId() != null) {
            return place.placeId();
        }

        String apiProvider = defaultValue(place.apiProvider(), "KAKAO");

        if (place.apiPlaceId() == null || place.apiPlaceId().trim().isEmpty()) {
            throw new IllegalArgumentException("API_PLACE_ID가 없는 새 장소는 저장할 수 없습니다.");
        }

        Long existingPlaceId = findPlaceIdByApi(apiProvider, place.apiPlaceId());

        if (existingPlaceId != null) {
            return existingPlaceId;
        }

        return insertPlace(memberId, apiProvider, place);
    }

    private Long findPlaceIdByApi(String apiProvider, String apiPlaceId) {
        String sql = """
                SELECT PLACE_ID
                FROM PLACES
                WHERE API_PROVIDER = :apiProvider
                  AND API_PLACE_ID = :apiPlaceId
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("apiProvider", apiProvider)
                .addValue("apiPlaceId", apiPlaceId);

        List<Long> result = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("PLACE_ID")
        );

        if (result.isEmpty()) {
            return null;
        }

        return result.get(0);
    }

    private Long insertPlace(Long memberId, String apiProvider, CourseBuilderSaveRequest.PlaceRequest place) {
        String sql = """
                INSERT INTO PLACES (
                    API_PROVIDER,
                    API_PLACE_ID,
                    CONTENT_ID,
                    NAME,
                    CATEGORY,
                    API_CATEGORY,
                    REGION,
                    ADDRESS,
                    ROAD_ADDRESS,
                    LATITUDE,
                    LONGITUDE,
                    PHONE,
                    PLACE_URL,
                    RATING,
                    REVIEW_COUNT,
                    DESCRIPTION,
                    IMAGE_URL,
                    SOURCE_TYPE,
                    RECOMMEND_YN,
                    APPROVAL_STATUS,
                    CREATED_BY_MEMBER_ID,
                    IS_ACTIVE
                )
                VALUES (
                    :apiProvider,
                    :apiPlaceId,
                    :contentId,
                    :name,
                    :category,
                    :apiCategory,
                    :region,
                    :address,
                    :roadAddress,
                    :latitude,
                    :longitude,
                    :phone,
                    :placeUrl,
                    :rating,
                    :reviewCount,
                    :description,
                    :imageUrl,
                    :sourceType,
                    :recommendYn,
                    :approvalStatus,
                    :createdByMemberId,
                    'Y'
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("apiProvider", apiProvider)
                .addValue("apiPlaceId", place.apiPlaceId())
                .addValue("contentId", place.contentId())
                .addValue("name", place.name())
                .addValue("category", defaultValue(place.category(), "TOUR"))
                .addValue("apiCategory", blankToNull(place.apiCategory()))
                .addValue("region", place.region())
                .addValue("address", place.address())
                .addValue("roadAddress", blankToNull(place.roadAddress()))
                .addValue("latitude", place.latitude())
                .addValue("longitude", place.longitude())
                .addValue("phone", blankToNull(place.phone()))
                .addValue("placeUrl", blankToNull(place.placeUrl()))
                .addValue("rating", place.rating() == null ? 0.0 : place.rating())
                .addValue("reviewCount", place.reviewCount() == null ? 0 : place.reviewCount())
                .addValue("description", blankToNull(place.description()))
                .addValue("imageUrl", blankToNull(place.imageUrl()))
                .addValue("sourceType", defaultValue(place.sourceType(), "USER_SELECTED"))
                .addValue("recommendYn", defaultValue(place.recommendYn(), "N"))
                .addValue("approvalStatus", defaultValue(place.approvalStatus(), "PENDING"))
                .addValue("createdByMemberId", memberId);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(sql, params, keyHolder, new String[]{"PLACE_ID"});

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("PLACE_ID 생성에 실패했습니다.");
        }

        return key.longValue();
    }

    private void insertCourseDetail(
            Long courseId,
            Long placeId,
            CourseBuilderSaveRequest.PlaceRequest place,
            int defaultOrder,
            Integer incomingDistanceM,
            Integer incomingDurationMin
    ) {
        String sql = """
                INSERT INTO COURSE_DETAILS (
                    COURSE_ID,
                    PLACE_ID,
                    DAY_NO,
                    PLACE_ORDER,
                    MEMO,
                    VISIT_TIME,
                    STAY_MINUTES,
                    DISTANCE_FROM_PREV_KM,
                    TRAVEL_MINUTES_FROM_PREV
                )
                VALUES (
                    :courseId,
                    :placeId,
                    :dayNo,
                    :placeOrder,
                    :memo,
                    :visitTime,
                    :stayMinutes,
                    :distanceFromPrevKm,
                    :travelMinutesFromPrev
                )
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("courseId", courseId)
                .addValue("placeId", placeId)
                .addValue("dayNo", place.dayNo() == null ? 1 : place.dayNo())
                .addValue("placeOrder", place.placeOrder() == null ? defaultOrder : place.placeOrder())
                .addValue("memo", blankToNull(place.memo()))
                .addValue("visitTime", blankToNull(place.visitTime()))
                .addValue("stayMinutes", place.stayMinutes())
                .addValue(
                        "distanceFromPrevKm",
                        incomingDistanceM == null ? 0.0 : incomingDistanceM / 1000.0
                )
                .addValue(
                        "travelMinutesFromPrev",
                        incomingDurationMin == null ? 0.0 : incomingDurationMin.doubleValue()
                );

        jdbcTemplate.update(sql, params);
    }

    private void updateCourseTotals(Long courseId) {
        String sql = """
                UPDATE TRAVEL_COURSES TC
                   SET TOTAL_DISTANCE_KM = (
                           SELECT NVL(SUM(CD.DISTANCE_FROM_PREV_KM), 0)
                             FROM COURSE_DETAILS CD
                            WHERE CD.COURSE_ID = TC.COURSE_ID
                       ),
                       TOTAL_TRAVEL_MINUTES = (
                           SELECT NVL(SUM(CD.TRAVEL_MINUTES_FROM_PREV), 0)
                             FROM COURSE_DETAILS CD
                            WHERE CD.COURSE_ID = TC.COURSE_ID
                       ),
                       TOTAL_VISIT_MINUTES = (
                           SELECT NVL(SUM(CD.STAY_MINUTES), 0)
                             FROM COURSE_DETAILS CD
                            WHERE CD.COURSE_ID = TC.COURSE_ID
                       ),
                       TOTAL_COURSE_MINUTES = (
                           SELECT NVL(SUM(CD.TRAVEL_MINUTES_FROM_PREV), 0)
                                + NVL(SUM(CD.STAY_MINUTES), 0)
                             FROM COURSE_DETAILS CD
                            WHERE CD.COURSE_ID = TC.COURSE_ID
                       ),
                       UPDATED_AT = SYSDATE
                 WHERE TC.COURSE_ID = :courseId
                """;

        jdbcTemplate.update(
                sql,
                new MapSqlParameterSource().addValue("courseId", courseId)
        );
    }

    /**
     * 지도 코스 저장 후 현재 프로젝트의 CourseSaveResponse 형식에 맞는 집계값을 반환한다.
     */
    private CourseSaveResponse getSavedCourseResponse(Long courseId) {
        String sql = """
                SELECT TC.COURSE_ID,
                       TC.TITLE,
                       TC.TOTAL_DISTANCE_KM,
                       TC.TOTAL_TRAVEL_MINUTES,
                       TC.TOTAL_VISIT_MINUTES,
                       TC.TOTAL_COURSE_MINUTES,
                       (SELECT COUNT(*)
                          FROM COURSE_DETAILS CD
                         WHERE CD.COURSE_ID = TC.COURSE_ID) AS PLACE_COUNT,
                       (SELECT COUNT(DISTINCT CD.DAY_NO)
                          FROM COURSE_DETAILS CD
                         WHERE CD.COURSE_ID = TC.COURSE_ID) AS DAY_COUNT
                  FROM TRAVEL_COURSES TC
                 WHERE TC.COURSE_ID = :courseId
                """;

        return jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource().addValue("courseId", courseId),
                (rs, rowNum) -> CourseSaveResponse.builder()
                        .courseId(rs.getLong("COURSE_ID"))
                        .title(rs.getString("TITLE"))
                        .placeCount(rs.getInt("PLACE_COUNT"))
                        .dayCount(rs.getInt("DAY_COUNT"))
                        .totalDistanceKm(rs.getDouble("TOTAL_DISTANCE_KM"))
                        .totalTravelTimeMinutes(rs.getDouble("TOTAL_TRAVEL_MINUTES"))
                        .totalVisitTimeMinutes(rs.getInt("TOTAL_VISIT_MINUTES"))
                        .totalCourseTimeMinutes(rs.getDouble("TOTAL_COURSE_MINUTES"))
                        .build()
        );
    }

    private Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Double getNullableDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }

    private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String defaultValue(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
