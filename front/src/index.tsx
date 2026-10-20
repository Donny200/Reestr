import "./index.css";
import ReactDOM from "react-dom/client";
import { App } from "./App";

// The service comparison feature was removed; forget the selection it kept in this browser.
try {
  window.localStorage.removeItem("reestr-compare-services");
} catch {
  // Storage blocked: nothing was stored either.
}

const rootEl = document.getElementById("root");
if (rootEl) {
  ReactDOM.createRoot(rootEl).render(<App />);
}
