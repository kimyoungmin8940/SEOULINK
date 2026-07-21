export const travelCodeDimensions = [
    {
        key: 'activity',
        title: '활동 성향',
        question: '여행할 때 나는?',
        fallback: 'A',
        options: {
            A: {
                label: '활동형',
                answer: '많이 돌아다니는 활동형',
                short: '많이 걷고 탐방하기',
                description: '다양한 장소를 직접 돌아다니며 새로운 경험을 즐겨요',
                icon: 'walk',
                color: 'tone-a',
            },
            H: {
                label: '휴식형',
                answer: '천천히 머무는 휴식형',
                short: '느긋하게 머물기',
                description: '이동을 줄이고 여유롭게 머무는 여행을 선호해요',
                icon: 'leaf',
                color: 'tone-h',
            },
        },
    },
    {
        key: 'culture',
        title: '문화 취향',
        question: '어떤 장소가 끌리나요?',
        fallback: 'T',
        options: {
            T: {
                label: '역사형',
                answer: '역사와 전통을 좋아해요',
                short: '역사와 전통 즐기기',
                description: '궁궐, 한옥, 전통 마을처럼 이야기가 있는 장소에 의미를 찾아요',
                icon: 'landmark',
                color: 'tone-t',
            },
            M: {
                label: '현대형',
                answer: '트렌디하고 현대적인 장소가 좋아요',
                short: '트렌드 따라가기',
                description: '핫플레이스, 전시, 쇼핑 거리처럼 지금의 서울을 느끼고 싶어해요',
                icon: 'spark',
                color: 'tone-m',
            },
        },
    },
    {
        key: 'budget',
        title: '소비 성향',
        question: '여행에서 중요한 것은?',
        fallback: 'B',
        options: {
            B: {
                label: '가성비형',
                answer: '가성비를 중요하게 생각해요',
                short: '합리적인 소비',
                description: '비용 대비 만족도가 높은 맛집과 체험을 찾아 알뜰하게 여행해요',
                icon: 'wallet',
                color: 'tone-b',
            },
            L: {
                label: '프리미엄형',
                answer: '퀄리티와 특별함을 중요하게 생각해요',
                short: '특별한 경험',
                description: '조금 더 비용을 쓰더라도 완성도 높은 공간과 서비스를 선호해요',
                icon: 'star',
                color: 'tone-l',
            },
        },
    },
    {
        key: 'mood',
        title: '자극 성향',
        question: '어떤 분위기의 여행을 선호하나요?',
        fallback: 'S',
        options: {
            S: {
                label: '안정형',
                answer: '안정적이고 무난한 코스를 선호해요',
                short: '안정적인 일정',
                description: '실패 확률이 낮고 누구와 가도 만족하기 쉬운 코스를 좋아해요',
                icon: 'shield',
                color: 'tone-s',
            },
            D: {
                label: '도파민형',
                answer: '새롭고 자극적인 코스를 선호해요',
                short: '새로운 자극',
                description: '인기 급상승 공간, 액티비티, 색다른 동선을 적극적으로 즐겨요',
                icon: 'heart',
                color: 'tone-d',
            },
        },
    },
    {
        key: 'density',
        title: '일정 밀도',
        question: '하루 일정을 어떻게 보내고 싶나요?',
        fallback: 'P',
        options: {
            P: {
                label: '빽빽한 일정형',
                answer: '여러 곳을 충분히 다니고 싶어요',
                short: '알찬 하루 구성',
                description: '하루 안에 관광지, 식당, 카페를 촘촘하게 엮어 다니는 걸 선호해요',
                icon: 'calendar',
                color: 'tone-p',
            },
            R: {
                label: '여유 일정형',
                answer: '적은 장소를 여유롭게 보고 싶어요',
                short: '여유 있는 하루',
                description: '장소 수보다 체류 시간과 컨디션을 더 중요하게 생각해요',
                icon: 'clock',
                color: 'tone-r',
            },
        },
    },
];

export const travelGuideData = {
    P: {
        category: '추천 일정 템포',
        title: '계획적으로 움직여요',
        description:
            '방문 순서와 이동 시간을 미리 정해 알찬 하루를 만들어보세요',
        icon: 'calendar',
        color: 'blue',
    },
    R: {
        category: '추천 일정 템포',
        title: '여유롭게 움직여요',
        description:
            '꼭 가고 싶은 장소를 중심으로 상황에 따라 자유롭게 움직여보세요',
        icon: 'clock',
        color: 'blue',
    },

    S: {
        category: '어울리는 장소',
        title: '검증된 명소를 즐겨요',
        description:
            '대표 관광지와 많은 여행자가 만족한 장소를 중심으로 둘러보세요',
        icon: 'shield',
        color: 'green',
    },
    D: {
        category: '어울리는 장소',
        title: '새로운 공간을 발견해요',
        description:
            '유명 명소와 함께 새로운 골목과 동네 공간도 둘러보세요',
        icon: 'landmark',
        color: 'green',
    },

    L: {
        category: '예산 활용법',
        title: '특별한 경험에 투자해요',
        description:
            '좋은 숙소와 식사처럼 만족도가 높은 경험에 예산을 사용해보세요',
        icon: 'star',
        color: 'orange',
    },
    B: {
        category: '예산 활용법',
        title: '합리적으로 즐겨요',
        description:
            '비용 대비 만족도가 높은 로컬 음식과 동네 경험을 찾아보세요',
        icon: 'wallet',
        color: 'orange',
    },
};

export function getCodeTraits(travelCode = '') {
    const normalizedCode =
        String(travelCode).trim().toUpperCase();

    return travelCodeDimensions.map(
        (dimension, index) => {
            const codeLetter =
                normalizedCode[index] ||
                dimension.fallback;

            const option =
                dimension.options[codeLetter] ||
                dimension.options[dimension.fallback];

            return {
                ...option,
                code: codeLetter,
                dimensionKey: dimension.key,
                title: dimension.title,
                question: dimension.question,
            };
        }
    );
}

export function getTravelTags(travelCode) {
    return getCodeTraits(travelCode).map((trait) => trait.label);
}

export function getTravelGuideItems(travelCode = '') {
    const normalizedCode = travelCode.trim().toUpperCase();

    return [
        travelGuideData[normalizedCode.charAt(4)], // P/R: 일정
        travelGuideData[normalizedCode.charAt(3)], // S/D: 장소
        travelGuideData[normalizedCode.charAt(2)], // L/B: 예산
    ].filter(Boolean);
}

export function getPreferredRegions(places = []) {
    const regionStats = new Map();

    places.forEach((place) => {
        const region = place.region?.trim();

        if (!region) {
            return;
        }

        const previous =
            regionStats.get(region) || {
                count: 0,
                totalScore: 0,
            };

        regionStats.set(region, {
            count: previous.count + 1,
            totalScore:
                previous.totalScore +
                (place.recommendationScore || 0),
        });
    });

    return [...regionStats.entries()]
        .sort((first, second) => {
            const countDifference =
                second[1].count -
                first[1].count;

            if (countDifference !== 0) {
                return countDifference;
            }

            const scoreDifference =
                second[1].totalScore -
                first[1].totalScore;

            if (scoreDifference !== 0) {
                return scoreDifference;
            }

            return first[0].localeCompare(
                second[0],
                'ko'
            );
        })
        .map(([region]) => region);
}
