import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const hotelService = process.env.HOTEL_SERVICE_URL ?? "http://localhost:8081";
const rateService = process.env.RATE_SERVICE_URL ?? "http://localhost:8082";
const reservationService = process.env.RESERVATION_SERVICE_URL ?? "http://localhost:8083";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api/hotels": hotelService,
      "/api/admin/hotels": hotelService,
      "/api/admin/room-types": hotelService,
      "/api/rates": rateService,
      "/api/admin/rates": rateService,
      "/api/search": reservationService,
      "/api/reservations": reservationService,
      "/api/admin/inventory": reservationService
    }
  },
  build: {
    sourcemap: false,
    cssMinify: true
  }
});
