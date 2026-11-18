#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 7.2.0 的数据补丁应用到你的 resources 目录。

本仓库不跟踪 /resources（游戏数据不属于服务端源码），所以数据修复以补丁形式提供：
    avatar_fix.json     AvatarExcelConfigData 的 nameTextMapHash 修正（154 个角色）
    chapter_fix.json    ChapterExcelConfigData 的幕号/幕名哈希修正（46 幕）
    textmap_added.json  服务端 TextMap 缺失、需要补入的词条（22 条）

用法：
    python apply_data_patch.py <你的 resources 目录> [--dry]

安全性：
    · 每个要写入的哈希都会先确认「在合并后的 TextMap 里能查到文本」，查不到就跳过并报告；
    · 写入前自动备份（.bak_时间戳）；
    · --dry 只预演，不写盘。
"""
import glob
import json
import os
import shutil
import sys
import datetime

HERE = os.path.dirname(os.path.abspath(__file__))


def load(name):
    return json.load(open(os.path.join(HERE, name), encoding="utf-8"))


def backup(path):
    stamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    bak = "%s.bak_%s" % (path, stamp)
    shutil.copy2(path, bak)
    return bak


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    res = sys.argv[1]
    dry = "--dry" in sys.argv

    excel = os.path.join(res, "ExcelBinOutput")
    tm_path = os.path.join(res, "TextMap", "TextMapCHS.json")
    for p in (excel, tm_path):
        if not os.path.exists(p):
            print("找不到:", p)
            return 2

    tm = json.load(open(tm_path, encoding="utf-8"))
    added = load("textmap_added.json")

    # 1) TextMap 词条
    merged = 0
    conflict = []
    for k, v in added.items():
        if k in tm and tm[k] != v:
            conflict.append((k, tm[k], v))
            continue
        if k not in tm:
            tm[k] = v
            merged += 1
    print("TextMap: 新增 %d 条, 冲突 %d 条" % (merged, len(conflict)))
    for k, a, b in conflict[:5]:
        print("   冲突 hash=%s 现有=%r 补丁=%r" % (k, a, b))

    if not dry and merged:
        bak = backup(tm_path)
        json.dump(tm, open(tm_path, "w", encoding="utf-8"), ensure_ascii=False)
        print("   TextMap 已写, 备份:", os.path.basename(bak))

    # 2) 两张 Excel 表
    for fname, patch_name, id_field in (
        ("AvatarExcelConfigData.json", "avatar_fix.json", "id"),
        ("ChapterExcelConfigData.json", "chapter_fix.json", "id"),
    ):
        path = os.path.join(excel, fname)
        if not os.path.exists(path):
            print("跳过（表不存在）:", fname)
            continue
        rows = json.load(open(path, encoding="utf-8"))
        by_id = {str(r.get(id_field)): r for r in rows}
        patch = load(patch_name)
        applied = skipped = 0
        for rid, fields in patch.items():
            row = by_id.get(rid)
            if row is None:
                skipped += 1
                continue
            for field, change in fields.items():
                new = change["new"]
                if str(new) not in tm:  # 文本查不到就不写
                    skipped += 1
                    continue
                row[field] = new
                applied += 1
        print("%s: 改写 %d 处, 跳过 %d 处" % (fname, applied, skipped))
        if not dry and applied:
            bak = backup(path)
            json.dump(rows, open(path, "w", encoding="utf-8"), ensure_ascii=False)
            print("   已写, 备份:", os.path.basename(bak))

    if dry:
        print("(--dry: 未写盘)")
    print("完成。改完请重启服务端（Excel 在启动时加载）。")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.exit(main())
