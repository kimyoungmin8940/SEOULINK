export const formatDistanceMeter = (distanceMeter) => {
    if (distanceMeter === null || distanceMeter === undefined) return "-";
    if (distanceMeter >= 1000) return `${(distanceMeter / 1000).toFixed(1)}km`;
    return `${distanceMeter}m`;
};

export const formatDurationMinute = (durationMinute) => {
    if (durationMinute === null || durationMinute === undefined) return "-";

    const safeMinute = Math.max(0, Math.floor(Number(durationMinute)));

    if (safeMinute >= 60) {
        const hour = Math.floor(safeMinute / 60);
        const minute = safeMinute % 60;
        return minute === 0 ? `${hour}시간` : `${hour}시간 ${minute}분`;
    }

    return `${safeMinute}분`;
};
