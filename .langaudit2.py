import json, os, re

root = r"src/main"
lang_dir = os.path.join(root, "resources/assets/simple_tweaks/lang")
en = json.loads(open(os.path.join(lang_dir, "en_us.json"), "rb").read().decode("utf-8-sig"))
zh = json.loads(open(os.path.join(lang_dir, "zh_cn.json"), "rb").read().decode("utf-8-sig"))

datapack = set()
gen = os.path.join("build/generated/ksp/main/resources/data/simple_tweaks/enchantment")
for f in os.listdir(gen):
    if f.endswith(".json"):
        datapack.add(os.path.splitext(f)[0])
print("datapack enchantments:", len(datapack))

en_ids = {k.split(".")[2] for k in en if k.startswith("enchantment.simple_tweaks.") and k.count(".") == 2}
zh_ids = {k.split(".")[2] for k in zh if k.startswith("enchantment.simple_tweaks.") and k.count(".") == 2}
print("lang ids (en):", len(en_ids), " (zh):", len(zh_ids))
print("lang ids not in datapack (en):", sorted(en_ids - datapack))
print("lang ids not in datapack (zh):", sorted(zh_ids - datapack))
print("datapack ids with no lang name:", sorted(datapack - en_ids))

# placeholder signature compare (en vs zh) for the same key
sig = lambda v: sorted(re.findall(r"%(\d+\$)?[sd%]", v))
mism = []
for k in en:
    if k not in zh:
        continue
    if sig(en[k]) != sig(zh[k]):
        mism.append((k, sig(en[k]), sig(zh[k])))
print("placeholder signature mismatches:", len(mism))
for m in mism[:12]:
    print("  ", m)
