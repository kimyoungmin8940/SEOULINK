import SurveyFlowLayout from '../../components/survey/SurveyFlowLayout';
import PreferenceResultPage from '../PreferenceResultPage';

function SurveyResultPage() {
    return (
        <SurveyFlowLayout currentStep={3}>
            <PreferenceResultPage />
        </SurveyFlowLayout>
    );
}

export default SurveyResultPage;
