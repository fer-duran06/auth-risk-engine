import type { Metadata } from "next";
import { MeView } from "./MeView";

export const metadata: Metadata = { title: "Mi cuenta" };

export default function MePage() {
  return <MeView />;
}
