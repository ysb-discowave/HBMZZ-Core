#!/usr/bin/env bash
# HBMZZ Core - offline structural checks for the independent reactor module.
# Copyright (C) 2026 HBMZZ contributors
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, version 3.
#
# No gradle, no network, no Minecraft classpath needed: grep/sed/awk only.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REACTOR_DIR="$REPO_ROOT/src/main/java/com/hbmzz/reactor"
NET_FILE="$REACTOR_DIR/ReactorNetworkMessages.java"

failures=0
fail() {
    printf 'FAIL: %s\n' "$*" >&2
    failures=$((failures + 1))
}

if [ ! -d "$REACTOR_DIR" ]; then
    printf 'FAIL: reactor package not found at %s\n' "$REACTOR_DIR" >&2
    exit 1
fi

SOURCES="$(find "$REACTOR_DIR" -type f -name '*.java' | sort)"
if [ -z "$SOURCES" ]; then
    printf 'FAIL: no java sources under %s\n' "$REACTOR_DIR" >&2
    exit 1
fi
SOURCE_COUNT="$(printf '%s\n' "$SOURCES" | wc -l | tr -d ' ')"

# --- 1. required files -------------------------------------------------------
for required in \
    ReactorRegistry.java ReactorState.java ReactorController.java \
    ReactorNetworkMessages.java ReactorTier.java \
    api/ReactorView.java api/ReactorGateway.java
do
    if [ ! -f "$REACTOR_DIR/$required" ]; then
        fail "missing required file: com/hbmzz/reactor/$required"
    fi
done

# --- 2. no cursed-edition dependency ----------------------------------------
# NOTE: the literal "com.hbm." (with a dot after hbm) does not match "com.hbmzz."
CURSED_REF="$(grep -R -n -E 'com\.hbm\.|TileEntityRBMK|RbmkTiming|RbmkCluster|RbmkLayout|RbmkParts' \
    "$REACTOR_DIR" --include='*.java' || true)"
if [ -n "$CURSED_REF" ]; then
    fail "reactor package references cursed-edition code:"
    printf '%s\n' "$CURSED_REF" >&2
fi

# --- 3. import whitelist -----------------------------------------------------
BAD_IMPORTS="$(grep -R -h '^[[:space:]]*import[[:space:]]' "$REACTOR_DIR" --include='*.java' \
    | grep -v -E '^[[:space:]]*import[[:space:]]+(java|net\.minecraft|com\.hbmzz)\.' || true)"
if [ -n "$BAD_IMPORTS" ]; then
    fail "import outside java.* / net.minecraft.* / com.hbmzz.*:"
    printf '%s\n' "$BAD_IMPORTS" >&2
fi

# --- 4. GPL-3.0 header on every java file -----------------------------------
while IFS= read -r src; do
    header="$(head -n 20 "$src")"
    rel="${src#$REPO_ROOT/}"
    if ! printf '%s\n' "$header" | grep -q 'GNU General Public License'; then
        fail "missing GPL header: $rel"
    fi
    if ! printf '%s\n' "$header" | grep -q 'version 3'; then
        fail "GPL header must state version 3: $rel"
    fi
done <<EOF
$SOURCES
EOF

# --- 5. network packet naming <-> registration pairing ----------------------
if [ ! -f "$NET_FILE" ]; then
    fail "missing $NET_FILE"
else
    if ! grep -q 'PROTOCOL_VERSION' "$NET_FILE"; then
        fail 'ReactorNetworkMessages has no PROTOCOL_VERSION'
    fi

    PAIRING="$(awk '
        /^[[:space:]]*public static final class ReactorPacket[A-Za-z0-9_]+/ {
            name = $0
            sub(/.*class /, "", name)
            sub(/[ .{].*/, "", name)
            cls = name
            next
        }
        cls != "" && /public int id\(\)/ { inid = 1; next }
        inid && /return [0-9]+;/ {
            line = $0
            sub(/.*return /, "", line)
            sub(/;.*/, "", line)
            ids[cls] = line
            inid = 0
            next
        }
        /^[[:space:]]*register\(ReactorPacket[A-Za-z0-9_]+\.class,[[:space:]]*[0-9]+\)/ {
            line = $0
            sub(/.*register\(/, "", line)
            sub(/\.class.*/, "", line)
            rname = line
            line = $0
            sub(/.*,[[:space:]]*/, "", line)
            sub(/\).*/, "", line)
            reg[rname] = line
        }
        END {
            for (c in ids) {
                if (!(c in reg)) print "unregistered:" c
            }
            for (c in reg) {
                if (!(c in ids)) print "undeclared:" c
                else if (ids[c] != reg[c])
                    print "id-mismatch:" c " id()=" ids[c] " register=" reg[c]
            }
        }
    ' "$NET_FILE")"
    if [ -n "$PAIRING" ]; then
        fail "network packet naming/registration mismatch:"
        printf '%s\n' "$PAIRING" >&2
    fi

    DECLARED="$(sed -n 's/^[[:space:]]*public static final class \(ReactorPacket[A-Za-z0-9_]*\).*/\1/p' "$NET_FILE" | sort)"
    REGISTERED="$(sed -n 's/^[[:space:]]*register(\(ReactorPacket[A-Za-z0-9_]*\)\.class,.*/\1/p' "$NET_FILE" | sort)"
    if [ -z "$DECLARED" ] || [ -z "$REGISTERED" ]; then
        fail 'no reactor packets declared or registered'
    fi
    DUP_DECL="$(printf '%s\n' "$DECLARED" | uniq -d)"
    DUP_REG="$(printf '%s\n' "$REGISTERED" | uniq -d)"
    if [ -n "$DUP_DECL" ] || [ -n "$DUP_REG" ]; then
        fail "duplicate packet names:"
        printf '%s\n%s\n' "$DUP_DECL" "$DUP_REG" >&2
    fi
fi

if [ "$failures" -ne 0 ]; then
    printf '%d reactor check(s) failed\n' "$failures" >&2
    exit 1
fi
printf 'OK: reactor structural checks passed (%s java files)\n' "$SOURCE_COUNT"
