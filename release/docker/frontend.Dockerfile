FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS build
WORKDIR /source
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28.0-alpine
COPY release/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/dist /usr/share/nginx/html
EXPOSE 8080
