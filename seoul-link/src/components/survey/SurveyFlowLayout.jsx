import Header from '../common/Header';
import Footer from '../common/Footer';
import RecommendationSteps from './RecommendationSteps';

function SurveyFlowLayout({ currentStep, children }) {
    return (
        <div className="page survey-flow-page">
            <Header variant="default" />

            <main className="survey-flow-shell">
                <RecommendationSteps currentStep={currentStep} />
                {children}
            </main>

            <Footer />
        </div>
    );
}

export default SurveyFlowLayout;
