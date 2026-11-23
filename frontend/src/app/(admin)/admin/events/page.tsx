import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingCard } from "@/components/admin/AdminPage";
import { EventsView } from "./EventsView";

export const metadata: Metadata = { title: "Eventos de login" };

/** useSearchParams exige un límite de Suspense para el prerender estático. */
export default function Page() {
  return (
    <Suspense fallback={<LoadingCard label="Cargando eventos…" />}>
      <EventsView />
    </Suspense>
  );
}
