# -*- coding: utf-8 -*-
"""Count the 7.2 quest condition/exec types that used to resolve to NONE.

Reads the pinned quest table and writes coverage.json next to this script.
Usage: python tools/quest-coverage-72.py [QuestExcelConfigData.json]
"""
import collections
import hashlib
import json
import os
import sys

CONTENT = [
    "QUEST_CONTENT_QUEST_GLOBAL_VAR_EQUAL",
    "QUEST_CONTENT_QUEST_GLOBAL_VAR_NOT_EQUAL",
    "QUEST_CONTENT_QUEST_GLOBAL_VAR_GREATER",
    "QUEST_CONTENT_ITEM_NUM_EQUAL",
    "QUEST_CONTENT_ITEM_NUM_GREATER",
    "QUEST_CONTENT_PRESENT_AT_SPECIFIC_SCENE",
    "QUEST_CONTENT_LEAVE_SCENE_RANGE",
]
EXECS = [
    "QUEST_EXEC_BATCH_SET_OPEN_STATE",
    "QUEST_EXEC_NOTIFY_DAILY_TASK",
    "QUEST_EXEC_UNLOCK_AVATAR_TEAM",
    "QUEST_EXEC_UNLOCK_AVATAR_TEAM_V2",
    "QUEST_EXEC_UNLOCK_DUNGEON_MIRROR_AVATAR_TEAM",
    "QUEST_EXEC_UNLOCK_MIRROR_AVATAR_TEAM",
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    default = r"resources/ExcelBinOutput/QuestExcelConfigData.json"
    path = sys.argv[1] if len(sys.argv) > 1 else default
    rows = json.load(open(path, encoding="utf-8", errors="ignore"))

    cond = collections.Counter()
    cond_q = collections.defaultdict(set)
    for quest in rows:
        for field, mode in (("finishCond", "finish"), ("failCond", "fail")):
            for c in quest.get(field) or []:
                t = c.get("type")
                if t in CONTENT:
                    cond[(t, mode)] += 1
                    cond_q[t].add(quest.get("subId"))

    exe = collections.Counter()
    exe_q = collections.defaultdict(set)
    for quest in rows:
        for field in ("beginExec", "finishExec", "failExec"):
            for e in quest.get(field) or []:
                t = e.get("type")
                if t in EXECS:
                    exe[t] += 1
                    exe_q[t].add(quest.get("subId"))

    out = {
        "quest_table": path,
        "quest_table_sha256": sha256(path),
        "quest_rows": len(rows),
        "conditions": {
            t: {
                "total": cond[(t, "finish")] + cond[(t, "fail")],
                "finish": cond[(t, "finish")],
                "fail": cond[(t, "fail")],
                "subquests": len(cond_q[t]),
            }
            for t in CONTENT
        },
        "execs": {t: {"total": exe[t], "subquests": len(exe_q[t])} for t in EXECS},
        "totals": {
            "conditions": sum(cond.values()),
            "condition_subquests": len(set().union(*cond_q.values())) if cond_q else 0,
            "execs": sum(exe.values()),
            "exec_subquests": len(set().union(*exe_q.values())) if exe_q else 0,
        },
    }
    dst = os.path.join(os.path.dirname(os.path.abspath(__file__)), "coverage.json")
    with open(dst, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(json.dumps(out["totals"], ensure_ascii=False))
    print("written", dst)


if __name__ == "__main__":
    main()
