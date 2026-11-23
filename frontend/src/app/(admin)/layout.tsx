import type { ReactNode } from "react";
import { RequireSession } from "@/components/auth/RequireSession";
import { AdminLayout } from "@/components/layout/AdminLayout";

/** Panel de administración: exige sesión y rol ADMIN (la autorización real la hace el backend). */
export default function AdminGroupLayout({ children }: { children: ReactNode }) {
  return (
    <RequireSession>
      <AdminLayout>{children}</AdminLayout>
    </RequireSession>
  );
}
