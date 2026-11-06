"""One-off conversion of FreeDict's eng-rus.tei (CC BY-SA 3.0, WikDict/DBnary/Wiktionary
sourced) into a compact SQLite asset for on-device lookup. Not part of the app's build --
run once, commit the resulting .db to app/src/main/assets/.
"""
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET

TEI_NS = "{http://www.tei-c.org/ns/1.0}"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
WHITESPACE = re.compile(r"\s+")
COMBINING_ACUTE_ACCENT = "́"
# Some quotes carry raw MediaWiki link markup, e.g. "[[осушить]] [[залп|залпом]]" ->
# "осушить залпом" -- a leftover from the DBnary/WikDict extraction that TEI export didn't
# resolve to plain text.
WIKI_LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\]")
MAX_TRANSLATIONS_PER_WORD = 12


def normalize(text: str) -> str:
    return WHITESPACE.sub(" ", text.strip().lower())


def clean_quote(text: str) -> str:
    resolved = WIKI_LINK.sub(lambda m: m.group(2) or m.group(1), text)
    return WHITESPACE.sub(" ", resolved.strip().replace(COMBINING_ACUTE_ACCENT, ""))


def russian_translations(entry_elem) -> list[str]:
    quotes: list[str] = []
    for cit in entry_elem.iter(TEI_NS + "cit"):
        if cit.get("type") != "trans" or cit.get(XML_LANG) != "ru":
            continue
        for quote in cit.findall(TEI_NS + "quote"):
            if not quote.text:
                continue
            clean = clean_quote(quote.text)
            if clean and clean not in quotes:
                quotes.append(clean)
    return quotes


def main() -> None:
    src, dst = sys.argv[1], sys.argv[2]
    merged: dict[str, list[str]] = {}
    entry_count = 0

    for event, elem in ET.iterparse(src, events=("end",)):
        if elem.tag != TEI_NS + "entry":
            continue
        entry_count += 1
        orth = elem.find(f"{TEI_NS}form/{TEI_NS}orth")
        if orth is not None and orth.text:
            quotes = russian_translations(elem)
            if quotes:
                bucket = merged.setdefault(normalize(orth.text), [])
                for q in quotes:
                    if q not in bucket:
                        bucket.append(q)
        elem.clear()

    conn = sqlite3.connect(dst)
    conn.execute("CREATE TABLE translations (word TEXT PRIMARY KEY, translations TEXT NOT NULL)")
    conn.executemany(
        "INSERT INTO translations VALUES (?, ?)",
        (
            (word, "\t".join(quotes[:MAX_TRANSLATIONS_PER_WORD]))
            for word, quotes in merged.items()
        ),
    )
    conn.commit()
    conn.close()
    print(f"{entry_count} TEI entries processed, {len(merged)} headwords with RU translations")


if __name__ == "__main__":
    main()
