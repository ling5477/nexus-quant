FROM --platform=$BUILDPLATFORM node:24.22.1-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205 AS build
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
FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
ARG NQ_DISTRIBUTION=NQ_SELF_HOSTED
LABEL com.nexusquant.distribution=$NQ_DISTRIBUTION
COPY release/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 release/docker/verify-distribution.sh /docker-entrypoint.d/40-verify-distribution.sh
COPY --from=build /source/frontend/dist /usr/share/nginx/html
EXPOSE 8080
