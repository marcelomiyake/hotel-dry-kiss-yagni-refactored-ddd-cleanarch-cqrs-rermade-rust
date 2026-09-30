import type { GuestDetails, SearchFormValues } from "../types";

export function validateSearch(values: SearchFormValues): string | null {
  if (!values.destination.trim()) {
    return "Enter a destination to search.";
  }
  const checkIn = new Date(`${values.checkIn}T12:00:00`);
  const checkOut = new Date(`${values.checkOut}T12:00:00`);
  if (!values.checkIn || !values.checkOut || checkOut <= checkIn) {
    return "Choose a check-out date after your check-in date.";
  }
  return null;
}

export function isValidEmail(email: string): boolean {
  const separator = email.indexOf("@");
  const domain = email.slice(separator + 1);
  return separator > 0 && separator === email.lastIndexOf("@") && !email.includes(" ")
    && domain.includes(".") && !domain.startsWith(".") && !domain.endsWith(".");
}

export function hasValidGuestDetails(guest: GuestDetails, policyAccepted: boolean): boolean {
  return Boolean(guest.name.trim()) && isValidEmail(guest.email) && policyAccepted;
}
