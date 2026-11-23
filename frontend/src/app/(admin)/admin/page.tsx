import type { Metadata } from "next";
import { DashboardView } from "./DashboardView";

export const metadata: Metadata = { title: "Panel de administración" };

export default function Page() {
  return <DashboardView />;
}
