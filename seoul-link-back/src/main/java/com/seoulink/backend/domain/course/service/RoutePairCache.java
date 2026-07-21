package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 동일 장소 쌍의 거리와 이동시간을 메모리에 보관하는 LRU 캐시이다.
 *
 * <p>도보 경로는 방향에 따라 달라질 수 있으므로 A→B와 B→A를 서로 다른 키로 저장한다.
 * 장소 ID가 같아도 좌표가 변경되면 별도 키가 만들어져 오래된 경로가 재사용되지 않는다.</p>
 */
@Component
public class RoutePairCache {

    private static final int DEFAULT_MAX_ENTRIES = 20_000;
    private static final long DEFAULT_TTL_MINUTES = 1_440L;

    private final int maxEntries;
    private final long ttlMillis;
    private final Map<RoutePairKey, CachedRoutePair> cache;

    public RoutePairCache(
            @Value("${course.distance-cache.max-entries:20000}") int maxEntries,
            @Value("${course.distance-cache.ttl-minutes:1440}") long ttlMinutes
    ) {
        this(maxEntries, Duration.ofMinutes(ttlMinutes));
    }

    /** 외부 설정 없이 사용하는 단위 테스트용 기본 캐시이다. */
    RoutePairCache() {
        this(DEFAULT_MAX_ENTRIES, Duration.ofMinutes(DEFAULT_TTL_MINUTES));
    }

    /** 캐시 크기와 TTL을 직접 지정하는 단위 테스트용 생성자이다. */
    RoutePairCache(int maxEntries, Duration ttl) {
        if (ttl == null) {
            throw new IllegalArgumentException("거리 캐시 TTL은 null일 수 없습니다.");
        }
        long ttlMillis = ttl.toMillis();
        if (maxEntries < 1) {
            throw new IllegalArgumentException("거리 캐시 최대 크기는 1 이상이어야 합니다.");
        }
        if (ttlMillis < 1) {
            throw new IllegalArgumentException("거리 캐시 TTL은 1ms 이상이어야 합니다.");
        }

        this.maxEntries = maxEntries;
        this.ttlMillis = ttlMillis;
        this.cache = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<RoutePairKey, CachedRoutePair> eldest
            ) {
                return size() > RoutePairCache.this.maxEntries;
            }
        };
    }

    /** 유효기간이 남은 장소 쌍 경로가 있으면 반환하고 만료된 값은 즉시 제거한다. */
    public synchronized Optional<RoutePairValue> get(
            PlaceCandidateDto from,
            PlaceCandidateDto to
    ) {
        RoutePairKey key = createKey(from, to);
        CachedRoutePair cached = cache.get(key);
        if (cached == null) {
            return Optional.empty();
        }

        if (cached.expiresAtMillis() <= System.currentTimeMillis()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(cached.value());
    }

    /** 외부 길찾기 또는 Haversine으로 계산한 거리·시간을 동일한 캐시에 저장한다. */
    public synchronized void put(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            double distanceKm,
            double travelTimeMinutes
    ) {
        validateRouteValue(distanceKm, "거리");
        validateRouteValue(travelTimeMinutes, "이동시간");

        cache.put(
                createKey(from, to),
                new CachedRoutePair(
                        new RoutePairValue(distanceKm, travelTimeMinutes),
                        System.currentTimeMillis() + ttlMillis
                )
        );
    }

    /** 테스트와 운영 점검에서 현재 보관 중인 장소 쌍 개수를 확인한다. */
    public synchronized int size() {
        removeExpiredEntries();
        return cache.size();
    }

    /** 장소 좌표 데이터가 대량 변경된 경우 캐시를 비울 수 있도록 제공한다. */
    public synchronized void clear() {
        cache.clear();
    }

    private RoutePairKey createKey(PlaceCandidateDto from, PlaceCandidateDto to) {
        return new RoutePairKey(createPlacePoint(from), createPlacePoint(to));
    }

    private PlacePoint createPlacePoint(PlaceCandidateDto place) {
        if (place == null || place.getLatitude() == null || place.getLongitude() == null) {
            throw new IllegalArgumentException("거리 캐시 키 생성에는 장소 좌표가 필요합니다.");
        }
        return new PlacePoint(
                place.getPlaceId(),
                Double.doubleToLongBits(place.getLatitude()),
                Double.doubleToLongBits(place.getLongitude())
        );
    }

    private void removeExpiredEntries() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private void validateRouteValue(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    "캐시에 저장할 " + fieldName + "은 0 이상의 유한한 숫자여야 합니다."
            );
        }
    }

    /** 캐시에서 조회되는 거리(km)와 이동시간(분) 한 쌍이다. */
    public record RoutePairValue(
            double distanceKm,
            double travelTimeMinutes
    ) {
    }

    private record PlacePoint(
            Long placeId,
            long latitudeBits,
            long longitudeBits
    ) {
    }

    private record RoutePairKey(
            PlacePoint from,
            PlacePoint to
    ) {
    }

    private record CachedRoutePair(
            RoutePairValue value,
            long expiresAtMillis
    ) {
    }
}
