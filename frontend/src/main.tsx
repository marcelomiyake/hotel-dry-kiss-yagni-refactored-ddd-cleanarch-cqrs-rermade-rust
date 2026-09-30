import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HotelApp } from "./App";
import { HttpHotelGateway } from "./infrastructure/HttpHotelGateway";
import "./styles.css";

const hotelGateway = new HttpHotelGateway();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HotelApp queries={hotelGateway} commands={hotelGateway} />
  </StrictMode>,
);
