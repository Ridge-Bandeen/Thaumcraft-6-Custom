"""Converts the original's 1.12 .lang translations to 1.21 .json, keyed like the port's en_us.json.

1.21 ignores .lang files, so every language but English showed only English. For each key in the port's en_us.json:
the same key in the old file if it still exists there, else the old key whose ORIGINAL English text (en_us.lang) is
identical to the port's English text for this key. Keys with no match are left out (the game falls back to English).

    python tools/convert_lang.py            # report coverage only
    python tools/convert_lang.py --write    # also write <lang>.json next to the .lang files
"""
import json, os, sys, collections

LANG = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'thaumcraft', 'lang')


def read_lang(path):
    out = {}
    with open(path, encoding='utf-8-sig', errors='replace') as f:
        for line in f:
            line = line.rstrip('\r\n')
            if not line or line.lstrip().startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            out[k.strip()] = v
    return out


def main():
    port_en = json.load(open(os.path.join(LANG, 'en_us.json'), encoding='utf-8'))
    orig_en = read_lang(os.path.join(LANG, 'en_us.lang'))
    by_text = collections.defaultdict(list)
    for k, v in orig_en.items():
        by_text[v.strip()].append(k)
    write = '--write' in sys.argv
    for name in sorted(os.listdir(LANG)):
        if not name.endswith('.lang') or name == 'en_us.lang':
            continue
        code = name[:-5].lower()
        old = read_lang(os.path.join(LANG, name))
        out, same, by_value = {}, 0, 0
        for key, en_text in port_en.items():
            if key in old:
                out[key] = old[key]
                same += 1
                continue
            for old_key in by_text.get(en_text.strip(), []):
                if old_key in old:
                    out[key] = old[old_key]
                    by_value += 1
                    break
        print(f'{code}: {len(out)}/{len(port_en)} keys ({same} same key, {by_value} by English text)')
        if write:
            with open(os.path.join(LANG, code + '.json'), 'w', encoding='utf-8', newline='\n') as f:
                json.dump(out, f, ensure_ascii=False, indent=2)
                f.write('\n')


if __name__ == '__main__':
    main()
