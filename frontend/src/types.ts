export interface NightlyRate {
  date: string;
  amount: number;
}

export interface RoomOffer {
  id: string;
  name: string;
  details: string;
  maxGuests: number;
  availableRooms: number;
  nightlyRates: NightlyRate[];
  totalPrice: number;
}

export interface Hotel {
  id: string;
  name: string;
  city: string;
  district: string;
  address: string;
  country: string;
  summary: string;
  imagePath: string;
  imageAlt: string;
  rating: number;
  roomTypes: RoomType[];
}

export interface RoomType {
  id: string;
  hotelId: string;
  name: string;
  details: string;
  maxGuests: number;
  totalInventory: number;
}

export interface SearchStay {
  id: string;
  name: string;
  city: string;
  district: string;
  address: string;
  summary: string;
  imagePath: string;
  imageAlt: string;
  rating: number;
  rooms: RoomOffer[];
}

export interface SearchResponse {
  stays: SearchStay[];
  checkIn: string;
  checkOut: string;
  guests: number;
}

export interface Reservation {
  id: string;
  hotelId: string;
  roomTypeId: string;
  hotelName: string;
  city: string;
  district: string;
  imagePath: string;
  imageAlt: string;
  roomTypeName: string;
  checkIn: string;
  checkOut: string;
  rooms: number;
  guests: number;
  guestName: string;
  guestEmail: string;
  total: number;
  status: "PAYMENT_PENDING" | "CONFIRMED" | "CANCELLED" | "PAYMENT_FAILED";
  paymentId: string | null;
  createdAt: string;
}

export interface SearchFormValues {
  destination: string;
  checkIn: string;
  checkOut: string;
  guests: string;
}

export interface GuestDetails {
  name: string;
  email: string;
}

export interface RoomTypeDraft {
  name: string;
  details: string;
  maxGuests: number;
  totalInventory: number;
}
