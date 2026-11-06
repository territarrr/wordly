#!/usr/bin/env python3
"""Fills missing "Транскрипция"/"Перевод" cells in the Wordly word-list Google Sheet.

Translation: looked up offline in app/src/main/assets/freedict_eng_rus.db (the same DB the
app itself ships and queries via translate/FreeDictTranslator.kt -- see
scripts/build_freedict_db.py for how it was built). No network calls at all.

Transcription: looked up on en.wiktionary.org, but batched -- MediaWiki's `action=query`
accepts up to BATCH_SIZE page titles per request (`prop=revisions&rvprop=content`), fetching
raw wikitext for a whole batch of words in one HTTP call, instead of one call per word (or,
as the app's own WiktionaryApi does on-device, two calls per word). The same batched wikitext
is also used as a fallback translation source for words FreeDict didn't have.

Usage:
    python3 fill_sheet_gaps.py <input.csv> <output.csv>

<input.csv> is a CSV export of the sheet (header: Слово,Транскрипция,Перевод). Fetch one with:
    curl -L "https://docs.google.com/spreadsheets/d/<ID>/export?format=csv&gid=0" -o input.csv
"""
from __future__ import annotations

import csv
import json
import re
import sqlite3
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

FREEDICT_DB = "app/src/main/assets/freedict_eng_rus.db"
WIKTIONARY_API = "https://en.wiktionary.org/w/api.php"
BATCH_SIZE = 50
USER_AGENT = "WordlyApp/1.0 (Android vocabulary learning app; one-off sheet maintenance script)"

WHITESPACE = re.compile(r"\s+")


def normalize(word: str) -> str:
    return WHITESPACE.sub(" ", word.strip().lower())


# ---- FreeDict: offline translation, zero requests ----


def freedict_lookup(words, db_path=FREEDICT_DB):
    conn = sqlite3.connect(db_path)
    result = {}
    for w in words:
        row = conn.execute(
            "SELECT translations FROM translations WHERE word = ?", (normalize(w),)
        ).fetchone()
        if row:
            result[w] = row[0].split("\t")
    conn.close()
    return result


# ---- Wiktionary: batched wikitext fetch, used for transcription + fallback translation ----

ENGLISH_SECTION = re.compile(r"(?m)^==\s*English\s*==\s*$(.*?)(?=^==[^=]|\Z)", re.S)
IPA_TEMPLATE = re.compile(r"\{\{IPA\|en\|([^}]*)\}\}")
IPA_TOKEN = re.compile(r"(/[^/{}|]+/|\[[^\]{}|]+\])")
TRANSLATIONS_SECTION = re.compile(r"(?m)^====?\s*Translations\s*====?\s*$(.*?)(?=^===|^==[^=]|\Z)", re.S)
RU_TRANSLATION = re.compile(r"\{\{t[+\-]?\w*\|ru\|([^|}]+)")


def wiktionary_batch_wikitext(titles):
    """[title, ...] -> {title: wikitext}, one HTTP request per BATCH_SIZE titles."""
    out = {}
    for i in range(0, len(titles), BATCH_SIZE):
        chunk = titles[i : i + BATCH_SIZE]
        params = {
            "action": "query",
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
            "format": "json",
            "formatversion": "2",
            "redirects": "1",
            "titles": "|".join(chunk),
        }
        url = WIKTIONARY_API + "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.load(resp)
        except urllib.error.URLError as e:
            print(f"  ! batch request failed ({e}); skipping {len(chunk)} words", file=sys.stderr)
            continue
        redirects = {r["from"]: r["to"] for r in data.get("query", {}).get("redirects", [])}
        for page in data.get("query", {}).get("pages", []):
            title = page.get("title")
            revisions = page.get("revisions")
            if not revisions:
                continue
            content = revisions[0].get("slots", {}).get("main", {}).get("content", "")
            original = next((frm for frm, to in redirects.items() if to == title), title)
            out[original] = content
        print(f"  batch {i // BATCH_SIZE + 1}: {len(chunk)} titles in 1 request", file=sys.stderr)
        time.sleep(0.3)
    return out


def extract_ipa(wikitext: str) -> str | None:
    m = ENGLISH_SECTION.search(wikitext)
    if not m:
        return None
    english = m.group(1)
    for tmpl_match in IPA_TEMPLATE.finditer(english):
        for token_match in IPA_TOKEN.finditer(tmpl_match.group(1)):
            return token_match.group(1)
    return None


def extract_translations(wikitext: str) -> list[str]:
    m = ENGLISH_SECTION.search(wikitext)
    if not m:
        return []
    english = m.group(1)
    seen = []
    for section_match in TRANSLATIONS_SECTION.finditer(english):
        for t_match in RU_TRANSLATION.finditer(section_match.group(1)):
            word = t_match.group(1).strip()
            if word and word not in seen:
                seen.append(word)
    return seen


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    input_path, output_path = sys.argv[1], sys.argv[2]

    with open(input_path, newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    header, data_rows = rows[0], rows[1:]
    data_rows = [r + [""] * (3 - len(r)) for r in data_rows]

    needs_translation = [r[0] for r in data_rows if not r[2].strip()]
    needs_ipa = [r[0] for r in data_rows if not r[1].strip()]

    print(f"{len(data_rows)} words total; {len(needs_translation)} missing translation, "
          f"{len(needs_ipa)} missing transcription", file=sys.stderr)

    freedict = freedict_lookup(needs_translation)
    print(f"FreeDict (offline, 0 requests) filled {len(freedict)}/{len(needs_translation)} translations",
          file=sys.stderr)

    still_missing_translation = [w for w in needs_translation if w not in freedict]
    wiktionary_words = sorted(set(needs_ipa) | set(still_missing_translation))
    print(f"Fetching {len(wiktionary_words)} words from Wiktionary in batches of {BATCH_SIZE}...",
          file=sys.stderr)
    wikitext_by_word = wiktionary_batch_wikitext(wiktionary_words)

    filled_ipa = filled_translation = 0
    for row in data_rows:
        word, ipa, translation = row
        wikitext = wikitext_by_word.get(word)
        if not ipa.strip() and wikitext:
            found = extract_ipa(wikitext)
            if found:
                row[1] = found
                filled_ipa += 1
        if not translation.strip():
            if word in freedict:
                row[2] = ", ".join(freedict[word])
                filled_translation += 1
            elif wikitext:
                found = extract_translations(wikitext)
                if found:
                    row[2] = ", ".join(found)
                    filled_translation += 1

    still_no_ipa = sum(1 for r in data_rows if not r[1].strip())
    still_no_translation = sum(1 for r in data_rows if not r[2].strip())
    print(f"Filled {filled_ipa} transcriptions, {filled_translation} translations "
          f"({sum(1 for w in freedict)} from FreeDict)", file=sys.stderr)
    print(f"Still missing after this pass: {still_no_ipa} transcriptions, "
          f"{still_no_translation} translations", file=sys.stderr)

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        writer.writerows(data_rows)
    print(f"Wrote {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
