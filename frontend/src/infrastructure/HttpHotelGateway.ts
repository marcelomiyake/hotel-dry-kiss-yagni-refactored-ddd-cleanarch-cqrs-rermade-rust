import type { HotelCommands, CreateReservationCommand, ChangeInventoryCommand } from "../application/HotelCommands";
import type { HotelQueries, SearchStaysQuery } from "../application/HotelQueries";
import { api } from "../api";
import type { Hotel, Reservation, RoomTypeDraft, SearchResponse } from "../types";

export class HttpHotelGateway implements HotelQueries, HotelCommands {
  async searchStays(query: SearchStaysQuery): Promise<SearchResponse> {
    const parameters = new URLSearchParams({
      destination: query.destination,
      checkIn: query.checkIn,
      checkOut: query.checkOut,
      guests: String(query.guests),
    });
    return api<SearchResponse>(`/api/search?${parameters.toString()}`);
  }

  findReservationsByEmail(email: string): Promise<Reservation[]> {
    return api<Reservation[]>(`/api/reservations?email=${encodeURIComponent(email)}`);
  }

  listHotels(): Promise<Hotel[]> {
    return api<Hotel[]>("/api/hotels?destination=");
  }

  createReservation(command: CreateReservationCommand): Promise<Reservation> {
    return api<Reservation>("/api/reservations", {
      method: "POST",
      body: JSON.stringify(command),
    });
  }

  cancelReservation(id: string): Promise<Reservation> {
    return api<Reservation>(`/api/reservations/${encodeURIComponent(id)}`, { method: "DELETE" });
  }

  createRoomType(hotelId: string, draft: RoomTypeDraft, adminKey: string): Promise<{ id: string }> {
    return api<{ id: string }>(`/api/admin/hotels/${encodeURIComponent(hotelId)}/room-types`, {
      method: "POST",
      headers: this.adminHeaders(adminKey),
      body: JSON.stringify(draft),
    });
  }

  updateRoomType(id: string, draft: RoomTypeDraft, adminKey: string): Promise<void> {
    return api<void>(`/api/admin/room-types/${encodeURIComponent(id)}`, {
      method: "PUT",
      headers: this.adminHeaders(adminKey),
      body: JSON.stringify(draft),
    });
  }

  createRateSchedule(roomTypeId: string, baseRate: number, adminKey: string): Promise<void> {
    return api<void>("/api/admin/rates/schedule", {
      method: "POST",
      headers: this.adminHeaders(adminKey),
      body: JSON.stringify({ roomTypeId, baseRate }),
    });
  }

  changeInventory(command: ChangeInventoryCommand, adminKey: string): Promise<void> {
    return api<void>("/api/admin/inventory", {
      method: "PUT",
      headers: this.adminHeaders(adminKey),
      body: JSON.stringify(command),
    });
  }

  removeRoomType(id: string, adminKey: string): Promise<void> {
    return api<void>(`/api/admin/room-types/${encodeURIComponent(id)}`, {
      method: "DELETE",
      headers: this.adminHeaders(adminKey),
    });
  }

  private adminHeaders(adminKey: string): HeadersInit {
    return { "X-Admin-Key": adminKey };
  }
}
