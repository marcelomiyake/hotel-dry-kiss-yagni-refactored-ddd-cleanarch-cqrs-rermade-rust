import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { HotelApp } from "../src/App";
import { HttpHotelGateway } from "../src/infrastructure/HttpHotelGateway";
import type { Hotel, Reservation, SearchResponse, SearchStay } from "../src/types";

const firstRoom = {
  id: "room-deluxe",
  name: "Deluxe King",
  details: "King bed · 2 guests · 35 m²",
  maxGuests: 2,
  availableRooms: 5,
  nightlyRates: [
    { date: "2026-11-10", amount: 620 },
    { date: "2026-11-11", amount: 640 },
  ],
  totalPrice: 1260,
};
const secondRoom = { ...firstRoom, id: "room-suite", name: "Executive Suite", totalPrice: 1800, nightlyRates: [{ date: "2026-11-10", amount: 900 }, { date: "2026-11-11", amount: 900 }] };
const firstStay: SearchStay = {
  id: "hotel-ritz",
  name: "Four Seasons Hotel Ritz Lisbon",
  city: "Lisbon",
  district: "Avenidas Novas",
  address: "Rua Rodrigo da Fonseca 88",
  summary: "A landmark city address.",
  imagePath: "/images/ritz.webp",
  imageAlt: "Hotel in Lisbon",
  rating: 9.5,
  rooms: [firstRoom, secondRoom],
};
const secondStay: SearchStay = { ...firstStay, id: "hotel-palace", name: "Pestana Palace Lisboa", rating: 9.2, rooms: [firstRoom] };
const searchResult: SearchResponse = {
  stays: [firstStay, secondStay],
  checkIn: "2026-11-10",
  checkOut: "2026-11-12",
  guests: 2,
};
const hotelCatalog: Hotel[] = [{
  id: firstStay.id,
  name: firstStay.name,
  city: "Lisbon",
  district: "Avenidas Novas",
  address: firstStay.address,
  country: "Portugal",
  summary: firstStay.summary,
  imagePath: firstStay.imagePath,
  imageAlt: firstStay.imageAlt,
  rating: firstStay.rating,
  roomTypes: [{ id: "room-deluxe", hotelId: firstStay.id, name: "Deluxe King", details: firstRoom.details, maxGuests: 2, totalInventory: 12 }],
}];
const booking: Reservation = {
  id: "12345678-aaaa-bbbb-cccc-123456789012",
  hotelId: firstStay.id,
  roomTypeId: firstRoom.id,
  hotelName: firstStay.name,
  city: "Lisbon",
  district: "Avenidas Novas",
  imagePath: firstStay.imagePath,
  imageAlt: firstStay.imageAlt,
  roomTypeName: "Deluxe King",
  checkIn: "2026-11-10",
  checkOut: "2026-11-12",
  rooms: 1,
  guests: 2,
  guestName: "Alex Guest",
  guestEmail: "alex@example.com",
  total: 1260,
  status: "CONFIRMED",
  paymentId: "payment-123",
  createdAt: "2026-09-28T12:00:00Z",
};

