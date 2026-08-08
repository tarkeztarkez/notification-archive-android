#!/usr/bin/env bash
set -euo pipefail
token="$(pass show notification-archive/api-token | head -n1)"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${token}" \
  https://notifications.marcinszyda.com/api/v1/sync/status
printf '\n'

