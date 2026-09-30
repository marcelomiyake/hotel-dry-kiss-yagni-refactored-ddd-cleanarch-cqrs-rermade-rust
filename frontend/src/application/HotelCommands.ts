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

export interface HotelCommands {
  createReservation(command: CreateReservationCommand): Promise<Reservation>;

  cancelReservation(id: string): Promise<Reservation>;

  createRoomType(hotelId: string, draft: RoomTypeDraft, adminKey: string): Promise<{ id: string }>;

  updateRoomType(id: string, draft: RoomTypeDraft, adminKey: string): Promise<void>;

  createRateSchedule(roomTypeId: string, baseRate: number, adminKey: string): Promise<void>;

  changeInventory(command: ChangeInventoryCommand, adminKey: string): Promise<void>;

  removeRoomType(id: string, adminKey: string): Promise<void>;
}
