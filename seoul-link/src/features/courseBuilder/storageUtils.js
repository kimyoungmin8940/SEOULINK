import { SELECTED_MAP_PLACES_STORAGE_KEY } from "./courseBuilderConstants";

export const loadStoredSelectedMapPlaces = () => {
    try {
        const savedValue = window.localStorage.getItem(SELECTED_MAP_PLACES_STORAGE_KEY);
        const parsedValue = savedValue ? JSON.parse(savedValue) : [];
        return Array.isArray(parsedValue) ? parsedValue : [];
    } catch (error) {
        console.warn("저장된 지도 선택 장소를 불러오지 못했습니다.", error);
        return [];
    }
};

export const saveSelectedMapPlaces = (placeList) => {
    try {
        window.localStorage.setItem(SELECTED_MAP_PLACES_STORAGE_KEY, JSON.stringify(placeList || []));
    } catch (error) {
        console.warn("지도 선택 장소를 저장하지 못했습니다.", error);
    }
};
