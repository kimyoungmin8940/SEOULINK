import "./App.css";
import Router from "./routes/Router";
import PhotoFilterDefinitions from "./components/common/PhotoFilterDefinitions";
import AuthSessionManager from "./components/common/AuthSessionManager";

function App() {
    return (
        <>
            <AuthSessionManager />
            <PhotoFilterDefinitions />
            <Router />
        </>
    );
}

export default App;