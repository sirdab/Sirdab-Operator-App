#!/usr/bin/env bash
# Point every attached device at the dev stack on this machine.
#
# The app is built against 127.0.0.1, which on a phone means the phone. These
# reverse tunnels are what make it mean this Mac instead, over USB, on the
# emulator and on hardware alike.
#
# 10.0.2.2 would do the same job on an emulator and nowhere else, and it was
# never enough on its own: Supabase mints photo upload URLs pointing at
# 127.0.0.1:54321, so the tunnel is needed either way.
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"

"$ADB" devices | awk 'NR>1 && $2=="device" {print $1}' | while read -r serial; do
  "$ADB" -s "$serial" reverse tcp:4400 tcp:4400 >/dev/null
  "$ADB" -s "$serial" reverse tcp:54321 tcp:54321 >/dev/null
  echo "$serial -> 4400, 54321"
done
