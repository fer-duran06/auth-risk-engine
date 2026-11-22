import type { Metadata } from "next";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { RegisterForm } from "./RegisterForm";

export const metadata: Metadata = { title: "Crear cuenta" };

export default function RegisterPage() {
  return (
    <AuthLayout width="lg">
      <RegisterForm />
    </AuthLayout>
  );
}
