interface ApiErrorBody {
  code?: string;
  message?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const requestUrl = new URL(path, window.location.origin);
  if (requestUrl.origin !== window.location.origin || !requestUrl.pathname.startsWith("/api/")) {
    throw new Error("Requests must target this site's API.");
  }
  const response = await fetch(`${requestUrl.pathname}${requestUrl.search}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...init.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch((): ApiErrorBody => ({}));
    throw new ApiError(response.status, body.code ?? "request_failed", body.message ?? "The request could not be completed.");
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}
