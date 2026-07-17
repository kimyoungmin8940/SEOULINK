import { useMemo, useState } from 'react';
import {
    ArrowLeft,
    ArrowRight,
    CalendarDays,
    Car,
    Footprints,
    Heart,
    House,
    TrainFront,
    UserRound,
    UsersRound,
} from 'lucide-react';

import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';

const TRAVEL_INFO_STORAGE_KEY = 'seoulinkTravelInfo';

const companionOptions = [
    { value: 'solo', label: '혼자', Icon: UserRound },
    { value: 'couple', label: '연인', Icon: Heart },
    { value: 'friends', label: '친구', Icon: UsersRound },
    { value: 'family', label: '가족', Icon: House },
];

const transportOptions = [
    { value: 'public', label: '대중교통', description: '지하철과 버스 중심', Icon: TrainFront },
    { value: 'walking', label: '도보 중심', description: '가까운 장소 위주', Icon: Footprints },
    { value: 'car', label: '자동차', description: '차량 이동 중심', Icon: Car },
];

function getToday() {
    const today = new Date();
    const localToday = new Date(today.getTime() - today.getTimezoneOffset() * 60 * 1000);
    return localToday.toISOString().slice(0, 10);
}

function getMaximumEndDate(startDate) {
    if (!startDate) {
        return '';
    }

    const maximumEndDate =
        new Date(`${startDate}T00:00:00`);

    // 시작일 포함 7일이므로 6일을 더한다.
    maximumEndDate.setDate(
        maximumEndDate.getDate() + 6
    );

    const localMaximumEndDate =
        new Date(
            maximumEndDate.getTime() -
            maximumEndDate.getTimezoneOffset() *
            60 *
            1000
        );

    return localMaximumEndDate
        .toISOString()
        .slice(0, 10);
}

function getInitialTravelInfo() {
    try {
        const storedTravelInfo = sessionStorage.getItem(TRAVEL_INFO_STORAGE_KEY);

        if (storedTravelInfo) {
            return JSON.parse(storedTravelInfo);
        }
    } catch {
        // 임시 저장값을 읽을 수 없으면 빈 입력 상태로 시작
    }

    return {
        startDate: '',
        endDate: '',
        companionType: '',
        transportType: '',
    };
}

