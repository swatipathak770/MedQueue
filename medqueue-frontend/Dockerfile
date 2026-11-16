FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL=
ARG VITE_WS_URL=
ARG MEDQUEUE_PRODUCTION_BUILD=false
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL
RUN npm run build
RUN if [ "$MEDQUEUE_PRODUCTION_BUILD" = "true" ]; then \
      test -z "$VITE_API_URL" && test -z "$VITE_WS_URL" || { echo "Production builds require empty VITE_API_URL and VITE_WS_URL" >&2; exit 1; }; \
      if grep -R -E 'https?://(localhost|127\.0\.0\.1)(:[0-9]+)?' /app/dist; then \
        echo "Production bundle contains a localhost URL" >&2; exit 1; \
      fi; \
    fi

FROM nginx:1.30.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
