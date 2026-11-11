#!/bin/sh
set -eu
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=core_password="$CORE_DATABASE_PASSWORD" \
  --set=questions_password="$QUESTIONS_DATABASE_PASSWORD" \
  --set=email_password="$EMAIL_DATABASE_PASSWORD" \
  --file=/opt/aja/roles.sql
