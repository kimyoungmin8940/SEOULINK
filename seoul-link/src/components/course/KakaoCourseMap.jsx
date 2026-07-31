import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { getMappableCoursePlaces } from '../../utils/courseMap';

const KAKAO_MAP_SCRIPT_ID = 'kakao-map-sdk';
const DEFAULT_CENTER = { latitude: 37.5665, longitude: 126.978 };
const ZOOM_CONTROL_HIDE_DELAY_MS = 3000;

let kakaoMapsLoaderPromise = null;

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
    const zoomControlTimerRef = useRef(null);
    const [loadResult, setLoadResult] = useState({ key: null, status: 'loading' });
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
    const requestKey = `${placesSignature}:${showZoomControl}`;

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
        let resizeObserver = null;
        let mapsApi = null;
        let map = null;
        let mapClickHandler = null;
        const overlays = [];
        const polylines = [];

        if (!container) return undefined;

        if (mappablePlaces.length === 0) {
            container.replaceChildren();
            return undefined;
        }

        if (!appKey) {
            container.replaceChildren();
            return undefined;
        }

        loadKakaoMaps(appKey)
            .then((maps) => {
                if (cancelled || !containerRef.current) return;

                mapsApi = maps;
                const positions = mappablePlaces.map((place) => (
                    new maps.LatLng(place.latitude, place.longitude)
                ));
                const center = positions[0]
                    || new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude);
                map = new maps.Map(container, {
                    center,
                    level: 5,
                    scrollwheel: false,
                });
                mapRef.current = map;

                if (showZoomControl) {
                    mapClickHandler = showZoomControlTemporarily;
                    maps.event.addListener(map, 'click', mapClickHandler);
                }

                positions.forEach((position, index) => {
                    const overlay = new maps.CustomOverlay({
                        map,
                        position,
                        content: createNumberMarker(mappablePlaces[index], index),
                        xAnchor: 0.5,
                        yAnchor: 0.5,
                        zIndex: 4,
                    });
                    overlays.push(overlay);
                });

                if (positions.length > 1) {
                    polylines.push(new maps.Polyline({
                        map,
                        path: positions,
                        strokeWeight: 8,
                        strokeColor: '#ffffff',
                        strokeOpacity: 0.92,
                        strokeStyle: 'solid',
                        zIndex: 1,
                    }));
                    polylines.push(new maps.Polyline({
                        map,
                        path: positions,
                        strokeWeight: 4,
                        strokeColor: '#1677da',
                        strokeOpacity: 0.95,
                        strokeStyle: 'shortdash',
                        zIndex: 2,
                    }));

                    const bounds = new maps.LatLngBounds();
                    positions.forEach((position) => bounds.extend(position));
                    map.setBounds(bounds, 48, 48, 48, 48);
                } else {
                    map.setCenter(positions[0]);
                    map.setLevel(4);
                }

                const relayoutMap = () => {
                    map.relayout();
                    if (positions.length === 1) map.setCenter(positions[0]);
                };

                if (typeof ResizeObserver !== 'undefined') {
                    resizeObserver = new ResizeObserver(relayoutMap);
                    resizeObserver.observe(container);
                }

                window.requestAnimationFrame(relayoutMap);
                setLoadResult({ key: requestKey, status: 'ready' });
            })
            .catch(() => {
                if (!cancelled) setLoadResult({ key: requestKey, status: 'error' });
            });

        return () => {
            cancelled = true;
            resizeObserver?.disconnect();
            if (mapsApi && map && mapClickHandler) {
                mapsApi.event.removeListener(map, 'click', mapClickHandler);
            }
            if (mapRef.current === map) mapRef.current = null;
            overlays.forEach((overlay) => overlay.setMap(null));
            polylines.forEach((polyline) => polyline.setMap(null));
            container.replaceChildren();
        };
    // placesSignature는 좌표·이름이 실제로 바뀐 경우에만 지도를 다시 생성하게 합니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [placesSignature, showZoomControl, showZoomControlTemporarily]);

    const status = mappablePlaces.length === 0
        ? 'empty'
        : !appKey
            ? 'missing-key'
            : loadResult.key === requestKey
                ? loadResult.status
                : 'loading';
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
