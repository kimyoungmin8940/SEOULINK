import {
    DEFAULT_REGION,
    MOVE_MAP_WAIT_MS,
    REGION_MAP_LEVEL,
    SEOUL_MAP_LEVEL,
    SEOUL_REGION_CENTERS,
    SEOUL_SEARCH_BOUNDS,
} from "./courseBuilderConstants";
import { getMarkerImageUrl } from "./placeUtils";
import { wait } from "./textUtils";

export const getRegionCenter = (regionValue) => SEOUL_REGION_CENTERS[regionValue] || SEOUL_REGION_CENTERS[DEFAULT_REGION];
export const getMapLevelByRegion = (regionValue) => (regionValue === DEFAULT_REGION ? SEOUL_MAP_LEVEL : REGION_MAP_LEVEL);

export const createMarkerImage = (place) => {
    const kakao = window.kakao;
    return new kakao.maps.MarkerImage(getMarkerImageUrl(place), new kakao.maps.Size(46, 46), {
        offset: new kakao.maps.Point(23, 46),
    });
};

export const moveMapToRegion = async (map, regionValue) => {
    const kakao = window.kakao;
    const centerInfo = getRegionCenter(regionValue);

    map.setLevel(getMapLevelByRegion(regionValue));
    map.setCenter(new kakao.maps.LatLng(centerInfo.latitude, centerInfo.longitude));

    await wait(MOVE_MAP_WAIT_MS);
};

export const moveMapToPlace = async (map, place, mapLevel) => {
    const kakao = window.kakao;
    const latitude = Number(place.latitude);
    const longitude = Number(place.longitude);

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        throw new Error("장소 좌표가 없어 지도로 이동할 수 없습니다.");
    }

    map.setLevel(mapLevel);
    map.setCenter(new kakao.maps.LatLng(latitude, longitude));

    await wait(MOVE_MAP_WAIT_MS);
};


export const fitMapToPlaces = async (map, placeList) => {
    const kakao = window.kakao;
    const validPlaces = (placeList || []).filter((place) => {
        const latitude = Number(place?.latitude);
        const longitude = Number(place?.longitude);
        return Number.isFinite(latitude) && Number.isFinite(longitude);
    });

    if (validPlaces.length === 0) {
        return false;
    }

    const bounds = new kakao.maps.LatLngBounds();

    validPlaces.forEach((place) => {
        bounds.extend(new kakao.maps.LatLng(Number(place.latitude), Number(place.longitude)));
    });

    map.setBounds(bounds);
    await wait(MOVE_MAP_WAIT_MS);
    return true;
};

export const makeMapSearchKey = (map, regionValue, themeValue, foodSubcategoryValue) => {
    const center = map.getCenter();

    return [
        regionValue,
        themeValue,
        foodSubcategoryValue,
        center.getLat().toFixed(4),
        center.getLng().toFixed(4),
        map.getLevel(),
    ].join("-");
};

export const createCoordinateBoundsGrid = ({ south, west, north, east }, gridSize) => {
    const kakao = window.kakao;
    const latStep = (north - south) / gridSize;
    const lngStep = (east - west) / gridSize;
    const boundsList = [];

    for (let row = 0; row < gridSize; row += 1) {
        for (let col = 0; col < gridSize; col += 1) {
            const cellSouthWest = new kakao.maps.LatLng(south + latStep * row, west + lngStep * col);
            const cellNorthEast = new kakao.maps.LatLng(south + latStep * (row + 1), west + lngStep * (col + 1));

            boundsList.push(new kakao.maps.LatLngBounds(cellSouthWest, cellNorthEast));
        }
    }

    return boundsList;
};

export const createBoundsGrid = (map, gridSize) => {
    const bounds = map.getBounds();
    const southWest = bounds.getSouthWest();
    const northEast = bounds.getNorthEast();

    return createCoordinateBoundsGrid(
        {
            south: southWest.getLat(),
            west: southWest.getLng(),
            north: northEast.getLat(),
            east: northEast.getLng(),
        },
        gridSize
    );
};

export const createSeoulSearchBoundsGrid = (gridSize) => createCoordinateBoundsGrid(SEOUL_SEARCH_BOUNDS, gridSize);

export const getGridSearchOption = (maxCount) =>
    maxCount >= 100
        ? { gridSize: 3, pageLimitPerCell: 2 }
        : { gridSize: 2, pageLimitPerCell: 1 };

export const isPlaceInsideMapBounds = (map, place) => {
    if (!place.latitude || !place.longitude) return false;

    const kakao = window.kakao;
    const position = new kakao.maps.LatLng(place.latitude, place.longitude);

    return map.getBounds().contain(position);
};
