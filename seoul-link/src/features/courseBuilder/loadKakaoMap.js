let kakaoMapPromise = null;

export function loadKakaoMap() {
    if (kakaoMapPromise) {
        return kakaoMapPromise;
    }

    kakaoMapPromise = new Promise((resolve, reject) => {
        if (window.kakao && window.kakao.maps) {
            window.kakao.maps.load(resolve);
            return;
        }

        const appKey = import.meta.env.VITE_KAKAO_MAP_KEY;

        if (!appKey) {
            reject(new Error("VITE_KAKAO_MAP_KEY가 .env에 없습니다."));
            return;
        }

        const script = document.createElement("script");
        script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&libraries=services&autoload=false`;
        script.async = true;

        script.onload = () => {
            window.kakao.maps.load(resolve);
        };

        script.onerror = () => {
            reject(new Error("카카오 지도 스크립트 로딩 실패"));
        };

        document.head.appendChild(script);
    });

    return kakaoMapPromise;
}