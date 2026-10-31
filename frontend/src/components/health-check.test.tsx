import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HealthCheck } from "./health-check";

afterEach(() => {
  vi.unstubAllGlobals();
});

it("reports Java API health via the same-origin route", async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: "UP" }) });
  vi.stubGlobal("fetch", fetchMock);
  render(<HealthCheck />);
  fireEvent.click(screen.getByRole("button", { name: "重新检查" }));
  await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("Java API 可用"));
  expect(fetchMock).toHaveBeenCalledWith("/api/health", { cache: "no-store" });
});
