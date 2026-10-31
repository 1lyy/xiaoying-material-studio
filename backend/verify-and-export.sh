#!/bin/sh
# Run the complete Maven gate and copy diagnostics to the host-mounted folder.
set -u

report_dir=/workspace/test-output
mkdir -p "$report_dir"
log_file="$report_dir/maven-verify.log"

mvn -B -ntp verify > "$log_file" 2>&1
result=$?

if [ -d target/surefire-reports ]; then
  cp target/surefire-reports/*.txt "$report_dir"/ 2>/dev/null || true
  cp target/surefire-reports/*.xml "$report_dir"/ 2>/dev/null || true
fi

printf 'Maven verify exit code: %s\n' "$result"
grep -E '^\[ERROR\] (Tests run:|.*<<< (FAILURE|ERROR)!|Failures:|Errors:)' "$log_file" | tail -n 80 || true
printf 'Full log and surefire reports: %s\n' "$report_dir"
exit "$result"
