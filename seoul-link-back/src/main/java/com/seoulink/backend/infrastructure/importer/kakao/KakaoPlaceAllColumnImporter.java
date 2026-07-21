package com.seoulink.backend.infrastructure.importer.kakao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "place.all-column-import.enabled", havingValue = "true")
public class KakaoPlaceAllColumnImporter implements CommandLineRunner {

    private static final String KAKAO_KEYWORD_SEARCH_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    public KakaoPlaceAllColumnImporter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            throw new IllegalStateException("application.properties에 kakao.rest-api-key를 입력해주세요.");
        }

        List<PlaceSeed> seeds = PlaceSeedData.seeds();

        System.out.println("========================================");
        System.out.println("PLACES 전체 컬럼 Import 시작: " + seeds.size() + "개");
        System.out.println("========================================");

        int insertCount = 0;
        int updateCount = 0;
        int failCount = 0;

        for (PlaceSeed seed : seeds) {
            try {
                validateSeed(seed);
                KakaoPlace kakaoPlace = searchKakaoPlace(seed);
                UpsertResult result = upsertPlace(seed, kakaoPlace);

                if (result.inserted()) {
                    insertCount++;
                    System.out.printf("[INSERT] PLACE_ID=%d / %s / %s%n", result.placeId(), seed.category(), seed.name());
                } else {
                    updateCount++;
                    System.out.printf("[UPDATE] PLACE_ID=%d / %s / %s%n", result.placeId(), seed.category(), seed.name());
                }
            } catch (Exception e) {
                failCount++;
                System.out.printf("[실패] %s / %s%n", seed.name(), e.getMessage());
            }
        }

        System.out.println("========================================");
        System.out.println("PLACES 전체 컬럼 Import 종료");
        System.out.println("INSERT: " + insertCount + "개");
        System.out.println("UPDATE: " + updateCount + "개");
        System.out.println("실패: " + failCount + "개");
        System.out.println("========================================");
    }

    private void validateSeed(PlaceSeed seed) {
        if (seed.name() == null || seed.name().isBlank()) {
            throw new IllegalArgumentException("장소명이 비어 있습니다.");
        }

        if (!List.of("TOUR", "RESTAURANT", "CAFE", "HOTEL").contains(seed.category())) {
            throw new IllegalArgumentException("CATEGORY는 TOUR, RESTAURANT, CAFE, HOTEL 중 하나여야 합니다.");
        }

        if (!List.of("RECOMMEND", "USER_SELECTED", "API_IMPORTED").contains(seed.sourceType())) {
            throw new IllegalArgumentException("SOURCE_TYPE은 RECOMMEND, USER_SELECTED, API_IMPORTED 중 하나여야 합니다.");
        }

        if (!List.of("Y", "N").contains(seed.recommendYn())) {
            throw new IllegalArgumentException("RECOMMEND_YN은 Y 또는 N이어야 합니다.");
        }

        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(seed.approvalStatus())) {
            throw new IllegalArgumentException("APPROVAL_STATUS는 PENDING, APPROVED, REJECTED 중 하나여야 합니다.");
        }

        if (!List.of("Y", "N").contains(seed.isActive())) {
            throw new IllegalArgumentException("IS_ACTIVE는 Y 또는 N이어야 합니다.");
        }
    }

    private KakaoPlace searchKakaoPlace(PlaceSeed seed) {
        List<Map<String, Object>> documents = requestKakaoKeywordSearch(
                "서울 " + seed.searchKeyword(),
                getKakaoCategoryGroupCode(seed.category())
        );

        if (documents.isEmpty()) {
            documents = requestKakaoKeywordSearch("서울 " + seed.searchKeyword(), null);
        }

        if (documents.isEmpty()) {
            throw new IllegalStateException("카카오 장소 검색 결과가 없습니다.");
        }

        return KakaoPlace.from(selectBestDocument(seed, documents));
    }

    private List<Map<String, Object>> requestKakaoKeywordSearch(String query, String categoryGroupCode) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(KAKAO_KEYWORD_SEARCH_URL)
                .queryParam("query", query)
                .queryParam("size", 10)
                .queryParam("page", 1);

        if (categoryGroupCode != null && !categoryGroupCode.isBlank()) {
            builder.queryParam("category_group_code", categoryGroupCode);
        }

        URI uri = builder.build().encode().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoRestApiKey.trim());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );

            Object documents = response.getBody() == null ? null : response.getBody().get("documents");

            if (documents instanceof List<?> documentList) {
                return documentList.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }

            return List.of();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("카카오 장소 검색 API 오류: " + e.getResponseBodyAsString());
        }
    }

    private String getKakaoCategoryGroupCode(String category) {
        return switch (category) {
            case "RESTAURANT" -> "FD6";
            case "CAFE" -> "CE7";
            case "HOTEL" -> "AD5";
            default -> "AT4";
        };
    }

    private Map<String, Object> selectBestDocument(PlaceSeed seed, List<Map<String, Object>> documents) {
        String seedName = normalize(seed.name());
        String searchKeyword = normalize(seed.searchKeyword());

        return documents.stream()
                .filter(this::isSeoulPlace)
                .filter(document -> {
                    String placeName = normalize(text(document, "place_name"));
                    return placeName.contains(seedName)
                            || seedName.contains(placeName)
                            || placeName.contains(searchKeyword)
                            || searchKeyword.contains(placeName);
                })
                .findFirst()
                .orElseGet(() -> documents.stream()
                        .filter(this::isSeoulPlace)
                        .findFirst()
                        .orElse(documents.get(0)));
    }

    private boolean isSeoulPlace(Map<String, Object> document) {
        String address = text(document, "address_name");
        String roadAddress = text(document, "road_address_name");
        String fullAddress = address + " " + roadAddress;
        return fullAddress.contains("서울");
    }

    private UpsertResult upsertPlace(PlaceSeed seed, KakaoPlace kakaoPlace) {
        Long existingPlaceId = findPlaceIdByKakaoId(kakaoPlace.id());

        if (existingPlaceId != null) {
            updatePlace(existingPlaceId, seed, kakaoPlace);
            return new UpsertResult(existingPlaceId, false);
        }

        existingPlaceId = findPlaceIdByName(seed.name(), kakaoPlace.name(), extractRegion(kakaoPlace.address(), kakaoPlace.roadAddress()));

        if (existingPlaceId != null) {
            updatePlace(existingPlaceId, seed, kakaoPlace);
            return new UpsertResult(existingPlaceId, false);
        }

        insertPlace(seed, kakaoPlace);
        Long insertedPlaceId = findPlaceIdByKakaoId(kakaoPlace.id());
        return new UpsertResult(insertedPlaceId, true);
    }

    private Long findPlaceIdByKakaoId(String kakaoPlaceId) {
        String sql = """
                SELECT PLACE_ID
                FROM PLACES
                WHERE API_PROVIDER = 'KAKAO'
                  AND API_PLACE_ID = :apiPlaceId
                """;

        List<Long> result = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("apiPlaceId", kakaoPlaceId),
                (rs, rowNum) -> rs.getLong("PLACE_ID")
        );

        return result.isEmpty() ? null : result.get(0);
    }

    private Long findPlaceIdByName(String seedName, String kakaoName, String region) {
        String sql = """
                SELECT PLACE_ID
                FROM PLACES
                WHERE REGION = :region
                  AND (NAME = :seedName OR NAME = :kakaoName)
                FETCH FIRST 1 ROWS ONLY
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("region", region)
                .addValue("seedName", seedName)
                .addValue("kakaoName", kakaoName);

        List<Long> result = jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("PLACE_ID")
        );

        return result.isEmpty() ? null : result.get(0);
    }

    private void insertPlace(PlaceSeed seed, KakaoPlace kakaoPlace) {
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
                    CREATED_BY_MEMBER_ID,
                    INDOOR_YN,
                    RAIN_OK_YN,
                    NIGHT_OK_YN,
                    AVG_STAY_MINUTES,
                    PRICE_LEVEL,
                    IS_ACTIVE,
                    CREATED_AT,
                    UPDATED_AT
                )
                VALUES (
                    'KAKAO',
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
                    :tagHistory,
                    :tagModern,
                    :tagBudget,
                    :tagLuxury,
                    :tagStable,
                    :tagDopamine,
                    :tagRelax,
                    :tagPacked,
                    :sourceType,
                    :recommendYn,
                    :approvalStatus,
                    :createdByMemberId,
                    :indoorYn,
                    :rainOkYn,
                    :nightOkYn,
                    :avgStayMinutes,
                    :priceLevel,
                    :isActive,
                    SYSDATE,
                    SYSDATE
                )
                """;

        jdbcTemplate.update(sql, createParams(seed, kakaoPlace));
    }

    private void updatePlace(Long placeId, PlaceSeed seed, KakaoPlace kakaoPlace) {
        String sql = """
                UPDATE PLACES
                SET
                    API_PROVIDER = 'KAKAO',
                    API_PLACE_ID = :apiPlaceId,
                    CONTENT_ID = :contentId,
                    NAME = :name,
                    CATEGORY = :category,
                    API_CATEGORY = :apiCategory,
                    REGION = :region,
                    ADDRESS = :address,
                    ROAD_ADDRESS = :roadAddress,
                    LATITUDE = :latitude,
                    LONGITUDE = :longitude,
                    PHONE = :phone,
                    PLACE_URL = :placeUrl,
                    RATING = :rating,
                    REVIEW_COUNT = :reviewCount,
                    DESCRIPTION = :description,
                    IMAGE_URL = :imageUrl,
                    TAG_HISTORY = :tagHistory,
                    TAG_MODERN = :tagModern,
                    TAG_BUDGET = :tagBudget,
                    TAG_LUXURY = :tagLuxury,
                    TAG_STABLE = :tagStable,
                    TAG_DOPAMINE = :tagDopamine,
                    TAG_RELAX = :tagRelax,
                    TAG_PACKED = :tagPacked,
                    SOURCE_TYPE = :sourceType,
                    RECOMMEND_YN = :recommendYn,
                    APPROVAL_STATUS = :approvalStatus,
                    CREATED_BY_MEMBER_ID = :createdByMemberId,
                    INDOOR_YN = :indoorYn,
                    RAIN_OK_YN = :rainOkYn,
                    NIGHT_OK_YN = :nightOkYn,
                    AVG_STAY_MINUTES = :avgStayMinutes,
                    PRICE_LEVEL = :priceLevel,
                    IS_ACTIVE = :isActive,
                    UPDATED_AT = SYSDATE
                WHERE PLACE_ID = :placeId
                """;

        MapSqlParameterSource params = createParams(seed, kakaoPlace)
                .addValue("placeId", placeId);

        jdbcTemplate.update(sql, params);
    }

    private MapSqlParameterSource createParams(PlaceSeed seed, KakaoPlace kakaoPlace) {
        return new MapSqlParameterSource()
                .addValue("apiPlaceId", kakaoPlace.id())
                .addValue("contentId", seed.contentId())
                .addValue("name", seed.name())
                .addValue("category", seed.category())
                .addValue("apiCategory", blankToNull(kakaoPlace.categoryName()))
                .addValue("region", extractRegion(kakaoPlace.address(), kakaoPlace.roadAddress()))
                .addValue("address", defaultAddress(kakaoPlace.address(), kakaoPlace.roadAddress()))
                .addValue("roadAddress", blankToNull(kakaoPlace.roadAddress()))
                .addValue("latitude", kakaoPlace.latitude())
                .addValue("longitude", kakaoPlace.longitude())
                .addValue("phone", blankToNull(kakaoPlace.phone()))
                .addValue("placeUrl", blankToNull(kakaoPlace.placeUrl()))
                .addValue("rating", seed.rating() == null ? 0.0 : seed.rating())
                .addValue("reviewCount", seed.reviewCount() == null ? 0 : seed.reviewCount())
                .addValue("description", blankToNull(seed.description()))
                .addValue("imageUrl", blankToNull(seed.imageUrl()))
                .addValue("tagHistory", seed.tagHistoryYn())
                .addValue("tagModern", seed.tagModernYn())
                .addValue("tagBudget", seed.tagBudgetYn())
                .addValue("tagLuxury", seed.tagLuxuryYn())
                .addValue("tagStable", seed.tagStableYn())
                .addValue("tagDopamine", seed.tagDopamineYn())
                .addValue("tagRelax", seed.tagRelaxYn())
                .addValue("tagPacked", seed.tagPackedYn())
                .addValue("sourceType", seed.sourceType())
                .addValue("recommendYn", seed.recommendYn())
                .addValue("approvalStatus", seed.approvalStatus())
                .addValue("createdByMemberId", seed.createdByMemberId())
                .addValue("indoorYn", seed.indoorYn())
                .addValue("rainOkYn", seed.rainOkYn())
                .addValue("nightOkYn", seed.nightOkYn())
                .addValue("avgStayMinutes", seed.resolvedAvgStayMinutes())
                .addValue("priceLevel", seed.resolvedPriceLevel().name())
                .addValue("isActive", seed.isActive());
    }

    private String extractRegion(String address, String roadAddress) {
        String addressText = (address == null || address.isBlank()) ? roadAddress : address;

        if (addressText == null || addressText.isBlank()) {
            return "서울";
        }

        for (String part : addressText.split(" ")) {
            if (part.endsWith("구")) {
                return part;
            }
        }

        return "서울";
    }

    private String defaultAddress(String address, String roadAddress) {
        if (address != null && !address.isBlank()) {
            return address;
        }

        return roadAddress;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    private String text(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record KakaoPlace(
            String id,
            String name,
            String categoryName,
            String address,
            String roadAddress,
            Double latitude,
            Double longitude,
            String phone,
            String placeUrl
    ) {
        static KakaoPlace from(Map<String, Object> document) {
            return new KakaoPlace(
                    String.valueOf(document.get("id")),
                    textValue(document, "place_name"),
                    textValue(document, "category_name"),
                    textValue(document, "address_name"),
                    textValue(document, "road_address_name"),
                    Double.valueOf(String.valueOf(document.get("y"))),
                    Double.valueOf(String.valueOf(document.get("x"))),
                    textValue(document, "phone"),
                    textValue(document, "place_url")
            );
        }

        private static String textValue(Map<String, Object> document, String key) {
            Object value = document.get(key);
            return value == null ? "" : String.valueOf(value);
        }
    }

    private record UpsertResult(
            Long placeId,
            boolean inserted
    ) {
    }
}
