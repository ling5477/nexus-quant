#!/bin/sh
set -eu
[ "$(uname -s)" = Darwin ] || { printf '%s\n' 'This installer entry point requires macOS.' >&2; exit 1; }
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec sh "$script_dir/nexusquant.sh" install "$@"
