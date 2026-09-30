import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "../src/api";

afterEach(() => vi.unstubAllGlobals());

describe("api", () => {
  it("sends JSON and decodes successful responses", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api<{ ok: boolean }>("/api/example", { method: "POST", body: "{}" })).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith("/api/example", expect.objectContaining({
      headers: { "Content-Type": "application/json" },
    }));
  });

  it("returns undefined for no-content responses and wraps API errors", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: "not_available", message: "Try again" }), { status: 409 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(api<void>("/api/no-content")).resolves.toBeUndefined();
    await expect(api("/api/conflict")).rejects.toMatchObject({
      name: "ApiError",
      status: 409,
      code: "not_available",
      message: "Try again",
    });
  });

  it("uses safe defaults when the error body is not JSON", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("oops", { status: 503 })));
    await expect(api("/api/down")).rejects.toBeInstanceOf(ApiError);
    await expect(api("/api/down")).rejects.toMatchObject({ code: "request_failed", message: "The request could not be completed." });
  });

  it("rejects requests outside this site's API path", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);

    await expect(api("https://example.com/api/bookings")).rejects.toThrow("Requests must target this site's API.");
    await expect(api("/api/../admin/keys")).rejects.toThrow("Requests must target this site's API.");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
