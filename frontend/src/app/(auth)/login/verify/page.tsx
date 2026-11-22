import type { Metadata } from "next";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { VerifyForm } from "./VerifyForm";

export const metadata: Metadata = { title: "Verificación en dos pasos" };

export default function VerifyPage() {
  return (
    <AuthLayout>
      <VerifyForm />
    </AuthLayout>
  );
}
