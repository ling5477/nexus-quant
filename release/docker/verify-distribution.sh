#!/bin/sh
set -eu

# 入口先核对后端实际制品身份；环境变量不能把静态前端提升为另一发行物。
expected=$(cat /usr/share/nginx/html/distribution.json)
case "$expected" in
    '{"distribution":"NQ_SELF_HOSTED"}'|'{"distribution":"NQ_PUBLIC_DEMO"}') ;;
    *) echo 'FRONTEND_DISTRIBUTION_IDENTITY_INVALID' >&2; exit 1 ;;
esac
attempt=0
while [ "$attempt" -lt 10 ]; do
    actual=$(wget -q -T 3 -O - http://backend:8080/api/distribution 2>/dev/null || true)
    if [ -n "$actual" ]; then
        [ "$actual" = "$expected" ] || { echo 'FRONTEND_BACKEND_DISTRIBUTION_MISMATCH' >&2; exit 1; }
        exit 0
    fi
    attempt=$((attempt + 1))
    sleep 1
done
echo 'BACKEND_DISTRIBUTION_UNAVAILABLE' >&2
exit 1
