# 7.2.0 data patch: avatar name hashes + chapter hashes + missing TextMap entries

`resources/` (game data) is not tracked by this repository, so the fixes below ship as a
patch you apply to **your own** resources copy. Nothing here touches the server code.

## What was wrong

`AvatarExcelConfigData.nameTextMapHash` and the two chapter hashes in
`ChapterExcelConfigData` (`chapterNumTextMapHash`, `chapterTitleTextMapHash`) are just
**keys into `TextMap/TextMapCHS.json`**. When the key is absent, the client gets an empty
string — avatar names and chapter titles render blank.

On the 7.2.0 resource set used here, 154 avatar hashes and 92 chapter hashes are absent from
`TextMapCHS.json`, so names/titles are blank. The patch replaces them with keys that do exist.

## Evidence (reproducible)

```
$ python tools/data-patch-7.2.0/validate_data_patch.py <resources> --expect tools/data-patch-7.2.0/expected-report.json
{
  "textmap_entries": 620459,
  "avatar":  { "entries": 154, "fields": 154,
               "old_hash_resolvable_in_textmap": 0,   "new_hash_resolvable_in_textmap": 154 },
  "chapter": { "chapters": 46, "fields": 92,
               "old_hash_resolvable_in_textmap": 0,   "new_hash_resolvable_in_textmap": 92 },
  "textmap_added": { "entries": 22, "already_present": 22 }
}
```

* `old_hash_resolvable_in_textmap = 0` — none of the current keys resolve to text: this is the bug.
* `new_hash_resolvable_in_textmap = fields` — every key the patch writes resolves to text.
* samples: avatar `10000002` old `1006042610` → *(empty)*, new `3733770008` → `神里绫华`;
  chapter `1001` old `988250` → *(empty)*, new `3424465551` → `序章 第一幕`.

## Contents

| file | meaning |
|---|---|
| `avatar_fix.json` | 154 avatar ids → `nameTextMapHash` old/new |
| `chapter_fix.json` | 46 chapter ids → `chapterNumTextMapHash` / `chapterTitleTextMapHash` old/new |
| `textmap_added.json` | 22 TextMap keys that the server-side table needs but the client TextMap lacks |
| `apply_data_patch.py` | applies the three files to a resources directory |
| `validate_data_patch.py` | recomputes the numbers above; `--expect` fails on mismatch |
| `expected-report.json` | the invariants any correct application must satisfy |

## Usage

```
python tools/data-patch-7.2.0/validate_data_patch.py <resources>          # 先看现状
python tools/data-patch-7.2.0/apply_data_patch.py   <resources> --dry    # 预演
python tools/data-patch-7.2.0/apply_data_patch.py   <resources>          # 应用
python tools/data-patch-7.2.0/validate_data_patch.py <resources> --expect tools/data-patch-7.2.0/expected-report.json
```

## Safety

* writing a hash that does not resolve to text is skipped and reported (the script checks the
  merged TextMap first), so a wrong patch cannot blank a name that currently works;
* every target file is backed up as `<file>.bak_<timestamp>` before being written;
* `--dry` never writes;
* re-running is idempotent — an already-applied row is left as is.

## Scope

Data only. Server-side quest logic is a separate change (see the quest conditions/execs PR).
