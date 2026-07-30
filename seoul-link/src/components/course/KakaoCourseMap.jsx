import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getMappableCoursePlaces } from '../../utils/courseMap';

const KAKAO_MAP_SCRIPT_ID = 'kakao-map-sdk';
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 };
const ZOOM_CONTROL_HIDE_DELAY_MS = 3000;
const MAP_UPDATE_IDLE_TIMEOUT_MS = 350;

let kakaoMapsLoaderPromise = null;

/** 마커·선 교체는 긴급한 카드 렌더가 끝난 뒤 브라우저 유휴 시간에 처리합니다. */
function scheduleMapUpdate(callback) {
    if (typeof window.requestIdleCallback === 'function') {
        const idleCallbackId = window.requestIdleCallback(callback, {
            timeout: MAP_UPDATE_IDLE_TIMEOUT_MS,
        });

        return () => {
            if (typeof window.cancelIdleCallback === 'function') {
                window.cancelIdleCallback(idleCallbackId);
            }
        };
    }

    const timeoutId = window.setTimeout(callback, 0);
    return () => window.clearTimeout(timeoutId);
}

function loadKakaoMaps(appKey) {
    if (typeof window === 'undefined') {
        return Promise.reject(new Error('브라우저에서만 지도를 불러올 수 있습니다.'));
    }

    if (window.kakao?.maps?.Map) {
        return Promise.resolve(window.kakao.maps);
    }

    if (!appKey) {
        return Promise.reject(new Error('VITE_KAKAO_MAP_KEY가 설정되지 않았습니다.'));
    }

    if (kakaoMapsLoaderPromise) return kakaoMapsLoaderPromise;

    kakaoMapsLoaderPromise = new Promise((resolve, reject) => {
        const finishLoading = () => {
            if (!window.kakao?.maps?.load) {
                document.getElementById(KAKAO_MAP_SCRIPT_ID)?.remove();
                reject(new Error('카카오 지도 SDK를 초기화하지 못했습니다.'));
                return;
            }

            window.kakao.maps.load(() => resolve(window.kakao.maps));
        };

        const failLoading = () => {
            document.getElementById(KAKAO_MAP_SCRIPT_ID)?.remove();
            reject(new Error('카카오 지도 SDK를 불러오지 못했습니다.'));
        };

        const existingScript = document.getElementById(KAKAO_MAP_SCRIPT_ID);

        if (existingScript) {
            existingScript.addEventListener('load', finishLoading, { once: true });
            existingScript.addEventListener('error', failLoading, { once: true });

            if (window.kakao?.maps?.load) finishLoading();
            return;
        }

        const script = document.createElement('script');
        script.id = KAKAO_MAP_SCRIPT_ID;
        script.async = true;
        script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${encodeURIComponent(appKey)}&autoload=false`;
        script.addEventListener('load', finishLoading, { once: true });
        script.addEventListener('error', failLoading, { once: true });
        document.head.appendChild(script);
    }).catch((error) => {
        kakaoMapsLoaderPromise = null;
        throw error;
    });

    return kakaoMapsLoaderPromise;
}

function isHotelCategory(category) {
    const normalized = String(category || '').trim().toUpperCase();
    return ['HOTEL', '숙소', '호텔', 'ACCOMMODATION', 'LODGING'].includes(normalized);
}

function getMarkerCategoryTone(place) {
    if (place?.routeOrigin || isHotelCategory(place?.category)) {
        return 'hotel';
    }

    const normalized = String(place?.category || '').trim().toUpperCase();

    if (['RESTAURANT', '식당', '맛집', 'FOOD'].includes(normalized)) {
        return 'restaurant';
    }

    if (['CAFE', '카페', 'COFFEE'].includes(normalized)) {
        return 'cafe';
    }

    return 'tour';
}

function createNumberMarker(place, index) {
    const marker = document.createElement('div');
    marker.className = `kakao-course-map-marker category-${getMarkerCategoryTone(place)}`;

    const isRouteOrigin = Boolean(place?.routeOrigin);
    const isHotel = isHotelCategory(place?.category);
    const visitOrder = Number(place?.visitOrder);
    const markerLabel = isRouteOrigin || isHotel
        ? 'H'
        : String(Number.isFinite(visitOrder) && visitOrder > 0
            ? visitOrder
            : index + 1);
    const markerTitle = isRouteOrigin
        ? `숙소 출발: ${place.placeName}`
        : isHotel
            ? `숙소 도착: ${place.placeName}`
            : `${markerLabel}. ${place.placeName}`;

    marker.textContent = markerLabel;
    marker.dataset.placeName = place.placeName;
    marker.title = markerTitle;
    marker.setAttribute('aria-label', markerTitle);

    return marker;
}

function getStatusMessage(status) {
    if (status === 'empty') {
        return {
            title: '장소 좌표를 기다리고 있어요',
            description: '실제 코스 데이터에 위도와 경도가 연결되면 지도가 자동으로 표시됩니다.',
        };
    }

    if (status === 'missing-key') {
        return {
            title: '카카오 지도 키가 필요해요',
            description: '.env.local의 VITE_KAKAO_MAP_KEY를 확인해주세요.',
        };
    }

    if (status === 'error') {
        return {
            title: '지도를 불러오지 못했어요',
            description: '카카오 JavaScript 키와 Web 플랫폼 도메인 설정을 확인해주세요.',
        };
    }

    return {
        title: '지도를 불러오는 중이에요',
        description: '잠시만 기다려주세요.',
    };
}

/**
 * 방문 순서가 적용된 장소 배열을 실제 카카오 지도에 표시합니다.
 * 현재는 지도 SDK의 Polyline으로 좌표 사이를 직선 연결하며,
 * 도보 도로선을 표시하려면 추후 Directions 경로 좌표를 전달해야 합니다.
 */
function KakaoCourseMap({
    places,
    ariaLabel = '장소 방문 순서가 표시된 코스 지도',
    showZoomControl = true,
}) {
    const containerRef = useRef(null);
    const mapRef = useRef(null);
    const mapsApiRef = useRef(null);
    const overlaysRef = useRef([]);
    const polylinesRef = useRef([]);
    const resizeObserverRef = useRef(null);
    const mapClickHandlerRef = useRef(null);
    const zoomControlTimerRef = useRef(null);
    const [mapStatus, setMapStatus] = useState('loading');
    const [isZoomControlVisible, setIsZoomControlVisible] = useState(false);
    const mappablePlaces = useMemo(() => getMappableCoursePlaces(places), [places]);
    const placesSignature = useMemo(() => mappablePlaces
        .map((place, index) => [
            place.placeId ?? index,
            place.latitude,
            place.longitude,
            place.placeName,
            place.category,
            place.visitOrder,
            Boolean(place.routeOrigin),
        ].join(':'))
        .join('|'), [mappablePlaces]);
    const appKey = import.meta.env.VITE_KAKAO_MAP_KEY?.trim();

    const showZoomControlTemporarily = useCallback(() => {
        setIsZoomControlVisible(true);

        if (zoomControlTimerRef.current !== null) {
            window.clearTimeout(zoomControlTimerRef.current);
        }

        zoomControlTimerRef.current = window.setTimeout(() => {
            setIsZoomControlVisible(false);
            zoomControlTimerRef.current = null;
        }, ZOOM_CONTROL_HIDE_DELAY_MS);
    }, []);

    useEffect(() => () => {
        if (zoomControlTimerRef.current !== null) {
            window.clearTimeout(zoomControlTimerRef.current);
        }
    }, []);

    useEffect(() => {
        const container = containerRef.current;
        let cancelled = false;

        if (!container) return undefined;

        if (!appKey) {
            container.replaceChildren();
            return undefined;
        }

        setMapStatus('loading');
        loadKakaoMaps(appKey)
            .then((maps) => {
                if (cancelled || !containerRef.current) return;

                const map = new maps.Map(container, {
                    center: new maps.LatLng(
                        DEFAULT_CENTER.latitude,
                        DEFAULT_CENTER.longitude,
                    ),
                    level: 5,
                    scrollwheel: false,
                });
                mapsApiRef.current = maps;
                mapRef.current = map;

                if (showZoomControl) {
                    mapClickHandlerRef.current = showZoomControlTemporarily;
                    maps.event.addListener(
                        map,
                        'click',
                        mapClickHandlerRef.current,
                    );
                }

                const relayoutMap = () => {
                    map.relayout();
                };

                if (typeof ResizeObserver !== 'undefined') {
                    resizeObserverRef.current = new ResizeObserver(relayoutMap);
                    resizeObserverRef.current.observe(container);
                }

                window.requestAnimationFrame(relayoutMap);
                setMapStatus('ready');
            })
            .catch(() => {
                if (!cancelled) setMapStatus('error');
            });

        return () => {
            cancelled = true;
            resizeObserverRef.current?.disconnect();
            resizeObserverRef.current = null;
            if (
                mapsApiRef.current
                && mapRef.current
                && mapClickHandlerRef.current
            ) {
                mapsApiRef.current.event.removeListener(
                    mapRef.current,
                    'click',
                    mapClickHandlerRef.current,
                );
            }
            mapClickHandlerRef.current = null;
            overlaysRef.current.forEach((overlay) => overlay.setMap(null));
            polylinesRef.current.forEach((polyline) => polyline.setMap(null));
            overlaysRef.current = [];
            polylinesRef.current = [];
            mapRef.current = null;
            mapsApiRef.current = null;
            container.replaceChildren();
        };
    }, [appKey, showZoomControl, showZoomControlTemporarily]);

    useEffect(() => {
        const maps = mapsApiRef.current;
        const map = mapRef.current;

        if (!maps || !map || mapStatus !== 'ready') {
            return undefined;
        }

        let frameId = null;
        const cancelScheduledUpdate = scheduleMapUpdate(() => {
            if (
                mapsApiRef.current !== maps
                || mapRef.current !== map
            ) {
                return;
            }

            overlaysRef.current.forEach((overlay) => overlay.setMap(null));
            polylinesRef.current.forEach((polyline) => polyline.setMap(null));
            overlaysRef.current = [];
            polylinesRef.current = [];

            if (mappablePlaces.length === 0) {
                return;
            }

            const positions = mappablePlaces.map((place) => (
                new maps.LatLng(place.latitude, place.longitude)
            ));

            overlaysRef.current = positions.map((position, index) => (
                new maps.CustomOverlay({
                    map,
                    position,
                    content: createNumberMarker(mappablePlaces[index], index),
                    xAnchor: 0.5,
                    yAnchor: 0.5,
                    zIndex: 4,
                })
            ));

            if (positions.length > 1) {
                polylinesRef.current = [
                    new maps.Polyline({
                        map,
                        path: positions,
                        strokeWeight: 8,
                        strokeColor: '#ffffff',
                        strokeOpacity: 0.92,
                        strokeStyle: 'solid',
                        zIndex: 1,
                    }),
                    new maps.Polyline({
                        map,
                        path: positions,
                        strokeWeight: 4,
                        strokeColor: '#1677da',
                        strokeOpacity: 0.95,
                        strokeStyle: 'shortdash',
                        zIndex: 2,
                    }),
                ];
            }

            frameId = window.requestAnimationFrame(() => {
                map.relayout();

                if (positions.length > 1) {
                    const bounds = new maps.LatLngBounds();
                    positions.forEach((position) => bounds.extend(position));
                    map.setBounds(bounds, 48, 48, 48, 48);
                } else {
                    map.setCenter(positions[0]);
                    map.setLevel(4);
                }
            });
        });

        return () => {
            cancelScheduledUpdate();
            if (frameId !== null) {
                window.cancelAnimationFrame(frameId);
            }
        };
    // placesSignature가 바뀔 때 지도 인스턴스는 유지하고 마커와 선만 교체합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mapStatus, placesSignature]);

    const status = mappablePlaces.length === 0
        ? 'empty'
        : !appKey
            ? 'missing-key'
            : mapStatus;
    const statusMessage = getStatusMessage(status);

    const changeZoomLevel = (levelChange) => {
        const map = mapRef.current;
        if (!map) return;

        map.setLevel(map.getLevel() + levelChange, { animate: true });
        showZoomControlTemporarily();
    };

    return (
        <div className="kakao-course-map" aria-label={ariaLabel}>
            <div className="kakao-course-map-surface" ref={containerRef} />

            {showZoomControl && isZoomControlVisible && status === 'ready' && (
                <div
                    className="kakao-course-map-zoom-control"
                    aria-label="지도 확대 및 축소"
                    onPointerDown={(event) => event.stopPropagation()}
                >
                    <button
                        type="button"
                        aria-label="지도 확대"
                        onClick={() => changeZoomLevel(-1)}
                    >
                        <span aria-hidden="true">+</span>
                    </button>
                    <button
                        type="button"
                        aria-label="지도 축소"
                        onClick={() => changeZoomLevel(1)}
                    >
                        <span aria-hidden="true">−</span>
                    </button>
                </div>
            )}

            {status !== 'ready' && (
                <div className={`kakao-course-map-state ${status}`} role="status">
                    <span className="kakao-course-map-state-icon" aria-hidden="true" />
                    <strong>{statusMessage.title}</strong>
                    <p>{statusMessage.description}</p>
                </div>
            )}

            <ol className="kakao-course-map-accessible-list">
                {mappablePlaces.map((place, index) => (
                    <li key={`${place.placeId ?? place.placeName}-${index}`}>
                        {index + 1}. {place.placeName}
                    </li>
                ))}
            </ol>
        </div>
    );
}

export default KakaoCourseMap;
