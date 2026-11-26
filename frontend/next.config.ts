import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // En despliegue el navegador llama a /api en el mismo origen del front y Next lo reenvía al
  // backend por red privada. Así la cookie de refresh (SameSite=Strict) sigue siendo same-site.
  async rewrites() {
    const backend = process.env.BACKEND_INTERNAL_URL;
    return backend ? [{ source: "/api/:path*", destination: `${backend}/api/:path*` }] : [];
  },
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
