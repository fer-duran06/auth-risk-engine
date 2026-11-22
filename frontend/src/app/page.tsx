import { redirect } from "next/navigation";

/** La raíz lleva al área de usuario; si no hay sesión, el guard redirige a /login. */
export default function Home() {
  redirect("/me");
}
