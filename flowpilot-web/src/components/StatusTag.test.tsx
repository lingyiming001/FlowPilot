import { describe, it, expect } from "vitest";
import { labels, actionLabel } from "./StatusTag";
describe("workflow vocabulary", () => {
  it("covers every persisted workflow state", () => {
    for (const state of [
      "RECEIVED",
      "ANALYZING",
      "QUERYING_TOOLS",
      "GENERATING_PROPOSAL",
      "WAITING_APPROVAL",
      "APPROVED",
      "REJECTED",
      "EXECUTING",
      "COMPLETED",
      "FAILED",
      "RETRY_PENDING",
      "MANUAL_REVIEW",
      "CANCELLED",
    ])
      expect(labels[state]).toBeTruthy();
  });
  it("distinguishes refund authorization from sending a message", () => {
    expect(actionLabel.requestRefund).not.toBe(actionLabel.sendCustomerEmail);
  });
});
