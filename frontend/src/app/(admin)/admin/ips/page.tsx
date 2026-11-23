import type { Metadata } from "next";
import { IpsView } from "./IpsView";

export const metadata: Metadata = { title: "IPs recientes" };

export default function Page() {
  return <IpsView />;
}
