import { describe, expect, it } from "vitest";
import { hasValidGuestDetails, isValidEmail, validateSearch } from "../src/domain/booking";

describe("booking rules", () => {
  it("requires a destination and a forward stay", () => {
    expect(validateSearch({ destination: " ", checkIn: "2026-11-10", checkOut: "2026-11-12", guests: "2" }))
      .toBe("Enter a destination to search.");
    expect(validateSearch({ destination: "Lisbon", checkIn: "", checkOut: "", guests: "2" }))
      .toBe("Choose a check-out date after your check-in date.");
    expect(validateSearch({ destination: "Lisbon", checkIn: "2026-11-12", checkOut: "2026-11-10", guests: "2" }))
      .toBe("Choose a check-out date after your check-in date.");
    expect(validateSearch({ destination: "Lisbon", checkIn: "2026-11-10", checkOut: "2026-11-12", guests: "2" }))
      .toBeNull();
  });

  it("validates guest identity and booking policy", () => {
    expect(isValidEmail("guest@example.com")).toBe(true);
    expect(isValidEmail("guest@@example.com")).toBe(false);
    expect(isValidEmail("guest@.example.com")).toBe(false);
    expect(isValidEmail("guest@example.com.")).toBe(false);
    expect(isValidEmail("guest @example.com")).toBe(false);
    expect(isValidEmail("guest@example")).toBe(false);
    expect(isValidEmail("guestexample.com")).toBe(false);
    expect(hasValidGuestDetails({ name: "Alex Guest", email: "guest@example.com" }, true)).toBe(true);
    expect(hasValidGuestDetails({ name: " ", email: "guest@example.com" }, true)).toBe(false);
    expect(hasValidGuestDetails({ name: "Alex Guest", email: "guest@example.com" }, false)).toBe(false);
  });
});
