import type { Reservation, RoomTypeDraft } from "../types";

export interface CreateReservationCommand {
  readonly reservationId: string;
  readonly hotelId: string;
  readonly roomTypeId: string;
  readonly checkIn: string;
  readonly checkOut: string;
  readonly rooms: number;
  readonly guests: number;
  readonly guestName: string;
  readonly guestEmail: string;
}

export interface ChangeInventoryCommand {
  readonly hotelId: string;
  readonly roomTypeId: string;
  readonly totalInventory: number;
}

export type ReservationJourneyScreen =
  | "search"
  | "results"
  | "details"
  | "checkout"
  | "confirmation"
  | "bookings"
  | "staff";

export interface ReservationJourneyEvent {
  readonly journeyId: string;
  readonly sequence: number;
  readonly eventType: "started" | "screen_viewed" | "completed";
  readonly screen: ReservationJourneyScreen;
}

export interface HotelCommands {
  createReservation(command: CreateReservationCommand): Promise<Reservation>;

  recordReservationJourneyEvent(event: ReservationJourneyEvent): Promise<void>;

  cancelReservation(id: string): Promise<Reservation>;

  createRoomType(hotelId: string, draft: RoomTypeDraft, adminKey: string): Promise<{ id: string }>;

  updateRoomType(id: string, draft: RoomTypeDraft, adminKey: string): Promise<void>;

  createRateSchedule(roomTypeId: string, baseRate: number, adminKey: string): Promise<void>;

  changeInventory(command: ChangeInventoryCommand, adminKey: string): Promise<void>;

  removeRoomType(id: string, adminKey: string): Promise<void>;
}
