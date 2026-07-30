package com.seoulink.backend.domain.course.exception;

/**
 * 대중교통 40분 상한을 지키려면 최소 일반 장소 수를 깨야 하는 경우 발생한다.
 * 프런트는 이 오류를 받으면 중복 제한을 한 단계씩만 완화해 같은 DAY를 다시 조회한다.
 */
public class PublicTransitMinimumPlaceException extends IllegalStateException {

    private final int minimumOrdinaryPlaces;

    public PublicTransitMinimumPlaceException(int minimumOrdinaryPlaces) {
        super(
                "대중교통 40분 제한을 지키면서 최소 장소 수를 유지할 수 없습니다. "
                        + "minimumOrdinaryPlaces="
                        + minimumOrdinaryPlaces
        );
        this.minimumOrdinaryPlaces = minimumOrdinaryPlaces;
    }

    public int getMinimumOrdinaryPlaces() {
        return minimumOrdinaryPlaces;
    }
}
