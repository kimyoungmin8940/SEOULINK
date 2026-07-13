package com.seoulink.backend.importer;

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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "place.import.enabled", havingValue = "true")
public class KakaoPlaceSeedImporter implements CommandLineRunner {

    private static final String KAKAO_KEYWORD_SEARCH_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.rest-api-key:}")
    private String kakaoRestApiKey;

    public KakaoPlaceSeedImporter(NamedParameterJdbcTemplate jdbcTemplate) {
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
        System.out.println("장소 Import 시작: " + seeds.size() + "개");
        System.out.println("========================================");

        int successCount = 0;
        int failCount = 0;

        for (PlaceSeed seed : seeds) {
            try {
                KakaoPlace kakaoPlace = searchKakaoPlace(seed);
                Long placeId = upsertPlace(seed, kakaoPlace);
                successCount++;
                System.out.printf("[성공] PLACE_ID=%d / %s / %s%n", placeId, seed.category(), kakaoPlace.name());
            } catch (Exception e) {
                failCount++;
                System.out.printf("[실패] %s / %s%n", seed.name(), e.getMessage());
            }
        }

        System.out.println("========================================");
        System.out.println("장소 Import 종료");
        System.out.println("성공: " + successCount + "개");
        System.out.println("실패: " + failCount + "개");
        System.out.println("========================================");
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

        Map<String, Object> selectedDocument = selectBestDocument(seed, documents);
        return KakaoPlace.from(selectedDocument);
    }

    private List<Map<String, Object>> requestKakaoKeywordSearch(
            String query,
            String categoryGroupCode
    ) {
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

        ResponseEntity<Map> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        Object documentsObject = Objects.requireNonNull(response.getBody()).get("documents");

        if (!(documentsObject instanceof List<?> documents)) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object document : documents) {
            if (document instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }

        return result;
    }

    private Map<String, Object> selectBestDocument(
            PlaceSeed seed,
            List<Map<String, Object>> documents
    ) {
        String seedName = normalize(seed.name());

        return documents.stream()
                .filter(this::isSeoulPlace)
                .filter(document -> normalize(text(document, "place_name")).equals(seedName))
                .findFirst()
                .orElseGet(() -> documents.stream()
                        .filter(this::isSeoulPlace)
                        .filter(document -> normalize(text(document, "place_name")).contains(seedName)
                                || seedName.contains(normalize(text(document, "place_name"))))
                        .findFirst()
                        .orElseGet(() -> documents.stream()
                                .filter(this::isSeoulPlace)
                                .findFirst()
                                .orElse(documents.get(0))));
    }

    private boolean isSeoulPlace(Map<String, Object> document) {
        String address = text(document, "address_name");
        String roadAddress = text(document, "road_address_name");
        String fullAddress = address + " " + roadAddress;
        return fullAddress.contains("서울");
    }

    private Long upsertPlace(PlaceSeed seed, KakaoPlace kakaoPlace) {
        Long existingPlaceId = findPlaceId(kakaoPlace.id());

        if (existingPlaceId == null) {
            insertPlace(seed, kakaoPlace);
            return findPlaceId(kakaoPlace.id());
        }

        updatePlace(existingPlaceId, seed, kakaoPlace);
        return existingPlaceId;
    }

    private Long findPlaceId(String kakaoPlaceId) {
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
                    IS_ACTIVE
                )
                VALUES (
                    'KAKAO',
                    :apiPlaceId,
                    NULL,
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
                    0,
                    0,
                    NULL,
                    NULL,
                    :tagHistory,
                    :tagModern,
                    :tagBudget,
                    :tagLuxury,
                    :tagStable,
                    :tagDopamine,
                    :tagRelax,
                    :tagPacked,
                    'Y'
                )
                """;

        jdbcTemplate.update(sql, createParams(seed, kakaoPlace));
    }

    private void updatePlace(Long placeId, PlaceSeed seed, KakaoPlace kakaoPlace) {
        String sql = """
                UPDATE PLACES
                SET
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
                    TAG_HISTORY = :tagHistory,
                    TAG_MODERN = :tagModern,
                    TAG_BUDGET = :tagBudget,
                    TAG_LUXURY = :tagLuxury,
                    TAG_STABLE = :tagStable,
                    TAG_DOPAMINE = :tagDopamine,
                    TAG_RELAX = :tagRelax,
                    TAG_PACKED = :tagPacked,
                    IS_ACTIVE = 'Y',
                    UPDATED_AT = SYSDATE
                WHERE PLACE_ID = :placeId
                """;

        MapSqlParameterSource params = createParams(seed, kakaoPlace)
                .addValue("placeId", placeId);

        jdbcTemplate.update(sql, params);
    }

    private MapSqlParameterSource createParams(PlaceSeed seed, KakaoPlace kakaoPlace) {
        Tags tags = Tags.from(seed.themes());

        return new MapSqlParameterSource()
                .addValue("apiPlaceId", kakaoPlace.id())
                .addValue("name", kakaoPlace.name())
                .addValue("category", seed.category())
                .addValue("apiCategory", kakaoPlace.categoryName())
                .addValue("region", extractRegion(kakaoPlace.address(), kakaoPlace.roadAddress()))
                .addValue("address", defaultValue(kakaoPlace.address(), kakaoPlace.roadAddress()))
                .addValue("roadAddress", blankToNull(kakaoPlace.roadAddress()))
                .addValue("latitude", kakaoPlace.latitude())
                .addValue("longitude", kakaoPlace.longitude())
                .addValue("phone", blankToNull(kakaoPlace.phone()))
                .addValue("placeUrl", blankToNull(kakaoPlace.url()))
                .addValue("tagHistory", yn(tags.history()))
                .addValue("tagModern", yn(tags.modern()))
                .addValue("tagBudget", yn(tags.budget()))
                .addValue("tagLuxury", yn(tags.luxury()))
                .addValue("tagStable", yn(tags.stable()))
                .addValue("tagDopamine", yn(tags.dopamine()))
                .addValue("tagRelax", yn(tags.relax()))
                .addValue("tagPacked", yn(tags.packed()));
    }

    private String getKakaoCategoryGroupCode(String category) {
        return switch (category) {
            case "TOUR" -> "AT4";
            case "RESTAURANT" -> "FD6";
            case "CAFE" -> "CE7";
            case "HOTEL" -> "AD5";
            default -> null;
        };
    }

    private String extractRegion(String address, String roadAddress) {
        String fullAddress = defaultValue(address, roadAddress);
        String[] parts = fullAddress.split(" ");

        for (String part : parts) {
            if (part.endsWith("구")) {
                return part;
            }
        }

        return "서울";
    }

    private String defaultValue(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return "서울";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    private String yn(boolean value) {
        return value ? "Y" : "N";
    }

    private String text(Map<String, Object> map, String fieldName) {
        Object value = map.get(fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("\\s+", "").trim();
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
            String url
    ) {
        static KakaoPlace from(Map<String, Object> document) {
            return new KakaoPlace(
                    String.valueOf(document.get("id")),
                    String.valueOf(document.get("place_name")),
                    String.valueOf(document.getOrDefault("category_name", "")),
                    String.valueOf(document.getOrDefault("address_name", "")),
                    String.valueOf(document.getOrDefault("road_address_name", "")),
                    Double.parseDouble(String.valueOf(document.get("y"))),
                    Double.parseDouble(String.valueOf(document.get("x"))),
                    String.valueOf(document.getOrDefault("phone", "")),
                    String.valueOf(document.getOrDefault("place_url", ""))
            );
        }
    }

    private record Tags(
            boolean history,
            boolean modern,
            boolean budget,
            boolean luxury,
            boolean stable,
            boolean dopamine,
            boolean relax,
            boolean packed
    ) {
        static Tags from(List<PlaceTheme> themes) {
            boolean history = false;
            boolean modern = false;
            boolean budget = false;
            boolean luxury = false;
            boolean stable = false;
            boolean dopamine = false;
            boolean relax = false;
            boolean packed = false;

            for (PlaceTheme theme : themes) {
                switch (theme) {
                    case PALACE_CULTURE -> {
                        history = true;
                        stable = true;
                    }
                    case NATURE_HANGANG -> {
                        relax = true;
                        stable = true;
                    }
                    case DATE -> {
                        modern = true;
                        relax = true;
                    }
                    case FOOD_TOUR -> {
                        budget = true;
                        dopamine = true;
                    }
                    case CAFE_TOUR -> {
                        modern = true;
                        relax = true;
                    }
                    case SHOPPING_HOTPLACE -> {
                        modern = true;
                        dopamine = true;
                    }
                    case NIGHT_VIEW -> {
                        modern = true;
                        dopamine = true;
                    }
                    case HOTEL_STAY -> {
                        relax = true;
                    }
                }
            }

            return new Tags(history, modern, budget, luxury, stable, dopamine, relax, packed);
        }
    }
}
