package com.seoulink.backend.domain.course.model;

/** ODsay가 반환하는 도시 내 대중교통 최적 경로의 대표 수단이다. */
public enum TransitPathType {

    SUBWAY(1),
    BUS(2),
    BUS_SUBWAY(3);

    private final int odsayPathType;

    TransitPathType(int odsayPathType) {
        this.odsayPathType = odsayPathType;
    }

    public int getOdsayPathType() {
        return odsayPathType;
    }

    /** ODsay pathType(1/2/3)을 프론트·DB 공통 enum 값으로 변환한다. */
    public static TransitPathType fromOdsayPathType(Integer pathType) {
        if (pathType == null) {
            throw new IllegalArgumentException("ODsay pathType이 누락되었습니다.");
        }

        return switch (pathType) {
            case 1 -> SUBWAY;
            case 2 -> BUS;
            case 3 -> BUS_SUBWAY;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 ODsay pathType입니다. pathType=" + pathType
            );
        };
    }
}
