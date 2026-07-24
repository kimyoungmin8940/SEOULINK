import { BusFront, Car, Footprints, Route, TrainFront } from 'lucide-react';

import { getTravelLegMeta } from '../../utils/courseTransport';

const transportIcons = {
    walking: Footprints,
    'public-transit': TrainFront,
    driving: Car,
    subway: TrainFront,
    bus: BusFront,
    'bus-subway': Route,
};

/** 이동수단 값에 맞는 아이콘을 결과·상세 화면에서 동일하게 표시합니다. */
function CourseTransportIcon({
    transportMode,
    transitPathType,
    size = 16,
    strokeWidth = 2,
    ...props
}) {
    const meta = getTravelLegMeta(transportMode, transitPathType);
    const Icon = transportIcons[meta?.icon] || Route;

    return <Icon size={size} strokeWidth={strokeWidth} {...props} />;
}

export default CourseTransportIcon;
