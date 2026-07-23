export const TRANSPORT_MODES = Object.freeze({
    WALKING: 'WALKING',
    PUBLIC_TRANSIT: 'PUBLIC_TRANSIT',
    DRIVING: 'DRIVING',
});

export const TRANSIT_PATH_TYPES = Object.freeze({
    WALKING: 'WALKING',
    SUBWAY: 'SUBWAY',
    BUS: 'BUS',
    BUS_SUBWAY: 'BUS_SUBWAY',
});

const transportMeta = Object.freeze({
    [TRANSPORT_MODES.WALKING]: Object.freeze({
        label: '도보',
        icon: 'walking',
        tip: '도보 이동시간을 기준으로 가까운 장소를 연결했어요. 편한 신발과 날씨를 함께 확인해 주세요.',
        scheduleNotice: '위 일정은 도보 이동 거리와 시간을 바탕으로 구성된 추천 동선입니다. 보행 환경과 장소 운영 시간에 따라 실제 일정은 달라질 수 있습니다.',
    }),
    [TRANSPORT_MODES.PUBLIC_TRANSIT]: Object.freeze({
        label: '대중교통',
        icon: 'public-transit',
        tip: '대중교통 경로와 이동시간을 기준으로 코스를 구성했어요. 환승이 부담되면 이동 최소 코스를 확인해 보세요.',
        scheduleNotice: '위 일정은 대중교통 이동 거리와 시간을 바탕으로 구성된 추천 동선입니다. 실시간 운행 상황과 환승 대기, 장소 운영 시간에 따라 실제 일정은 달라질 수 있습니다.',
    }),
    [TRANSPORT_MODES.DRIVING]: Object.freeze({
        label: '자동차',
        icon: 'driving',
        tip: '자동차 이동시간을 기준으로 코스를 구성했어요. 출발 전 교통 상황과 장소별 주차 정보를 확인해 주세요.',
        scheduleNotice: '위 일정은 자동차 이동 거리와 시간을 바탕으로 구성된 추천 동선입니다. 실시간 교통과 주차 상황, 장소 운영 시간에 따라 실제 일정은 달라질 수 있습니다.',
    }),
});

const transitPathMeta = Object.freeze({
    [TRANSIT_PATH_TYPES.WALKING]: Object.freeze({
        label: '도보',
        icon: 'walking',
    }),
    [TRANSIT_PATH_TYPES.SUBWAY]: Object.freeze({
        label: '지하철',
        icon: 'subway',
    }),
    [TRANSIT_PATH_TYPES.BUS]: Object.freeze({
        label: '버스',
        icon: 'bus',
    }),
    [TRANSIT_PATH_TYPES.BUS_SUBWAY]: Object.freeze({
        label: '버스 + 지하철',
        icon: 'bus-subway',
    }),
});

/** 서버와 DB에서 사용하는 세 가지 이동수단 값만 화면 공통 형식으로 인정합니다. */
export function normalizeTransportMode(value) {
    if (typeof value !== 'string') return null;

    const normalized = value.trim().toUpperCase();
    return transportMeta[normalized] ? normalized : null;
}

/** ODsay 경로 종류와 대중교통 검색 실패 시 실제 도보 전환 값을 허용합니다. */
export function normalizeTransitPathType(value) {
    if (typeof value !== 'string') return null;

    const normalized = value.trim().toUpperCase();
    return transitPathMeta[normalized] ? normalized : null;
}

/** 응답, 요청, 미리보기 순서처럼 여러 후보 중 처음 확인되는 이동수단을 반환합니다. */
export function resolveTransportMode(...values) {
    for (const value of values) {
        const normalized = normalizeTransportMode(value);
        if (normalized) return normalized;
    }

    return null;
}

/** 이동수단별 한글명·아이콘 종류·화면 안내 문구를 한곳에서 관리합니다. */
export function getTransportMeta(value) {
    const transportMode = normalizeTransportMode(value);
    return transportMode
        ? { transportMode, ...transportMeta[transportMode] }
        : null;
}

/** 코스 전체 이동수단과 ODsay 구간 종류를 합쳐 장소 사이 실제 표시값을 만듭니다. */
export function getTravelLegMeta(transportModeValue, transitPathTypeValue) {
    const transport = getTransportMeta(transportModeValue);
    const transitPathType = normalizeTransitPathType(transitPathTypeValue);

    if (transport?.transportMode === TRANSPORT_MODES.PUBLIC_TRANSIT && transitPathType) {
        return {
            ...transport,
            transitPathType,
            ...transitPathMeta[transitPathType],
        };
    }

    return transport;
}
