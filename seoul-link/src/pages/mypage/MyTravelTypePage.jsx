import { useEffect, useState } from "react";

import SurveyFlowLayout from "../../components/survey/SurveyFlowLayout";
import { getMyTravelType } from "../../api/mypageApi";
import { authStore } from "../../store/authStore";

const SURVEY_ID_STORAGE_KEY = "seoulinkSurveyId";

/**
 * 영환 마이페이지와 동일하게 최신 회원 설문의 ID를 복원한 뒤,
 * 기존 설문 결과 화면으로 이동한다.
 */
export default function MyTravelTypePage() {
    const member = authStore.getMember();
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        const moveToSurveyResult = async () => {
            if (!member?.memberId) {
                setErrorMessage("로그인 회원 정보를 확인할 수 없습니다.");
                return;
            }

            try {
                const travelType = await getMyTravelType(member.memberId);
                const surveyId = Number(travelType?.surveyId);

                if (!Number.isInteger(surveyId) || surveyId < 1) {
                    throw new Error("저장된 취향 검사 결과가 없습니다.");
                }

                sessionStorage.setItem(
                    SURVEY_ID_STORAGE_KEY,
                    String(surveyId),
                );
                window.location.replace("/survey/result");
            } catch (error) {
                setErrorMessage(
                    error?.message || "취향 검사 결과를 불러오지 못했습니다.",
                );
            }
        };

        moveToSurveyResult();
    }, [member?.memberId]);

    return (
        <SurveyFlowLayout currentStep={3}>
            <section className="survey-stage-card">
                {errorMessage ? (
                    <>
                        <p className="survey-question-error">{errorMessage}</p>
                        <a className="survey-stage-primary-btn" href="/travel-info">
                            취향 검사 시작하기
                        </a>
                    </>
                ) : (
                    <p>최근 취향 검사 결과를 불러오고 있습니다.</p>
                )}
            </section>
        </SurveyFlowLayout>
    );
}
