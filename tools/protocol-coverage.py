#!/usr/bin/env python3
"""Audit compiled-style opcode constants and active receiver annotations, without guessing identities."""
import argparse
import ast
from collections import defaultdict
import csv
import json
from pathlib import Path
import re


def uncomment(source):
    # Ignore literals as well, so quoted URLs or annotations cannot look like Java code.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',
                  lambda match: re.sub(r'[^\n]', ' ', match[0]), source)


def constants(source):
    expressions = dict(re.findall(r'public\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);', uncomment(source)))
    result = {}

    def resolve(name, visiting=()):
        if name in result:
            return result[name]
        if name in visiting or name not in expressions:
            raise ValueError(f'Unknown or cyclic opcode alias: {name}')
        value = expressions[name].strip()
        if re.fullmatch(r'(?:PacketOpcodes\.)?\w+', value) and not value[0].isdigit():
            result[name] = resolve(value.rsplit('.', 1)[-1], (*visiting, name))
        else:
            parsed = ast.literal_eval(value)
            if type(parsed) is not int:
                raise ValueError(f'Non-integer opcode: {name}')
            result[name] = parsed
        return result[name]

    for name in expressions:
        resolve(name)
    return result


def audit(root, reference=None):
    java = root / 'src/main/java/emu/grasscutter'
    values = constants((java / 'net/packet/PacketOpcodes.java').read_text())
    receivers = defaultdict(list)
    unknown = []
    for path in sorted((java / 'server/packet/recv').glob('*.java')):
        for block in re.findall(r'@Opcodes\s*\((.*?)\)', uncomment(path.read_text()), re.S):
            if re.search(r'\bdisabled\s*=\s*true\b', block):
                continue
            for name in re.findall(r'PacketOpcodes\.(\w+)', block):
                if name == 'NONE':
                    continue
                if name not in values:
                    unknown.append(name)
                else:
                    receivers[name].append(str(path.relative_to(root)))
    zero = {name: receivers[name] for name in sorted(receivers) if values[name] == 0}
    by_value = defaultdict(list)
    for name in receivers:
        if values[name] > 0:
            by_value[values[name]].append(name)
    result = {
        'opcode_constants': len(values),
        'zero_opcode_constants': sum(value == 0 for value in values.values()),
        'receiver_names': len(receivers),
        'zero_receiver_count': len(zero),
        'zero_receivers': zero,
        'unknown_receiver_constants': sorted(set(unknown)),
        'receiver_opcode_collisions': {str(value): sorted(names) for value, names in sorted(by_value.items()) if len(names) > 1},
        'limitation': 'Counts direct, enabled @Opcodes references only. Aliases are resolved; comments and NONE are excluded. Registration does not prove field compatibility or complete gameplay. Reference name matches are candidates, not permission to enable a route.',
    }
    if reference:
        by_name = defaultdict(set)
        with reference.open() as stream:
            for row in csv.DictReader(stream):
                by_name[row['name']].add(int(row['cmd_id']))
        rows = {name: next(iter(ids)) for name, ids in by_name.items() if len(ids) == 1}
        result['ambiguous_reference_names'] = sorted(name for name, ids in by_name.items() if len(ids) > 1)
        result['reference_constant_mismatches'] = [
            {'name': name, 'server_cmd': values[name], 'reference_cmd': rows[name]}
            for name in sorted(values) if name in rows and rows[name] != values[name]
        ]
        result['reference_receiver_mismatches'] = [
            {'name': name, 'server_cmd': values[name], 'reference_cmd': rows[name]}
            for name in sorted(receivers) if name in rows and rows[name] != values[name]
        ]
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--reference-cmdid', type=Path)
    args = parser.parse_args()
    print(json.dumps(audit(args.source_root, args.reference_cmdid), indent=2))
