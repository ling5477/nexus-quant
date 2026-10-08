FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS build
WORKDIR /source/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# 保持源码相对路径，图表声明和正式包文档读取同一份 canonical 原文。
COPY THIRD_PARTY_NOTICES.md /source/THIRD_PARTY_NOTICES.md
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28.0-alpine
COPY release/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /source/frontend/dist /usr/share/nginx/html
EXPOSE 8080
