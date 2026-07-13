package com.seoulink.backend.coursebuilder;

import com.seoulink.backend.coursebuilder.dto.CourseBuilderPlaceResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import java.util.List;

@Service
public class PlaceColumnSearchService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlaceColumnSearchService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CourseBuilderPlaceResponse> findPlaces(
            String theme,
            String region,
            Integer limit,
            String category,
            String indoorYn,
            String rainOkYn,
            String nightOkYn,
            String priceLevel
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("limit", normalizeLimit(limit));

        StringBuilder sql = new StringBuilder("""
                SELECT
                    PLACE_ID,
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
                    INDOOR_YN,
                    RAIN_OK_YN,
                    NIGHT_OK_YN,
                    AVG_STAY_MINUTES,
                    PRICE_LEVEL
                FROM PLACES
                WHERE IS_ACTIVE = 'Y'
                  AND RECOMMEND_YN = 'Y'
                  AND APPROVAL_STATUS = 'APPROVED'
                """);

        appendRegionCondition(sql, params, region);
        appendOptionalEquals(sql, params, "CATEGORY", "category", category);
        appendOptionalEquals(sql, params, "INDOOR_YN", "indoorYn", indoorYn);
        appendOptionalEquals(sql, params, "RAIN_OK_YN", "rainOkYn", rainOkYn);
        appendOptionalEquals(sql, params, "NIGHT_OK_YN", "nightOkYn", nightOkYn);
        appendOptionalEquals(sql, params, "PRICE_LEVEL", "priceLevel", priceLevel);
        appendThemeCondition(sql, params, theme);

        sql.append("""
                ORDER BY
                    REVIEW_COUNT DESC,
                    RATING DESC,
                    PLACE_ID DESC
                FETCH FIRST :limit ROWS ONLY
                """);

        return jdbcTemplate.query(sql.toString(), params, this::mapPlace);
    }

    private void appendRegionCondition(StringBuilder sql, MapSqlParameterSource params, String region) {
        if (region == null || region.isBlank() || "서울".equals(region)) {
            return;
        }

        sql.append(" AND REGION = :region\n");
        params.addValue("region", region);
    }

    private void appendOptionalEquals(
            StringBuilder sql,
            MapSqlParameterSource params,
            String columnName,
            String paramName,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return;
        }

        sql.append(" AND ").append(columnName).append(" = :").append(paramName).append("\n");
        params.addValue(paramName, value.toUpperCase(Locale.ROOT));
    }

    private void appendThemeCondition(StringBuilder sql, MapSqlParameterSource params, String theme) {
        String normalizedTheme = theme == null ? "ALL" : theme.toUpperCase(Locale.ROOT);

        switch (normalizedTheme) {
            case "PALACE_CULTURE" -> sql.append(" AND CATEGORY = 'TOUR' AND TAG_HISTORY = 'Y'\n");
            case "NATURE_HANGANG" -> sql.append(" AND CATEGORY = 'TOUR' AND TAG_RELAX = 'Y' AND TAG_STABLE = 'Y'\n");
            case "DATE" -> sql.append(" AND TAG_MODERN = 'Y' AND TAG_RELAX = 'Y'\n");
            case "FOOD_TOUR" -> sql.append(" AND CATEGORY = 'RESTAURANT'\n");
            case "CAFE_TOUR" -> sql.append(" AND CATEGORY = 'CAFE'\n");
            case "SHOPPING_HOTPLACE" -> sql.append(" AND TAG_MODERN = 'Y' AND TAG_DOPAMINE = 'Y'\n");
            case "NIGHT_VIEW" -> sql.append(" AND NIGHT_OK_YN = 'Y'\n");
            case "HOTEL_STAY" -> sql.append(" AND CATEGORY = 'HOTEL'\n");
            case "INDOOR" -> sql.append(" AND INDOOR_YN = 'Y'\n");
            case "RAINY_DAY" -> sql.append(" AND RAIN_OK_YN = 'Y'\n");
            case "BUDGET" -> sql.append(" AND (TAG_BUDGET = 'Y' OR PRICE_LEVEL = 'LOW')\n");
            case "LUXURY" -> sql.append(" AND (TAG_LUXURY = 'Y' OR PRICE_LEVEL = 'HIGH')\n");
            case "ALL" -> {
            }
            default -> {
            }
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 30;
        }

        return Math.min(limit, 120);
    }

    private CourseBuilderPlaceResponse mapPlace(ResultSet rs, int rowNum) throws SQLException {
        return new CourseBuilderPlaceResponse(
                rs.getLong("PLACE_ID"),
                rs.getString("API_PROVIDER"),
                rs.getString("API_PLACE_ID"),
                rs.getString("NAME"),
                rs.getString("CATEGORY"),
                rs.getString("API_CATEGORY"),
                rs.getString("REGION"),
                rs.getString("ADDRESS"),
                rs.getString("ROAD_ADDRESS"),
                rs.getDouble("LATITUDE"),
                rs.getDouble("LONGITUDE"),
                rs.getString("PHONE"),
                rs.getString("PLACE_URL"),
                rs.getDouble("RATING"),
                rs.getInt("REVIEW_COUNT"),
                rs.getString("DESCRIPTION"),
                rs.getString("IMAGE_URL"),
                rs.getString("TAG_HISTORY"),
                rs.getString("TAG_MODERN"),
                rs.getString("TAG_BUDGET"),
                rs.getString("TAG_LUXURY"),
                rs.getString("TAG_STABLE"),
                rs.getString("TAG_DOPAMINE"),
                rs.getString("TAG_RELAX"),
                rs.getString("TAG_PACKED"),
                rs.getString("SOURCE_TYPE"),
                rs.getString("RECOMMEND_YN"),
                rs.getString("APPROVAL_STATUS"),
                rs.getString("INDOOR_YN"),
                rs.getString("RAIN_OK_YN"),
                rs.getString("NIGHT_OK_YN"),
                getNullableInteger(rs, "AVG_STAY_MINUTES"),
                rs.getString("PRICE_LEVEL")
        );
    }

    private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }
}
