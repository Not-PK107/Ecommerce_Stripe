export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || " ")
  .replace(/\/$/, "");

export async function apiRequest(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      credentials: "include",
      ...options,
      headers: {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new Error(
      `Could not connect to the shop API at ${API_BASE_URL}. Make sure the Spring Boot backend is running.`,
    );
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.text();
      if (body) {
        try {
          const parsed = JSON.parse(body);
          message = parsed.message || parsed.error || body;
        } catch {
          message = body;
        }
      }
    } catch {
      // Keep the HTTP status message when the response body cannot be read.
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}
