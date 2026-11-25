#!/bin/sh
set -eu
if [ "${DEMO_SEED_ENABLED:-true}" != "true" ]; then
  echo 'Demo seed disabled by DEMO_SEED_ENABLED'
  exit 0
fi
export PGPASSWORD="$CORE_DATABASE_PASSWORD"
pdf=/opt/aja/demo/assets/proporcoes.pdf
png=/opt/aja/demo/assets/grupos.png
rendered=/tmp/aja-demo-core.sql
sed -e "s|__DEMO_PDF_BASE64__|$(base64 "$pdf" | tr -d '\n')|g" -e "s|__DEMO_PDF_SIZE__|$(wc -c < "$pdf" | tr -d ' ')|g" -e "s|__DEMO_PDF_SHA256__|$(sha256sum "$pdf" | cut -d' ' -f1)|g" -e "s|__DEMO_PNG_BASE64__|$(base64 "$png" | tr -d '\n')|g" -e "s|__DEMO_PNG_SIZE__|$(wc -c < "$png" | tr -d ' ')|g" -e "s|__DEMO_PNG_SHA256__|$(sha256sum "$png" | cut -d' ' -f1)|g" /opt/aja/demo/core.sql > "$rendered"
psql -X -v ON_ERROR_STOP=1 -h postgres -U core -d aja -f "$rendered"
rm -f "$rendered"
export PGPASSWORD="$QUESTIONS_DATABASE_PASSWORD"
psql -X -v ON_ERROR_STOP=1 -h postgres -U questions -d aja -f /opt/aja/demo/questions.sql
