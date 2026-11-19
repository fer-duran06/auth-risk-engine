import { notFound } from "next/navigation";
import { Suspense } from "react";

// Página de laboratorio para la práctica SAST. Solo existe si LAB_MODE=true (variable de servidor).
export default function LabXssPage({ searchParams }: PageProps<"/lab/xss">) {
  if (process.env.LAB_MODE !== "true") notFound();

  return (
    <main className="p-6">
      <Suspense fallback={<p>Cargando…</p>}>
        <Saludo searchParams={searchParams} />
      </Suspense>
    </main>
  );
}

async function Saludo({ searchParams }: { searchParams: PageProps<"/lab/xss">["searchParams"] }) {
  const { nombre } = await searchParams;
  const valor = typeof nombre === "string" ? nombre : "";
  // VULNERABILIDAD INTENCIONAL (práctica SAST #5): XSS reflejado (CWE-79)
  return <p dangerouslySetInnerHTML={{ __html: `Hola, ${valor}` }} />;
}
