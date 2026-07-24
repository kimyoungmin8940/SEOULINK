package com.seoulink.backend.domain.course.routing;

import com.seoulink.backend.domain.course.model.TransitPathType;

/** 이동수단별 계산기가 반환하는 거리·이동시간·대중교통 경로 종류 행렬이다. */
public record RouteCalculation(
        double[][] distancesKm,
        double[][] travelTimesMinutes,
        boolean[][] estimatedPairs,
        TransitPathType[][] transitPathTypes
) {

    public RouteCalculation {
        validateMatrix(
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                transitPathTypes
        );
    }

    /** 대중교통 경로 종류가 없던 기존 계산기용 호환 생성자이다. */
    public RouteCalculation(
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs
    ) {
        this(
                distancesKm,
                travelTimesMinutes,
                estimatedPairs,
                createEmptyTransitPathTypes(distancesKm)
        );
    }

    /** 전체 행렬이 실제값 또는 추정값인 기존 계산기용 호환 생성자이다. */
    public RouteCalculation(
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean estimated
    ) {
        this(
                distancesKm,
                travelTimesMinutes,
                createEstimatedPairs(distancesKm, estimated),
                createEmptyTransitPathTypes(distancesKm)
        );
    }

    public double getDistanceKm(int fromIndex, int toIndex) {
        return distancesKm[fromIndex][toIndex];
    }

    public double getTravelTimeMinutes(int fromIndex, int toIndex) {
        return travelTimesMinutes[fromIndex][toIndex];
    }

    public boolean isEstimated(int fromIndex, int toIndex) {
        return estimatedPairs[fromIndex][toIndex];
    }

    public TransitPathType getTransitPathType(int fromIndex, int toIndex) {
        return transitPathTypes[fromIndex][toIndex];
    }

    /** 하나라도 추정값인 장소 쌍이 있는지 확인하는 기존 호출부용 메서드이다. */
    public boolean estimated() {
        for (boolean[] row : estimatedPairs) {
            for (boolean estimated : row) {
                if (estimated) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validateMatrix(
            double[][] distancesKm,
            double[][] travelTimesMinutes,
            boolean[][] estimatedPairs,
            TransitPathType[][] transitPathTypes
    ) {
        if (distancesKm == null
                || travelTimesMinutes == null
                || estimatedPairs == null
                || transitPathTypes == null
                || distancesKm.length != travelTimesMinutes.length
                || distancesKm.length != estimatedPairs.length
                || distancesKm.length != transitPathTypes.length) {
            throw new IllegalArgumentException("거리·시간 행렬 크기가 올바르지 않습니다.");
        }

        int size = distancesKm.length;
        for (int row = 0; row < size; row++) {
            if (distancesKm[row] == null
                    || travelTimesMinutes[row] == null
                    || estimatedPairs[row] == null
                    || transitPathTypes[row] == null
                    || distancesKm[row].length != size
                    || travelTimesMinutes[row].length != size
                    || estimatedPairs[row].length != size
                    || transitPathTypes[row].length != size) {
                throw new IllegalArgumentException("거리·시간 행렬은 정사각형이어야 합니다.");
            }
        }
    }

    private static boolean[][] createEstimatedPairs(
            double[][] distancesKm,
            boolean estimated
    ) {
        if (distancesKm == null) {
            return null;
        }
        int size = distancesKm.length;
        boolean[][] result = new boolean[size][size];
        if (!estimated) {
            return result;
        }

        for (int fromIndex = 0; fromIndex < size; fromIndex++) {
            for (int toIndex = 0; toIndex < size; toIndex++) {
                result[fromIndex][toIndex] = fromIndex != toIndex;
            }
        }
        return result;
    }

    private static TransitPathType[][] createEmptyTransitPathTypes(
            double[][] distancesKm
    ) {
        return distancesKm == null
                ? null
                : new TransitPathType[distancesKm.length][distancesKm.length];
    }
}
