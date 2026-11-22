import type { ReactNode } from "react";
import { RequireSession } from "@/components/auth/RequireSession";
import { AppLayout } from "@/components/layout/AppLayout";

/** Rutas del área de usuario: requieren sesión. */
export default function ProtectedLayout({ children }: { children: ReactNode }) {
  return (
    <RequireSession>
      <AppLayout>{children}</AppLayout>
    </RequireSession>
  );
}
