FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Estas variables se incrustan al compilar (Railway las pasa como build args).
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_DEMO_MODE
ARG BACKEND_INTERNAL_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_DEMO_MODE=$NEXT_PUBLIC_DEMO_MODE \
    BACKEND_INTERNAL_URL=$BACKEND_INTERNAL_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app ./
USER node
EXPOSE 3000
CMD ["sh","-c","npm start -- -p ${PORT:-3000} -H 0.0.0.0"]
