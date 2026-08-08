#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime, timedelta


BASE_URL = "https://notifications.marcinszyda.com"
PASS_ENTRY = "notification-archive/api-token"
KIND_HINTS = {
    "payments": ("bank", "pko", "iko", "zen", "revolut", "blik", "payment", "płat"),
    "messaging": ("telegram", "whatsapp", "messenger", "signal", "beeper", "message", "wiadomo"),
}


def token() -> str:
    result = subprocess.run(
        ["pass", "show", PASS_ENTRY], check=True, capture_output=True, text=True
    )
    return result.stdout.splitlines()[0].strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Read the private phone notification archive")
    parser.add_argument("--since-hours", type=float, default=24)
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--query")
    parser.add_argument("--kind", choices=sorted(KIND_HINTS))
    parser.add_argument("--package")
    parser.add_argument("--event-type", choices=("posted", "updated", "removed"))
    args = parser.parse_args()
    if not 1 <= args.limit <= 200 or args.since_hours <= 0:
        parser.error("--limit must be 1..200 and --since-hours must be positive")

    since = datetime.now(UTC) - timedelta(hours=args.since_hours)
    params = {"since": since.isoformat(), "limit": 200 if args.kind else args.limit}
    if args.query:
        params["q"] = args.query
    if args.package:
        params["package_name"] = args.package
    if args.event_type:
        params["event_type"] = args.event_type
    request = urllib.request.Request(
        f"{BASE_URL}/api/v1/notifications?{urllib.parse.urlencode(params)}",
        headers={
            "Authorization": f"Bearer {token()}",
            "Accept": "application/json",
            "User-Agent": "HermesPhoneNotifications/1.0",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            rows = json.load(response)
    except (urllib.error.URLError, subprocess.CalledProcessError) as error:
        print(f"notification archive request failed: {error}", file=sys.stderr)
        return 1

    if args.kind:
        hints = KIND_HINTS[args.kind]
        rows = [
            row for row in rows
            if any(hint in " ".join(str(row.get(key) or "") for key in
                ("package_name", "app_name", "title", "body")).lower() for hint in hints)
        ][: args.limit]
    else:
        rows = rows[: args.limit]
    json.dump(rows, sys.stdout, ensure_ascii=False, indent=2, default=str)
    print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
