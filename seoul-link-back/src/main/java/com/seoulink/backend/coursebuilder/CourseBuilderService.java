package com.seoulink.backend.coursebuilder;

import com.seoulink.backend.coursebuilder.dto.CourseBuilderPlaceResponse;
import com.seoulink.backend.coursebuilder.dto.CourseRouteRequest;
import com.seoulink.backend.coursebuilder.dto.CourseRouteResponse;
import com.seoulink.backend.coursebuilder.dto.CourseSaveRequest;
import com.seoulink.backend.coursebuilder.dto.CourseSaveResponse;
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
            "https://api.openrouteservice.org/v2/directions/foot-walking/geojson";

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
        int normalizedLimit = limit == null ? 120 : Math.max(1, Math.min(limit, 300));

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

        for (int i = 0; i < routePlaces.size() - 1; i++) {
            CourseRouteRequest.RoutePlaceRequest fromPlace = routePlaces.get(i);
            CourseRouteRequest.RoutePlaceRequest toPlace = routePlaces.get(i + 1);

            int fromDayNo = fromPlace.dayNo() == null ? 1 : fromPlace.dayNo();
            int toDayNo = toPlace.dayNo() == null ? 1 : toPlace.dayNo();

            if (fromDayNo != toDayNo) {
                continue;
            }

            segments.add(calculateWalkingRouteSegment(fromPlace, toPlace, i, i + 1, fromDayNo));
        }

        return new CourseRouteResponse(segments);
    }

    private boolean hasValidCoordinate(CourseRouteRequest.RoutePlaceRequest place) {
        return place != null
                && place.latitude() != null
                && place.longitude() != null;
    }

    private CourseRouteResponse.RouteSegmentResponse calculateWalkingRouteSegment(
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace,
            int fromIndex,
            int toIndex,
            int dayNo
    ) {
        try {
            RouteSummary routeSummary = requestOpenRouteServiceWalkingRoute(fromPlace, toPlace);

            return new CourseRouteResponse.RouteSegmentResponse(
                    fromPlace.clientPlaceId(),
                    toPlace.clientPlaceId(),
                    fromPlace.name(),
                    toPlace.name(),
                    fromIndex,
                    toIndex,
                    dayNo,
                    routeSummary.distanceMeter(),
                    routeSummary.durationSecond(),
                    secondsToMinutes(routeSummary.durationSecond()),
                    routeSummary.routePoints(),
                    "길찾기 성공"
            );
        } catch (Exception e) {
            return new CourseRouteResponse.RouteSegmentResponse(
                    fromPlace.clientPlaceId(),
                    toPlace.clientPlaceId(),
                    fromPlace.name(),
                    toPlace.name(),
                    fromIndex,
                    toIndex,
                    dayNo,
                    null,
                    null,
                    null,
                    List.of(),
                    e.getMessage()
            );
        }
    }

    private RouteSummary requestOpenRouteServiceWalkingRoute(
            CourseRouteRequest.RoutePlaceRequest fromPlace,
            CourseRouteRequest.RoutePlaceRequest toPlace
    ) {
        if (openRouteServiceApiKey == null || openRouteServiceApiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenRouteService API 키가 없습니다. application.properties에 openrouteservice.api-key를 설정해주세요.");
        }

        Map<String, Object> requestBody = Map.of(
                "coordinates",
                List.of(
                        List.of(fromPlace.longitude(), fromPlace.latitude()),
                        List.of(toPlace.longitude(), toPlace.latitude())
                ),
                "instructions",
                false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", openRouteServiceApiKey.trim());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ORS_WALKING_DIRECTIONS_URL,
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            return parseOpenRouteServiceRouteSummary(response.getBody());
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("OpenRouteService 길찾기 API 오류: " + e.getResponseBodyAsString());
        }
    }

    private RouteSummary parseOpenRouteServiceRouteSummary(Map responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("OpenRouteService 응답이 비어 있습니다.");
        }

        RouteSummary summaryFromGeoJson = parseOpenRouteServiceGeoJson(responseBody);

        if (summaryFromGeoJson != null) {
            return summaryFromGeoJson;
        }

        RouteSummary summaryFromRoutes = parseOpenRouteServiceRoutes(responseBody);

        if (summaryFromRoutes != null) {
            return summaryFromRoutes;
        }

        Object errorObject = responseBody.get("error");

        if (errorObject != null) {
            throw new IllegalStateException("OpenRouteService 오류: " + errorObject);
        }

        throw new IllegalStateException("OpenRouteService 응답에서 거리/시간 정보를 찾지 못했습니다.");
    }

    private RouteSummary parseOpenRouteServiceRoutes(Map responseBody) {
        Object routesObject = responseBody.get("routes");

        if (!(routesObject instanceof List<?> routes) || routes.isEmpty()) {
            return null;
        }

        Object firstRouteObject = routes.get(0);

        if (!(firstRouteObject instanceof Map<?, ?> firstRoute)) {
            return null;
        }

        Object summaryObject = firstRoute.get("summary");

        if (!(summaryObject instanceof Map<?, ?> summary)) {
            return null;
        }

        Integer distanceMeter = toRoundedInteger(summary.get("distance"));
        Integer durationSecond = toRoundedInteger(summary.get("duration"));

        if (distanceMeter == null || durationSecond == null) {
            return null;
        }

        return new RouteSummary(distanceMeter, durationSecond, List.of());
    }

    private RouteSummary parseOpenRouteServiceGeoJson(Map responseBody) {
        Object featuresObject = responseBody.get("features");

        if (!(featuresObject instanceof List<?> features) || features.isEmpty()) {
            return null;
        }

        Object firstFeatureObject = features.get(0);

        if (!(firstFeatureObject instanceof Map<?, ?> firstFeature)) {
            return null;
        }

        Object propertiesObject = firstFeature.get("properties");

        if (!(propertiesObject instanceof Map<?, ?> properties)) {
            return null;
        }

        Object summaryObject = properties.get("summary");

        if (!(summaryObject instanceof Map<?, ?> summary)) {
            return null;
        }

        Integer distanceMeter = toRoundedInteger(summary.get("distance"));
        Integer durationSecond = toRoundedInteger(summary.get("duration"));

        if (distanceMeter == null || durationSecond == null) {
            return null;
        }

        return new RouteSummary(
                distanceMeter,
                durationSecond,
                parseOpenRouteServiceGeoJsonRoutePoints(firstFeature)
        );
    }

    private List<CourseRouteResponse.RoutePointResponse> parseOpenRouteServiceGeoJsonRoutePoints(
            Map<?, ?> feature
    ) {
        Object geometryObject = feature.get("geometry");

        if (!(geometryObject instanceof Map<?, ?> geometry)) {
            return List.of();
        }

        Object coordinatesObject = geometry.get("coordinates");

        if (!(coordinatesObject instanceof List<?> coordinates)) {
            return List.of();
        }

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

    private record RouteSummary(
            Integer distanceMeter,
            Integer durationSecond,
            List<CourseRouteResponse.RoutePointResponse> routePoints
    ) {
    }

    @Transactional
    public CourseSaveResponse saveCourse(CourseSaveRequest request) {
        validateRequest(request);

        Long courseId = insertTravelCourse(request);

        for (int i = 0; i < request.places().size(); i++) {
            CourseSaveRequest.PlaceRequest place = request.places().get(i);

            Long placeId = findOrInsertPlace(request.memberId(), place);

            insertCourseDetail(courseId, placeId, place, i + 1);
        }

        return new CourseSaveResponse(courseId, "코스가 저장되었습니다.");
    }

    private void validateRequest(CourseSaveRequest request) {
        if (request.memberId() == null) {
            throw new IllegalArgumentException("회원 ID가 없습니다.");
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
    }

    private Long insertTravelCourse(CourseSaveRequest request) {
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

    private Long findOrInsertPlace(Long memberId, CourseSaveRequest.PlaceRequest place) {
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

    private Long insertPlace(Long memberId, String apiProvider, CourseSaveRequest.PlaceRequest place) {
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
            CourseSaveRequest.PlaceRequest place,
            int defaultOrder
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
                .addValue("distanceFromPrevKm", place.moveDistanceM() == null
                        ? 0D
                        : place.moveDistanceM() / 1000D)
                .addValue("travelMinutesFromPrev", place.moveDurationMin() == null
                        ? 0D
                        : place.moveDurationMin().doubleValue());

        jdbcTemplate.update(sql, params);
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
