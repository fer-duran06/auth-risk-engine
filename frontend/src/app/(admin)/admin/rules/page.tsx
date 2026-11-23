import type { Metadata } from "next";
import { RulesView } from "./RulesView";

export const metadata: Metadata = { title: "Reglas y umbrales" };

export default function Page() {
  return <RulesView />;
}
