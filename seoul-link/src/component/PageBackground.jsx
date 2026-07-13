import "./PageBackground.css";
import background from "../assets/background.png";

export default function PageBackground({ children }) {
    return (
        <div
            className="page-background"
            style={{ backgroundImage: `url(${background})` }}
        >
            <div className="page-background-dim">{children}</div>
        </div>
    );
}