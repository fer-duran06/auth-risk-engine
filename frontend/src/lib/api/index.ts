import { createAuthApi } from "./auth";
import { API_URL, DEMO_MODE } from "./config";
import { createHttp } from "./http";
import { simulatedIp } from "./simulated-ip";

/** Instancia única del cliente, configurada con las variables NEXT_PUBLIC_*. */
export const authApi = createAuthApi(
  createHttp({ baseUrl: API_URL, demoMode: DEMO_MODE, getSimulatedIp: simulatedIp.get }),
);

export type {
  AccessToken,
  AuthApi,
  CurrentUser,
  LoginResult,
  MeResult,
  RegisterResult,
  TotpSetup,
  TotpStatus,
  VerifyResult,
} from "./auth";
export { API_URL, DEMO_MODE } from "./config";
