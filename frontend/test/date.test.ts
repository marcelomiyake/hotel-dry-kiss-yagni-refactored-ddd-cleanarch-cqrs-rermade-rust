import { describe, expect, it } from "vitest";
import { addDays, formatDate, formatMoney, getNights, localDateOffset } from "../src/date";

describe("date and money helpers", () => {
  it("formats local dates and adds calendar days", () => {
    expect(addDays("2026-02-27", 3)).toBe("2026-03-02");
    expect(getNights("2026-03-01", "2026-03-05")).toBe(4);
    expect(getNights("2026-03-05", "2026-03-01")).toBe(0);
    expect(localDateOffset(0)).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("uses predictable display formats", () => {
    expect(formatDate("2026-04-09")).toBe("9 Apr 2026");
    expect(formatDate("")).toBe("");
    expect(formatMoney(620)).toContain("620.00");
  });
});
