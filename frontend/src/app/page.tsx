import ApiStatus from "@/components/ApiStatus";

export default function Home() {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <div className="w-full max-w-md rounded-2xl border border-borde bg-tarjeta p-8 shadow-lg shadow-black/30">
        <h1 className="text-3xl font-semibold tracking-tight">
          Auth<span className="text-acento">Shield</span>
        </h1>
        <p className="mt-2 text-sm text-texto-suave">
          Motor de autenticación adaptativa basado en riesgo.
        </p>
        <div className="mt-8">
          <ApiStatus />
        </div>
      </div>
    </main>
  );
}
