import type { Hotel, Reservation, SearchResponse } from "../types";

export interface SearchStaysQuery {
  readonly destination: string;
  readonly checkIn: string;
  readonly checkOut: string;
  readonly guests: number;
}

export interface HotelQueries {
  searchStays(query: SearchStaysQuery): Promise<SearchResponse>;

  findReservationsByEmail(email: string): Promise<Reservation[]>;

  listHotels(): Promise<Hotel[]>;
}
