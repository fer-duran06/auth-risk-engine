import type { Metadata } from "next";
import { SecurityView } from "./SecurityView";

export const metadata: Metadata = { title: "Seguridad de la cuenta" };

export default function SecurityPage() {
  return <SecurityView />;
}
