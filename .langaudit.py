import json, os, re

root = r"src/main"
lang_dir = os.path.join(root, "resources/assets/simple_tweaks/lang")
en = json.loads(open(os.path.join(lang_dir, "en_us.json"), "rb").read().decode("utf-8-sig"))
zh = json.loads(open(os.path.join(lang_dir, "zh_cn.json"), "rb").read().decode("utf-8-sig"))

# collect literal keys used in code
keys = set()
pat = re.compile(r'Text\.translatable\(\s*"([^"]+)"')
pat2 = re.compile(r'"(commands\.simple_tweaks\.[a-z_.]+|gui\.simple_tweaks\.[a-z_.]+|enchantment\.simple_tweaks\.[a-z_.]+|element\.simple_tweaks\.[a-z_.]+)"')
for base, _, files in os.walk(root):
    for f in files:
        if not f.endswith((".kt", ".java")):
            continue
        p = os.path.join(base, f)
        txt = open(p, "rb").read().decode("utf-8", "replace")
        for m in pat.finditer(txt):
            keys.add(m.group(1))
        for m in pat2.finditer(txt):
            keys.add(m.group(1))

# keys built dynamically (reaction.simple_tweaks.<key>, enchantment.simple_tweaks.<id>.desc) -> prefixes
missing_en = sorted(k for k in keys if k not in en)
missing_zh = sorted(k for k in keys if k not in zh)
print("literal keys used in code:", len(keys))
print("missing in en:", missing_en)
print("missing in zh:", missing_zh)

# dynamic families: every reaction key must exist
reaction_keys = sorted(k for k in en if k.startswith("reaction.simple_tweaks."))
print("reaction keys:", len(reaction_keys))
missing_reaction_zh = [k for k in reaction_keys if k not in zh]
print("reaction keys missing in zh:", missing_reaction_zh)

# sanity: values with a single % (would break String.format)
bad = []
for name, d in (("en", en), ("zh", zh)):
    for k, v in d.items():
        for m in re.finditer(r"%", v):
            i = m.start()
            nxt = v[i:i+2]
            if nxt == "%%":
                continue
            # allow %s / %d / %1$s
            if re.match(r"%(\d+\$)?[sd]", v[i:]):
                continue
            bad.append((name, k, v[max(0,i-20):i+20]))
print("suspicious % values:", bad[:10], "count", len(bad))

# empty or placeholder-ish values
empty = [k for k, v in en.items() if not v.strip()]
print("empty en values:", empty)
