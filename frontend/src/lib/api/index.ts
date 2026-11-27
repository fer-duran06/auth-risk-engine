import { createAdminApi } from "./admin";
import { createAuthApi } from "./auth";
import { API_URL, DEMO_MODE } from "./config";
import { createHttp } from "./http";
import { createNotificationsApi } from "./notifications";
import { simulatedIp } from "./simulated-ip";

const http = createHttp({ baseUrl: API_URL, demoMode: DEMO_MODE, getSimulatedIp: simulatedIp.get });

/** Instancias únicas del cliente, configuradas con las variables NEXT_PUBLIC_*. */
export const authApi = createAuthApi(http);
export const adminApi = createAdminApi(http);
export const notificationsApi = createNotificationsApi(http);

export type {
  AccessToken,
  AuthApi,
  BreachStatus,
  BreachStatusResult,
  CurrentUser,
  LoginResult,
  MeResult,
  RegisterResult,
  TotpSetup,
  TotpStatus,
  VerifyResult,
} from "./auth";
export type { AppNotification, MarkReadResult, NotificationsApi, NotificationsResult } from "./notifications";
export type {
  AdminEvent,
  AdminResult,
  AdminStats,
  EventFilter,
  EventPage,
  IpActivity,
  RiskConfig,
  RuleConfig,
  Zone,
} from "./admin";
export { ZONES } from "./admin";
export { API_URL, DEMO_MODE } from "./config";
