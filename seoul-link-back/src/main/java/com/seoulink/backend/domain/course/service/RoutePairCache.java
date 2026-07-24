package com.seoulink.backend.domain.course.service;

import com.seoulink.backend.domain.course.dto.request.PlaceCandidateDto;
import com.seoulink.backend.domain.course.model.TransportMode;
import com.seoulink.backend.domain.course.model.TransitPathType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 동일 장소 쌍의 거리와 이동시간을 메모리에 보관하는 LRU 캐시이다.
 *
 * <p>경로는 방향과 이동수단에 따라 달라질 수 있으므로 A→B와 B→A, 도보·대중교통·
 * 자동차를 모두 별도 키로 저장한다. 장소 ID가 같아도 좌표가 변경되면 별도 키가
 * 만들어져 오래된 경로가 재사용되지 않는다.</p>
 */
@Component
public class RoutePairCache {

    private static final int DEFAULT_MAX_ENTRIES = 20_000;
    private static final long DEFAULT_TTL_MINUTES = 1_440L;

    private final int maxEntries;
    private final long ttlMillis;
    private final Map<RoutePairKey, CachedRoutePair> cache;
    private final Map<RoutePairKey, CompletableFuture<RoutePairValue>> inFlight;

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
        this.inFlight = new ConcurrentHashMap<>();
    }

    /** 유효기간이 남은 장소 쌍 경로가 있으면 반환하고 만료된 값은 즉시 제거한다. */
    public synchronized Optional<RoutePairValue> get(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            TransportMode transportMode
    ) {
        RoutePairKey key = createKey(from, to, transportMode);
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
            TransportMode transportMode,
            double distanceKm,
            double travelTimeMinutes,
            boolean estimated
    ) {
        put(
                from,
                to,
                transportMode,
                distanceKm,
                travelTimeMinutes,
                estimated,
                null
        );
    }

    /**
     * 동일 장소 쌍이 동시에 요청되면 첫 요청만 외부 API를 호출하고 결과를 공유한다.
     *
     * <p>실제 경로는 기존 TTL 캐시에 저장하고, 추정값은 동시에 기다리던 요청에만
     * 전달한 뒤 폐기해 외부 API 복구 후 다시 조회할 수 있게 한다.</p>
     */
    public RoutePairValue getOrLoad(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            TransportMode transportMode,
            Supplier<RoutePairValue> loader
    ) {
        if (loader == null) {
            throw new IllegalArgumentException("경로 로더는 필수입니다.");
        }

        Optional<RoutePairValue> cached = get(from, to, transportMode);
        if (cached.isPresent()) {
            return cached.get();
        }

        RoutePairKey key = createKey(from, to, transportMode);
        CompletableFuture<RoutePairValue> created =
                new CompletableFuture<>();
        CompletableFuture<RoutePairValue> existing =
                inFlight.putIfAbsent(key, created);
        if (existing != null) {
            return await(existing);
        }

        try {
            // inFlight 등록 직전에 다른 요청이 캐시를 채웠을 수 있으므로 한 번 더 확인한다.
            Optional<RoutePairValue> cachedAfterRegistration =
                    get(from, to, transportMode);
            RoutePairValue value = cachedAfterRegistration.orElseGet(loader);
            if (value == null) {
                throw new IllegalStateException(
                        "외부 경로 조회 결과가 비어 있습니다."
                );
            }
            put(
                    from,
                    to,
                    transportMode,
                    value.distanceKm(),
                    value.travelTimeMinutes(),
                    value.estimated(),
                    value.transitPathType()
            );
            created.complete(value);
            return value;
        } catch (RuntimeException | Error exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, created);
        }
    }

    /** 대중교통 실제 경로는 ODsay의 지하철·버스·혼합 종류까지 함께 보관한다. */
    public synchronized void put(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            TransportMode transportMode,
            double distanceKm,
            double travelTimeMinutes,
            boolean estimated,
            TransitPathType transitPathType
    ) {
        validateRouteValue(distanceKm, "거리");
        validateRouteValue(travelTimeMinutes, "이동시간");

        // 추정값은 외부 API가 복구된 뒤 다시 실제 경로를 조회할 수 있어야 하므로 저장하지 않는다.
        if (estimated) {
            return;
        }

        cache.put(
                createKey(from, to, transportMode),
                new CachedRoutePair(
                        new RoutePairValue(
                                distanceKm,
                                travelTimeMinutes,
                                estimated,
                                transitPathType
                        ),
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

    private RoutePairKey createKey(
            PlaceCandidateDto from,
            PlaceCandidateDto to,
            TransportMode transportMode
    ) {
        if (transportMode == null) {
            throw new IllegalArgumentException("거리 캐시 키 생성에는 이동수단이 필요합니다.");
        }
        return new RoutePairKey(
                transportMode,
                createPlacePoint(from),
                createPlacePoint(to)
        );
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

    private RoutePairValue await(
            CompletableFuture<RoutePairValue> future
    ) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "공유 경로 조회에 실패했습니다.",
                    cause
            );
        }
    }

    /** 캐시에서 조회되는 거리(km)와 이동시간(분) 한 쌍이다. */
    public record RoutePairValue(
            double distanceKm,
            double travelTimeMinutes,
            boolean estimated,
            TransitPathType transitPathType
    ) {
    }

    private record PlacePoint(
            Long placeId,
            long latitudeBits,
            long longitudeBits
    ) {
    }

    private record RoutePairKey(
            TransportMode transportMode,
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