function TravelInfoPage() {
    const [travelInfo, setTravelInfo] = useState(getInitialTravelInfo);
    const [errorMessage, setErrorMessage] = useState('');
    const today = useMemo(() => getToday(), []);

    const maximumEndDate = useMemo(
        () =>
            getMaximumEndDate(
                travelInfo.startDate
            ),
        [travelInfo.startDate]
    );

    const travelDays = useMemo(() => {
        if (!travelInfo.startDate || !travelInfo.endDate) {
            return 0;
        }

        const startDate = new Date(`${travelInfo.startDate}T00:00:00`);
        const endDate = new Date(`${travelInfo.endDate}T00:00:00`);
        const difference = Math.round((endDate - startDate) / (1000 * 60 * 60 * 24));

        return difference >= 0 ? difference + 1 : 0;
    }, [travelInfo.endDate, travelInfo.startDate]);

    const updateTravelInfo = (field, value) => {
        setTravelInfo((previousTravelInfo) => ({
            ...previousTravelInfo,
            [field]: value,
        }));
        setErrorMessage('');
    };

    const handleSubmit = (event) => {
        event.preventDefault();

        if (!travelInfo.startDate || !travelInfo.endDate) {
            setErrorMessage('여행 시작일과 종료일을 모두 선택해주세요');
            return;
        }

        if (travelDays < 1) {
            setErrorMessage(
                '여행 종료일은 시작일과 같거나 이후여야 합니다'
            );
            return;
        }

        if (travelDays > 7) {
            setErrorMessage(
                '여행 기간은 최대 7일까지 선택할 수 있습니다'
            );
            return;
        }

        if (!travelInfo.companionType) {
            setErrorMessage('누구와 여행하는지 선택해주세요');
            return;
        }

        if (!travelInfo.transportType) {
            setErrorMessage('주로 이용할 이동 수단을 선택해주세요');
            return;
        }

        sessionStorage.setItem(
            TRAVEL_INFO_STORAGE_KEY,
            JSON.stringify({
                region: '서울',
                ...travelInfo,
                travelDays,
            }),
        );

        window.location.assign('/survey');
    };

    return (
        <SurveyFlowLayout currentStep={1}>
            <section className="travel-info-card" aria-labelledby="travel-info-title">
                <div className="travel-info-heading">
                    <p className="survey-flow-eyebrow">나만의 서울 여행 준비</p>
                    <h1 id="travel-info-title">여행 기본 정보를 알려주세요</h1>
                    <p>입력한 정보는 여행 일정과 장소 이동 순서를 추천할 때 사용됩니다</p>
                </div>

                <form className="travel-info-form" onSubmit={handleSubmit} noValidate>
                    <fieldset className="travel-info-fieldset">
                        <legend>
                            <span className="travel-info-legend-icon">
                                <CalendarDays size={20} strokeWidth={2.1} aria-hidden="true" />
                            </span>
                            <span>
                                여행 일정
                                <small>서울에서 머무를 날짜를 선택해주세요</small>
                            </span>
                        </legend>

                        <div className="travel-date-grid">
                            <label>
                                <span>여행 시작일</span>
                                <input
                                    type="date"
                                    min={today}
                                    value={travelInfo.startDate}
                                    onChange={(event) => updateTravelInfo('startDate', event.target.value)}
                                />
                            </label>

                            <span className="travel-date-divider">―</span>

                            <label>
                                <span>여행 종료일</span>
                                <input
                                    type="date"
                                    min={travelInfo.startDate || today}
                                    max={maximumEndDate || undefined}
                                    value={travelInfo.endDate}
                                    onChange={(event) =>
                                        updateTravelInfo(
                                            'endDate',
                                            event.target.value
                                        )
                                    }
                                />
                            </label>

                            <div className={`travel-days-summary${travelDays > 0 ? ' visible' : ''}`} aria-live="polite">
                                {travelDays > 0 ? `${travelDays}일 여행` : '일정 선택'}
                            </div>
                        </div>
                    </fieldset>

                    <fieldset className="travel-info-fieldset">
                        <legend>
                            <span className="travel-info-legend-icon">
                                <UsersRound size={20} strokeWidth={2.1} aria-hidden="true" />
                            </span>
                            <span>
                                동행 유형
                                <small>누구와 함께하는 여행인가요?</small>
                            </span>
                        </legend>

                        <div className="companion-option-grid">
                            {companionOptions.map(({ value, label, Icon }) => (
                                <label
                                    className={`travel-choice-card${travelInfo.companionType === value ? ' selected' : ''}`}
                                    key={value}
                                >
                                    <input
                                        type="radio"
                                        name="companionType"
                                        value={value}
                                        checked={travelInfo.companionType === value}
                                        onChange={() => updateTravelInfo('companionType', value)}
                                    />
                                    <Icon size={24} strokeWidth={1.9} aria-hidden="true" />
                                    <span>{label}</span>
                                </label>
                            ))}
                        </div>
                    </fieldset>

                    <fieldset className="travel-info-fieldset">
                        <legend>
                            <span className="travel-info-legend-icon">
                                <TrainFront size={20} strokeWidth={2.1} aria-hidden="true" />
                            </span>
                            <span>
                                주 이동 수단
                                <small>장소 간 이동 시간을 계산하는 데 사용됩니다</small>
                            </span>
                        </legend>

                        <div className="transport-option-grid">
                            {transportOptions.map(({ value, label, description, Icon }) => (
                                <label
                                    className={`travel-choice-card transport-choice${travelInfo.transportType === value ? ' selected' : ''}`}
                                    key={value}
                                >
                                    <input
                                        type="radio"
                                        name="transportType"
                                        value={value}
                                        checked={travelInfo.transportType === value}
                                        onChange={() => updateTravelInfo('transportType', value)}
                                    />
                                    <Icon size={25} strokeWidth={1.9} aria-hidden="true" />
                                    <span>
                                        <strong>{label}</strong>
                                        <small>{description}</small>
                                    </span>
                                </label>
                            ))}
                        </div>
                    </fieldset>

                    {errorMessage && (
                        <p className="travel-info-error" role="alert">
                            {errorMessage}
                        </p>
                    )}

                    <div className="travel-info-actions">
                        <a className="travel-info-back-btn" href="/">
                            <ArrowLeft size={18} strokeWidth={2.2} aria-hidden="true" />
                            메인으로
                        </a>

                        <button className="travel-info-next-btn" type="submit">
                            취향 검사 시작
                            <ArrowRight size={19} strokeWidth={2.2} aria-hidden="true" />
                        </button>
                    </div>
                </form>
            </section>
        </SurveyFlowLayout>
    );
}

export default TravelInfoPage;
