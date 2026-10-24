#!/usr/bin/env bash
# Runs one photo through the scan pipeline against a running backend:
# login -> presign -> upload -> create scan -> poll -> print result.
#
# Usage: scripts/scan-photo.sh path/to/meal.jpg [email] [password]
# Needs curl and python. The backend needs GEMINI_API_KEY (default provider)
# or, with VISION_PROVIDER=claude, ANTHROPIC_API_KEY for real results.
set -euo pipefail

PHOTO="${1:?usage: scan-photo.sh photo.jpg [email] [password]}"
EMAIL="${2:-demo@foodscan.dev}"
PASSWORD="${3:-demo-password-1}"
BASE="${BASE_URL:-http://localhost:8080}"

# Prepare the photo the way the app does: JPEG, longest side at most 1568 px.
# This also accepts formats the backend doesn't (AVIF, HEIC, ...). Needs Pillow.
CONVERTED="$(mktemp --suffix=.jpg)"
trap 'rm -f "$CONVERTED"' EXIT
python -I -c '
import sys
from PIL import Image, ImageOps
im = ImageOps.exif_transpose(Image.open(sys.argv[1])).convert("RGB")
im.thumbnail((1568, 1568))
im.save(sys.argv[2], "JPEG", quality=80)
' "$PHOTO" "$CONVERTED"
PHOTO="$CONVERTED"
TYPE=image/jpeg

field() { python -I -c "import json,sys; print(json.load(sys.stdin)$1)"; }

TOKEN=$(curl -sf -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | field "['accessToken']")

UPLOAD=$(curl -sf -X POST "$BASE/api/uploads/presign" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d "{\"contentType\":\"$TYPE\"}")
KEY=$(echo "$UPLOAD" | field "['key']")
URL=$(echo "$UPLOAD" | field "['url']")
NEEDS_AUTH=$(echo "$UPLOAD" | field "['requiresAuth']")

AUTH=()
[ "$NEEDS_AUTH" = "True" ] && AUTH=(-H "Authorization: Bearer $TOKEN")
curl -sf -X PUT "$URL" "${AUTH[@]}" -H "Content-Type: $TYPE" --data-binary "@$PHOTO"

SCAN_ID=$(curl -sf -X POST "$BASE/api/scans" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d "{\"imageKey\":\"$KEY\"}" | field "['id']")
echo "Scan $SCAN_ID created, analyzing..." >&2

for _ in $(seq 1 120); do
  RESULT=$(curl -sf "$BASE/api/scans/$SCAN_ID" -H "Authorization: Bearer $TOKEN")
  STATUS=$(echo "$RESULT" | field "['status']")
  [ "$STATUS" != "processing" ] && break
  sleep 1
done

SCAN_JSON="$RESULT" python -I - <<'PY'
import json, os
s = json.loads(os.environ["SCAN_JSON"])
print("status:", s["status"], " meal:", s.get("mealName"))
if s.get("error"):
    print("error:", s["error"])
if s.get("notes"):
    print("notes:", s["notes"])
for i in s.get("items", []):
    n = i["nutrients"]
    line = "- {}: {} g ({}-{}) -> {} [{}] | {} kcal P{} C{} F{}".format(
        i["name"], i["grams"], i["gramsLow"], i["gramsHigh"], i["food"]["name"], i["source"],
        n["kcal"], n["proteinG"], n["carbsG"], n["fatG"])
    if i["flags"]:
        line += "  flags=" + ",".join(i["flags"])
    print(line)
t = s.get("totals") or {}
print("TOTAL: {} kcal  P{} C{} F{}".format(t.get("kcal"), t.get("proteinG"), t.get("carbsG"), t.get("fatG")))
PY
