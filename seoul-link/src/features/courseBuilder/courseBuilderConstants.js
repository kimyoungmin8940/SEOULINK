export const DEFAULT_REGION = "서울";
export const TEST_MEMBER_ID = 1;

export const SEOUL_MAP_LEVEL = 8;
export const REGION_MAP_LEVEL = 6;
export const PLACE_FOCUS_MAP_LEVEL = 4;
export const SEARCH_RESULT_MAP_LEVEL = 4;
export const KAKAO_PAGE_SIZE = 15;

export const MAX_COUNT_PER_THEME_ALL = 30;
export const MAX_COUNT_SINGLE_THEME = 120;
export const MAX_COUNT_KEYWORD_SEARCH = 120;

export const AUTO_SEARCH_DELAY_MS = 900;
export const AUTO_ROUTE_CALC_DELAY_MS = 500;
export const MOVE_MAP_WAIT_MS = 500;

export const SEOUL_SEARCH_BOUNDS = {
    south: 37.4133,
    west: 126.7341,
    north: 37.7151,
    east: 127.2693,
};

export const SEOUL_REGION_CENTERS = {
    서울: { latitude: 37.5665, longitude: 126.978 },
    강남구: { latitude: 37.5172, longitude: 127.0473 },
    강동구: { latitude: 37.5301, longitude: 127.1238 },
    강북구: { latitude: 37.6396, longitude: 127.0257 },
    강서구: { latitude: 37.5509, longitude: 126.8495 },
    관악구: { latitude: 37.4784, longitude: 126.9516 },
    광진구: { latitude: 37.5384, longitude: 127.0823 },
    구로구: { latitude: 37.4955, longitude: 126.8877 },
    금천구: { latitude: 37.4569, longitude: 126.8955 },
    노원구: { latitude: 37.6542, longitude: 127.0568 },
    도봉구: { latitude: 37.6688, longitude: 127.0471 },
    동대문구: { latitude: 37.5744, longitude: 127.0396 },
    동작구: { latitude: 37.5124, longitude: 126.9393 },
    마포구: { latitude: 37.5663, longitude: 126.9019 },
    서대문구: { latitude: 37.5791, longitude: 126.9368 },
    서초구: { latitude: 37.4837, longitude: 127.0324 },
    성동구: { latitude: 37.5633, longitude: 127.0371 },
    성북구: { latitude: 37.5894, longitude: 127.0167 },
    송파구: { latitude: 37.5145, longitude: 127.1059 },
    양천구: { latitude: 37.5169, longitude: 126.8664 },
    영등포구: { latitude: 37.5264, longitude: 126.8962 },
    용산구: { latitude: 37.5326, longitude: 126.9905 },
    은평구: { latitude: 37.6027, longitude: 126.9291 },
    종로구: { latitude: 37.5735, longitude: 126.979 },
    중구: { latitude: 37.5636, longitude: 126.9976 },
    중랑구: { latitude: 37.6063, longitude: 127.0927 },
};

export const SEOUL_REGIONS = Object.keys(SEOUL_REGION_CENTERS);
