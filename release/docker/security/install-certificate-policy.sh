#!/bin/sh
set -eu

# 只接受已核对的供应商原始字节，不从网络下载或现场拼接证书指纹。
policy_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
policy="$policy_dir/blocked.certs"
expected=2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10
: "${JAVA_HOME:?JAVA_HOME must identify the build or runtime JDK}"
grep -Eq '^JAVA_VERSION="21([.+"]|$)' "$JAVA_HOME/release" || {
    echo 'Certificate policy requires Java 21' >&2
    exit 1
}
printf '%s  %s\n' "$expected" "$policy" | sha256sum --check --status
target="$JAVA_HOME/lib/security/blocked.certs"
test -f "$target"
grep -qx 'Algorithm=SHA-256' "$target"

# 后续 JDK 可能新增其他阻断项；遇到未知条目必须停止，不能回退其安全策略。
while IFS= read -r entry || test -n "$entry"; do
    case "$entry" in
        ''|'#'*|'Algorithm=SHA-256') continue ;;
    esac
    grep -Fqx -- "$entry" "$policy" || {
        echo 'Existing JDK certificate blocks are not a subset of the pinned policy' >&2
        exit 1
    }
done < "$target"

temporary=$(mktemp "$JAVA_HOME/lib/security/blocked.certs.nq.XXXXXX")
trap 'rm -f -- "$temporary"' EXIT HUP INT TERM
cp -- "$policy" "$temporary"
chmod 644 "$temporary"
mv -f -- "$temporary" "$target"
printf '%s  %s\n' "$expected" "$target" | sha256sum --check --status
echo "Java certificate policy installed: sha256:$expected"
