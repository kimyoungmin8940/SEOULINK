package com.seoulink.backend.coursebuilder.service;

import com.seoulink.backend.coursebuilder.dto.CourseBuilderPlaceDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class KakaoPlaceImportService {

    private static final String KAKAO_KEYWORD_SEARCH_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final JsonMapper jsonMapper;
    private final RestTemplate restTemplate;
    private final String kakaoRestApiKey;

    public KakaoPlaceImportService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            JsonMapper jsonMapper,
            @Value("${kakao.rest-api-key}") String kakaoRestApiKey
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.restTemplate = new RestTemplate();
        this.kakaoRestApiKey = kakaoRestApiKey;
    }

    /**
     * 카카오 장소 검색 API에서 장소를 검색한 후
     * PLACES 테이블에 저장하고 저장된 장소 목록을 반환합니다.
     */
    @Transactional
    public List<CourseBuilderPlaceDto> importPlaces(
            String region,
            String category,
            String keyword
    ) {
        validateApiKey();

        String selectedCategory = normalizeCategory(category);
        String query = makeSearchQuery(
                region,
                selectedCategory,
                keyword
        );

        String expectedGroupCode =
                getKakaoCategoryGroupCode(selectedCategory);

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(KAKAO_KEYWORD_SEARCH_URL)
                    .queryParam("query", query)
                    .queryParam("size", 15)
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();

            headers.set(
                    "Authorization",
                    "KakaoAK " + kakaoRestApiKey.trim()
            );

            HttpEntity<Void> entity =
                    new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            uri,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            String responseBody = response.getBody();

            if (!hasText(responseBody)) {
                return List.of();
            }

            JsonNode root =
                    jsonMapper.readTree(responseBody);

            JsonNode documents =
                    root.path("documents");

            if (!documents.isArray()) {
                return List.of();
            }

            List<String> kakaoPlaceIds =
                    new ArrayList<>();

            for (JsonNode document : documents) {
                String kakaoPlaceId =
                        text(document, "id");

                String kakaoGroupCode =
                        text(
                                document,
                                "category_group_code"
                        );

                if (!hasText(kakaoPlaceId)) {
                    continue;
                }

                /*
                 * 요청한 카테고리와 카카오 API의
                 * 카테고리 그룹이 다르면 제외합니다.
                 */
                if (
                        hasText(expectedGroupCode)
                                && hasText(kakaoGroupCode)
                                && !expectedGroupCode.equals(
                                kakaoGroupCode
                        )
                ) {
                    continue;
                }

                boolean saved = upsertPlace(
                        document,
                        selectedCategory,
                        region
                );

                if (saved) {
                    kakaoPlaceIds.add(kakaoPlaceId);
                }
            }

            if (kakaoPlaceIds.isEmpty()) {
                return List.of();
            }

            return findImportedPlaces(kakaoPlaceIds);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "카카오 장소 가져오기에 실패했습니다. 검색어: "
                            + query,
                    e
            );
        }
    }

    /**
     * application.properties에 카카오 REST API 키가
     * 입력되어 있는지 확인합니다.
     */
    private void validateApiKey() {
        if (!hasText(kakaoRestApiKey)) {
            throw new IllegalStateException(
                    "application.properties의 "
                            + "kakao.rest-api-key에 "
                            + "카카오 REST API 키를 입력해야 합니다."
            );
        }
    }

    /**
     * 프론트에서 소문자로 보내더라도
     * 대문자 카테고리로 통일합니다.
     */
    private String normalizeCategory(
            String category
    ) {
        String normalized =
                hasText(category)
                        ? category
                        .trim()
                        .toUpperCase(Locale.ROOT)
                        : "TOUR";

        return switch (normalized) {
            case "TOUR",
                 "RESTAURANT",
                 "CAFE",
                 "HOTEL" -> normalized;

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 카테고리입니다: "
                            + category
                            + " "
                            + "(TOUR, RESTAURANT, "
                            + "CAFE, HOTEL만 가능)"
            );
        };
    }

    /**
     * 카카오 장소 정보를 PLACES 테이블에 저장합니다.
     *
     * 이미 존재하면 UPDATE,
     * 존재하지 않으면 INSERT 합니다.
     */
    private boolean upsertPlace(
            JsonNode document,
            String category,
            String regionParam
    ) {
        String apiPlaceId =
                text(document, "id");

        String name =
                text(document, "place_name");

        String apiCategory =
                text(document, "category_name");

        String address =
                text(document, "address_name");

        String roadAddress =
                text(document, "road_address_name");

        String phone =
                text(document, "phone");

        String placeUrl =
                text(document, "place_url");

        Double latitude =
                parseDouble(
                        text(document, "y")
                );

        Double longitude =
                parseDouble(
                        text(document, "x")
                );

        /*
         * PLACES 테이블의 필수값이 없으면
         * 저장하지 않습니다.
         */
        if (
                !hasText(apiPlaceId)
                        || !hasText(name)
                        || !hasText(address)
                        || latitude == null
                        || longitude == null
        ) {
            return false;
        }

        String region = resolveRegion(
                regionParam,
                address
        );

        PlaceTags tags =
                classifyDefaultTags(category);

        String sql = """
                MERGE INTO PLACES p
                USING (
                    SELECT ? AS API_PLACE_ID
                    FROM DUAL
                ) src
                ON (
                    p.API_PROVIDER = 'KAKAO'
                    AND p.API_PLACE_ID = src.API_PLACE_ID
                )

                WHEN MATCHED THEN
                    UPDATE SET
                        NAME = ?,
                        CATEGORY = ?,
                        API_CATEGORY = ?,
                        REGION = ?,
                        ADDRESS = ?,
                        ROAD_ADDRESS = ?,
                        LATITUDE = ?,
                        LONGITUDE = ?,
                        PHONE = ?,
                        PLACE_URL = ?,
                        DESCRIPTION = ?,
                        TAG_HISTORY = ?,
                        TAG_MODERN = ?,
                        TAG_BUDGET = ?,
                        TAG_LUXURY = ?,
                        TAG_STABLE = ?,
                        TAG_DOPAMINE = ?,
                        TAG_RELAX = ?,
                        TAG_PACKED = ?,
                        IS_ACTIVE = 'Y',
                        UPDATED_AT = SYSDATE

                WHEN NOT MATCHED THEN
                    INSERT (
                        API_PROVIDER,
                        API_PLACE_ID,
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
                        TAG_HISTORY,
                        TAG_MODERN,
                        TAG_BUDGET,
                        TAG_LUXURY,
                        TAG_STABLE,
                        TAG_DOPAMINE,
                        TAG_RELAX,
                        TAG_PACKED,
                        SOURCE_TYPE,
                        RECOMMEND_YN,
                        APPROVAL_STATUS,
                        IS_ACTIVE
                    )
                    VALUES (
                        'KAKAO',
                        src.API_PLACE_ID,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        0,
                        0,
                        ?,
                        NULL,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        ?,
                        'API_IMPORTED',
                        'N',
                        'PENDING',
                        'Y'
                    )
                """;

        jdbcTemplate.update(
                sql,

                // MERGE 검색 조건
                apiPlaceId,

                // WHEN MATCHED UPDATE 값
                name,
                category,
                emptyToNull(apiCategory),
                region,
                address,
                emptyToNull(roadAddress),
                latitude,
                longitude,
                emptyToNull(phone),
                emptyToNull(placeUrl),
                emptyToNull(apiCategory),
                tags.history(),
                tags.modern(),
                tags.budget(),
                tags.luxury(),
                tags.stable(),
                tags.dopamine(),
                tags.relax(),
                tags.packed(),

                // WHEN NOT MATCHED INSERT 값
                name,
                category,
                emptyToNull(apiCategory),
                region,
                address,
                emptyToNull(roadAddress),
                latitude,
                longitude,
                emptyToNull(phone),
                emptyToNull(placeUrl),
                emptyToNull(apiCategory),
                tags.history(),
                tags.modern(),
                tags.budget(),
                tags.luxury(),
                tags.stable(),
                tags.dopamine(),
                tags.relax(),
                tags.packed()
        );

        return true;
    }

    /**
     * 카카오에서 새로 가져온 장소의 기본 태그를
     * 카테고리에 따라 설정합니다.
     */
    private PlaceTags classifyDefaultTags(
            String category
    ) {
        return switch (category) {
            case "TOUR" -> new PlaceTags(
                    "Y", // 역사
                    "N", // 현대
                    "N", // 가성비
                    "N", // 럭셔리
                    "Y", // 안정
                    "N", // 도파민
                    "N", // 휴식
                    "N"  // 빽빽한 일정
            );

            case "CAFE" -> new PlaceTags(
                    "N",
                    "Y",
                    "N",
                    "N",
                    "N",
                    "Y",
                    "Y",
                    "N"
            );

            case "RESTAURANT" -> new PlaceTags(
                    "N",
                    "N",
                    "Y",
                    "N",
                    "N",
                    "N",
                    "N",
                    "Y"
            );

            case "HOTEL" -> new PlaceTags(
                    "N",
                    "N",
                    "N",
                    "N",
                    "Y",
                    "N",
                    "Y",
                    "N"
            );

            default -> new PlaceTags(
                    "N",
                    "N",
                    "N",
                    "N",
                    "N",
                    "N",
                    "N",
                    "N"
            );
        };
    }

    /**
     * 방금 저장하거나 갱신한 카카오 장소를
     * DB에서 다시 조회합니다.
     */
    private List<CourseBuilderPlaceDto> findImportedPlaces(
            List<String> kakaoPlaceIds
    ) {
        String sql = """
                SELECT
                    PLACE_ID,
                    NAME,
                    CATEGORY,
                    REGION,
                    ADDRESS,
                    LATITUDE,
                    LONGITUDE,
                    RATING,
                    REVIEW_COUNT,
                    IMAGE_URL,
                    TAG_HISTORY,
                    TAG_MODERN,
                    TAG_BUDGET,
                    TAG_LUXURY,
                    TAG_STABLE,
                    TAG_DOPAMINE,
                    TAG_RELAX,
                    TAG_PACKED
                FROM PLACES
                WHERE API_PROVIDER = 'KAKAO'
                  AND API_PLACE_ID IN (:ids)
                  AND IS_ACTIVE = 'Y'
                ORDER BY NAME
                """;

        MapSqlParameterSource params =
                new MapSqlParameterSource();

        params.addValue(
                "ids",
                kakaoPlaceIds
        );

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                this::mapPlace
        );
    }

    /**
     * PLACES 조회 결과를 CourseBuilderPlaceDto로 변환합니다.
     */
    private CourseBuilderPlaceDto mapPlace(
            ResultSet rs,
            int rowNum
    ) throws SQLException {
        Object ratingObject =
                rs.getObject("RATING");

        Object reviewCountObject =
                rs.getObject("REVIEW_COUNT");

        return new CourseBuilderPlaceDto(
                rs.getLong("PLACE_ID"),
                rs.getString("NAME"),
                rs.getString("CATEGORY"),
                rs.getString("REGION"),
                rs.getString("ADDRESS"),
                rs.getDouble("LATITUDE"),
                rs.getDouble("LONGITUDE"),

                ratingObject == null
                        ? null
                        : rs.getDouble("RATING"),

                reviewCountObject == null
                        ? null
                        : rs.getInt("REVIEW_COUNT"),

                rs.getString("IMAGE_URL"),
                rs.getString("TAG_HISTORY"),
                rs.getString("TAG_MODERN"),
                rs.getString("TAG_BUDGET"),
                rs.getString("TAG_LUXURY"),
                rs.getString("TAG_STABLE"),
                rs.getString("TAG_DOPAMINE"),
                rs.getString("TAG_RELAX"),
                rs.getString("TAG_PACKED")
        );
    }

    /**
     * keyword가 입력되면 keyword를 사용하고,
     * 없으면 지역 + 카테고리로 검색어를 만듭니다.
     */
    private String makeSearchQuery(
            String region,
            String category,
            String keyword
    ) {
        if (hasText(keyword)) {
            return keyword.trim();
        }

        String regionText =
                hasText(region)
                        ? region.trim()
                        : "서울";

        return regionText
                + " "
                + getCategoryKeyword(category);
    }

    private String getCategoryKeyword(
            String category
    ) {
        return switch (category) {
            case "TOUR" -> "관광지";
            case "RESTAURANT" -> "음식점";
            case "CAFE" -> "카페";
            case "HOTEL" -> "숙소";
            default -> "관광지";
        };
    }

    /**
     * 카카오 로컬 API 카테고리 그룹 코드입니다.
     */
    private String getKakaoCategoryGroupCode(
            String category
    ) {
        return switch (category) {
            case "TOUR" -> "AT4";
            case "RESTAURANT" -> "FD6";
            case "CAFE" -> "CE7";
            case "HOTEL" -> "AD5";
            default -> "";
        };
    }

    /**
     * 특정 구를 검색한 경우 해당 구를 사용합니다.
     *
     * 서울 전체 검색처럼 구 이름이 전달되지 않은 경우에는
     * 카카오 주소에서 실제 구 이름을 가져옵니다.
     */
    private String resolveRegion(
            String regionParam,
            String address
    ) {
        if (
                hasText(regionParam)
                        && regionParam.trim().endsWith("구")
        ) {
            return regionParam.trim();
        }

        String extractedRegion =
                extractRegion(address);

        if (hasText(extractedRegion)) {
            return extractedRegion;
        }

        return "서울";
    }

    /**
     * 주소에서 종로구, 강남구 등의 구 이름을 추출합니다.
     */
    private String extractRegion(
            String address
    ) {
        if (!hasText(address)) {
            return "";
        }

        String[] parts =
                address.trim().split("\\s+");

        for (String part : parts) {
            if (part.endsWith("구")) {
                return part;
            }
        }

        return "";
    }

    private Double parseDouble(
            String value
    ) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return Double.parseDouble(
                    value.trim()
            );

        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Jackson 3 JsonNode에서 문자열을 가져옵니다.
     */
    private String text(
            JsonNode node,
            String fieldName
    ) {
        if (node == null) {
            return "";
        }

        JsonNode value =
                node.path(fieldName);

        if (
                value.isMissingNode()
                        || value.isNull()
        ) {
            return "";
        }

        return value.asString("");
    }

    private String emptyToNull(
            String value
    ) {
        return hasText(value)
                ? value.trim()
                : null;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.trim().isEmpty();
    }

    /**
     * PLACES 테이블의 추천 태그 값 묶음입니다.
     */
    private record PlaceTags(
            String history,
            String modern,
            String budget,
            String luxury,
            String stable,
            String dopamine,
            String relax,
            String packed
    ) {
    }
}