FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS build
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
WORKDIR /source/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
# 保持源码相对路径，图表声明和正式包文档读取同一份 canonical 原文。
COPY THIRD_PARTY_NOTICES.md /source/THIRD_PARTY_NOTICES.md
COPY DISCLAIMER.md /source/DISCLAIMER.md
RUN case "$NQ_DISTRIBUTION" in NQ_SELF_HOSTED) distribution="SELF_HOSTED" ;; NQ_PUBLIC_DEMO) distribution="PUBLIC_DEMO" ;; *) exit 64 ;; esac \
    && VITE_DISTRIBUTION="$distribution" npm run build
FROM nginxinc/nginx-unprivileged:1.28.0-alpine
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
LABEL com.nexusquant.distribution=$NQ_DISTRIBUTION
COPY release/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 release/docker/verify-distribution.sh /docker-entrypoint.d/40-verify-distribution.sh
COPY --from=build /source/frontend/dist /usr/share/nginx/html
EXPOSE 8080
