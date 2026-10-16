export type Row = Record<string, any>;
export interface User {
  id: string;
  username: string;
  displayName: string;
  teamId: string;
  roles: string[];
  permissions: string[];
}
export interface Envelope<T> {
  success: boolean;
  data: T;
  code: string;
  message: string;
}
