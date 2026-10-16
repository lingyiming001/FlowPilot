import { create } from "zustand";
import type { User } from "../types";
interface Auth {
  token: string | null;
  user: User | null;
  login: (token: string, user: User) => void;
  logout: () => void;
}
// Session storage avoids persisting tokens beyond the browser session; no credentials in URLs.
export const useAuth = create<Auth>((set) => ({
  token: sessionStorage.getItem("flowpilot-token"),
  user: null,
  login: (token, user) => {
    sessionStorage.setItem("flowpilot-token", token);
    set({ token, user });
  },
  logout: () => {
    sessionStorage.removeItem("flowpilot-token");
    set({ token: null, user: null });
  },
}));