function response(data: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function setFetch(...responses: Response[]) {
  const fetchMock = vi.fn();
  responses.forEach((result) => fetchMock.mockResolvedValueOnce(result));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const hotelGateway = new HttpHotelGateway();

function renderHotelApp() {
  return render(<HotelApp queries={hotelGateway} commands={hotelGateway} />);
}

beforeEach(() => {
  vi.spyOn(crypto, "randomUUID").mockReturnValue("11111111-2222-4333-8444-555555555555");
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("hotel reservation experience", () => {
  it("validates a search and completes booking, history, and cancellation", async () => {
    const fetchMock = setFetch(
      response(searchResult),
      response(booking),
      response([booking]),
      response({ ...booking, status: "CANCELLED" }),
    );
    renderHotelApp();
    expect(screen.getByRole("heading", { name: "Find a stay that feels like Lisbon." })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Destination"), { target: { value: " " } });
    fireEvent.click(screen.getByRole("button", { name: "Search stays" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter a destination");
    fireEvent.change(screen.getByLabelText("Destination"), { target: { value: "Lisbon, Portugal" } });
    fireEvent.click(screen.getByRole("button", { name: "Search stays" }));

    expect(await screen.findByText("2 stays to compare")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Sort stays"), { target: { value: "rating" } });
    fireEvent.change(screen.getByLabelText("Sort stays"), { target: { value: "price" } });
    fireEvent.click(screen.getAllByRole("button", { name: "View rooms" })[0]);
    expect(await screen.findByRole("heading", { name: "Four Seasons Hotel Ritz Lisbon", level: 1 })).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText(/Executive Suite/));
    fireEvent.click(screen.getByRole("button", { name: "Continue to guest details" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm reservation" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Add a valid name and email");
    fireEvent.change(screen.getByLabelText("Full name"), { target: { value: "Alex Guest" } });
    fireEvent.change(screen.getByLabelText("Email address"), { target: { value: "alex@example.com" } });
    fireEvent.click(screen.getByLabelText(/reviewed the room details/));
    fireEvent.click(screen.getByRole("button", { name: "Confirm reservation" }));
    expect(await screen.findByRole("heading", { name: "Your Lisbon stay is on the list." })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/reservations", expect.objectContaining({
      method: "POST",
      body: expect.stringContaining('"guestEmail":"alex@example.com"'),
    }));

    fireEvent.click(screen.getByRole("button", { name: /View my bookings/ }));
    expect(await screen.findByRole("heading", { name: "My bookings", level: 1 })).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: firstStay.name, level: 2 })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Cancel reservation" }));
    const dialog = screen.getByRole("dialog", { name: "Cancel this reservation?" });
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel reservation" }));
    expect(await screen.findByText("Cancelled", { selector: ".booking-state" })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith(`/api/reservations/${booking.id}`, { method: "DELETE", headers: { "Content-Type": "application/json" } });
  });

  it("shows empty and failed search states and retries", async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error("Network unavailable"))
      .mockResolvedValueOnce(response({ ...searchResult, stays: [] }));
    vi.stubGlobal("fetch", fetchMock);
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "Search stays" }));
    expect(await screen.findByRole("heading", { name: "We couldn’t check availability." })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try search again" }));
    expect(await screen.findByRole("heading", { name: "No rooms are available for these dates." })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Change search" }));
    expect(screen.getByRole("heading", { name: "Find a stay that feels like Lisbon." })).toBeInTheDocument();
  });

  it("loads bookings by email and handles empty and failed history", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response([booking]))
      .mockRejectedValueOnce(new Error("History unavailable"));
    vi.stubGlobal("fetch", fetchMock);
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "My bookings" }));
    expect(await screen.findByRole("heading", { name: "No bookings found for this email." })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Email address used for booking"), { target: { value: "alex@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Find bookings" }));
    expect(await screen.findByRole("heading", { name: firstStay.name, level: 2 })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Email address used for booking"), { target: { value: "alex@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Find bookings" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("History unavailable");
  });

  it("requires email to look up history", async () => {
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "My bookings" }));
    fireEvent.change(screen.getByLabelText("Email address used for booking"), { target: { value: "" } });
    fireEvent.submit(screen.getByRole("button", { name: "Find bookings" }).closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter the email address used");
  });

  it("updates, adds, and removes a room type from staff", async () => {
    const addedCatalog = [{ ...hotelCatalog[0], roomTypes: [...hotelCatalog[0].roomTypes, {
      id: "new-room", hotelId: firstStay.id, name: "Family Suite", details: "Two beds · 4 guests", maxGuests: 4, totalInventory: 10,
    }] }];
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(hotelCatalog))
      .mockResolvedValueOnce(response({}, 200))
      .mockResolvedValueOnce(response(null, 204))
      .mockResolvedValueOnce(response(hotelCatalog))
      .mockResolvedValueOnce(response({ id: "new-room" }, 201))
      .mockResolvedValueOnce(response(null, 204))
      .mockResolvedValueOnce(response(null, 204))
      .mockResolvedValueOnce(response(addedCatalog))
      .mockResolvedValueOnce(response(null, 204))
      .mockResolvedValueOnce(response(hotelCatalog));
    vi.stubGlobal("fetch", fetchMock);
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "Staff" }));
    expect(await screen.findByRole("heading", { name: "Staff room management" })).toBeInTheDocument();
    await screen.findByLabelText("Room name");
    await waitFor(() => expect(screen.getByLabelText("Room name")).toHaveValue("Deluxe King"));
    fireEvent.click(screen.getByRole("button", { name: "Save room details" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Enter the staff key");
    fireEvent.change(screen.getByLabelText("Staff key"), { target: { value: "staff-test-key" } });
    fireEvent.click(screen.getByRole("button", { name: "Save room details" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Room type and inventory updated");

    fireEvent.click(screen.getByRole("button", { name: "Add another room type" }));
    fireEvent.change(screen.getByLabelText("Room name"), { target: { value: "Family Suite" } });
    fireEvent.change(screen.getByLabelText("Room details"), { target: { value: "Two beds · 4 guests" } });
    fireEvent.click(screen.getByRole("button", { name: "Add room type" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/admin/rates/schedule", expect.any(Object)));
    expect(await screen.findByRole("status")).toHaveTextContent("Room type, inventory, and nightly rates added");

    fireEvent.click(screen.getByRole("button", { name: "Remove room type" }));
    const removeDialog = screen.getByRole("dialog", { name: "Remove this room type?" });
    fireEvent.click(within(removeDialog).getByRole("button", { name: "Remove room type" }));
    expect(await screen.findByRole("status")).toHaveTextContent("Room type removed from new searches");
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/hotels/hotel-ritz/room-types", expect.objectContaining({
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Admin-Key": "staff-test-key" },
    }));
  });

  it("shows staff catalog and update errors", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(hotelCatalog))
      .mockRejectedValueOnce(new Error("Staff update failed"));
    vi.stubGlobal("fetch", fetchMock);
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "Staff" }));
    await screen.findByLabelText("Room name");
    fireEvent.change(screen.getByLabelText("Staff key"), { target: { value: "staff-test-key" } });
    fireEvent.click(screen.getByRole("button", { name: "Save room details" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Staff update failed");
  });

  it("reports staff catalog load errors", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("Catalog unavailable")));
    renderHotelApp();
    fireEvent.click(screen.getByRole("button", { name: "Staff" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Catalog unavailable");
  });
});
