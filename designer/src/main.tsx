import { createRoot } from "react-dom/client";
import { DesignerApp } from "./app/DesignerApp";
import "./styles.css";

const root = document.getElementById("root");
if (!root) {
  throw new Error("Valerian Designer root element is missing");
}

createRoot(root).render(<DesignerApp />);
