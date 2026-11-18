#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验 7.2.0 数据补丁的必要性与应用结果。

用法：
    python validate_data_patch.py <resources 目录> [--expect expected-report.json]

输出（并与 expected-report.json 比对，若有差异退出码 1）：
    · avatar_fix   : 154 条里，旧哈希在 TextMapCHS 能解析出文本的有几条（应为 0），
                     新哈希能解析出文本的有几条（应为 154）
    · chapter_fix  : 46 幕 / 92 个哈希字段，同上
    · textmap_added: 22 条补丁词条，在 TextMapCHS 中已存在几条（应用后应为 22）

为什么"旧哈希解析不出来"就是补丁必要性的证据：
    AvatarExcelConfigData 的 nameTextMapHash / ChapterExcelConfigData 的幕号幕名哈希
    只是 TextMapCHS 的键。键不存在时客户端拿到空文本——角色名、章节名显示为空。
    补丁把键改成 TextMapCHS 里真实存在的键，并给出改前改后的计数，任何人都能复算。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as f:
        return json.load(f)


def count_resolvable(fields, tm):
    """fields: {id: {field: {old, new}}} -> (old_ok, new_ok, total_fields)"""
    old_ok = new_ok = total = 0
    for _rid, spec in fields.items():
        for _field, change in spec.items():
            total += 1
            if str(change["old"]) in tm:
                old_ok += 1
            if str(change["new"]) in tm:
                new_ok += 1
    return old_ok, new_ok, total


def check_avatar_table(res, avatar_fix, tm):
    """若 resources 里带了 AvatarExcelConfigData，统计当前状态。"""
    path = os.path.join(res, "ExcelBinOutput", "AvatarExcelConfigData.json")
    if not os.path.exists(path):
        return {"present": False}
    rows = json.load(open(path, encoding="utf-8"))
    by_id = {str(r.get("id")): r for r in rows}
    already = missing = 0
    for rid, spec in avatar_fix.items():
        row = by_id.get(rid)
        if row is None:
            continue
        if row.get("nameTextMapHash") == spec["nameTextMapHash"]["new"]:
            already += 1
        elif row.get("nameTextMapHash") == spec["nameTextMapHash"]["old"]:
            missing += 1
    return {"present": True, "already_applied": already, "needs_patch": missing}


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    res = sys.argv[1]

    tm_path = os.path.join(res, "TextMap", "TextMapCHS.json")
    if not os.path.exists(tm_path):
        print("找不到:", tm_path)
        return 2
    tm = json.load(open(tm_path, encoding="utf-8", errors="ignore"))

    avatar = load("avatar_fix.json")
    chapter = load("chapter_fix.json")
    added = load("textmap_added.json")

    a_old, a_new, a_total = count_resolvable(avatar, tm)
    c_old, c_new, c_total = count_resolvable(chapter, tm)
    t_present = sum(1 for k in added if str(k) in tm)

    report = {
        "textmap_entries": len(tm),
        "avatar": {
            "entries": len(avatar),
            "fields": a_total,
            "old_hash_resolvable_in_textmap": a_old,
            "new_hash_resolvable_in_textmap": a_new,
            "table": check_avatar_table(res, avatar, tm),
        },
        "chapter": {
            "chapters": len(chapter),
            "fields": c_total,
            "old_hash_resolvable_in_textmap": c_old,
            "new_hash_resolvable_in_textmap": c_new,
        },
        "textmap_added": {"entries": len(added), "already_present": t_present},
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))

    exit_code = 0
    # 必要条件：补丁里给出的新哈希必须全部能在 TextMapCHS 解析出文本
    if a_new != a_total:
        print("FAIL: avatar 新哈希并非全部可解析 (%d/%d)" % (a_new, a_total))
        exit_code = 1
    if c_new != c_total:
        print("FAIL: chapter 新哈希并非全部可解析 (%d/%d)" % (c_new, c_total))
        exit_code = 1

    if "--expect" in sys.argv:
        idx = sys.argv.index("--expect")
        if idx + 1 < len(sys.argv):
            exp_path = sys.argv[idx + 1]
            if os.path.exists(exp_path):
                exp = json.load(open(exp_path, encoding="utf-8"))
                for key in ("avatar", "chapter", "textmap_added"):
                    for field, want in exp.get(key, {}).items():
                        got = report.get(key, {}).get(field)
                        if got != want:
                            print("FAIL: %s.%s 期望 %r 实际 %r" % (key, field, want, got))
                            exit_code = 1
    return exit_code


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
