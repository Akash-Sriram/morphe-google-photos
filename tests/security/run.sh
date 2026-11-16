#!/bin/sh
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
test_classes=$(mktemp -d "${TMPDIR:-/tmp}/morphe-security-tests.XXXXXX")
trap 'rm -rf "$test_classes"' EXIT HUP INT TERM
source_dir="$repo_dir/extensions/shared/library/src/main/java/app/morphe/extension/shared"
javac --release 17 -d "$test_classes" \
    "$source_dir/requests/TrustedGoogleMedia.java" \
    "$source_dir/requests/ExportSafety.java" \
    "$source_dir/diagnostics/DiagnosticRedactor.java" \
    "$repo_dir/tests/security/SecurityRegressionTest.java"
java -cp "$test_classes" SecurityRegressionTest
