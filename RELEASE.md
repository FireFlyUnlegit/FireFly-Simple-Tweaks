# 发布手册（GitHub Release + CurseForge）

> 面向作者本人的**手动**发布流程。代理不建 Release、不传 CurseForge（都需要账号授权），
> 这里只提供可照做的步骤、可复制的文案和校验方法。
>
> 当前版本：**`v2.2.4`**（`build=cleanup20`）· 产物 `build/libs/simple_tweaks-2.2.4.jar`
> · `C28E3C784CE271805C6CB312AF26CD3BA6CD59CEA35814EAA3F286E9A47EF507` / 839421 bytes

---

## 0. 硬规则（先读）

**发布用的 jar 必须与 `clean build` 的产物逐字节相同。**

任何"重新打包、改 zip 注释、替换 lang、改文件名里的版本号"都会改变 SHA256，进而让
**PCL / Modrinth / CurseForge 的指纹识别**认不出它 —— PCL 的模组列表是靠在线指纹匹配的，
认不出就只显示文件名 + 默认方块图标（这不是 `fabric.mod.json` 的问题，见下文「常见坑」）。

---

## 1. 发布前确认（30 秒）

| 项 | 命令 / 位置 | 期望 |
|---|---|---|
| 版本号 | `gradle.properties` 的 `mod_version` | 等于要发布的版本，如 `2.2.4` |
| 构建标识 | `SimpleTweaksClient` 的 `(vX.Y.Z, build=cleanupN)` | 与 `docs/acceptance-checklist.md` 页首一致 |
| 构建 | `.\gradlew.bat clean build` | `BUILD SUCCESSFUL` + `runTest: 14 checks, 0 failed` |
| 产物 | `build/libs/simple_tweaks-<version>.jar` | 字节数与页首记录一致 |
| 哈希 | `Get-FileHash build\libs\simple_tweaks-<version>.jar -Algorithm SHA256` | 与页首 64 位哈希**逐位**相同 |
| 冒烟（可选） | `.\gradlew.bat runGametest` | `All 5 required tests passed :)` |

> ❌ **不要上传**：`build/libs/simple_tweaks-<version>-sources.jar`、`build/devlibs/*-dev.jar`
> （未 remap，装了必崩）、任何 `.jar.prev.*` / `.bak` / `.unused-*`。

---

## 2. GitHub Release

1. 确认 tag 已推送（本版已推 `v2.2.4`）：
   ```powershell
   git tag -a v2.2.4 -m "v2.2.4 - <一句话主题>"
   git push origin v2.2.4
   ```
2. 打开 <https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases/new>
3. **Choose a tag** → 选 `v2.2.4`（不要新建分支、不要用别的 ref）
4. **Release title**：`v2.2.4` 或 `v2.2.4 — 元素系统成型 / Elements take shape`
5. **Describe this release**：复制 [附录 A](#附录-arelease-说明可直接复制)
6. **Attach binaries**：拖入 `build/libs/simple_tweaks-2.2.4.jar` —— **只有这一个文件**
7. 勾选 `Set as the latest release` → **Publish release**
8. 发布后从 Release 页面**重新下载**一次，比 SHA256（第 4 节）

---

## 3. CurseForge

1. 打开 CurseForge 上本项目的 **Files** 页 → **Upload File**
2. **File**：`build/libs/simple_tweaks-2.2.4.jar`
3. **Display name**：`FireFly's Simple Tweaks 2.2.4`（不要改文件名里的版本）
4. **Release type**：`Release`（正式）
5. **Game version**：只勾 **`1.21.1`**；**Mod loader**：`Fabric`
   （**不要**勾 Forge/NeoForge —— 那是 `master` 分支的 1.12.2 版，另一个工程）
6. **Environment**：`Client and server`（本模组两侧都有逻辑）
7. **Dependencies → Required**：
   - `Fabric API`（`0.116.17+1.21.1` 或更新）
   - `Fabric Language Kotlin`（`1.12.3+kotlin.2.0.21` 或更新）
   - 可选 **Recommended**：`Mod Menu`（只影响在模组列表里看说明）
8. **Changelog**：复制 [附录 B](#附录-bcurseforge-changelog可直接复制)
9. 上传后 CF 会算指纹；PCL 的模组列表**几分钟后**才识别（有缓存）

---

## 4. 发布后校验

```powershell
# 对从 Release / CurseForge 下载回来的文件：
Get-FileHash .\simple_tweaks-2.2.4.jar -Algorithm SHA256
# 必须与 docs/acceptance-checklist.md 页首记录的哈希逐位相同
```

加载确认：客户端 `logs/latest.log` 里应出现

```
FireFly's Simple Tweaks load complete.
FireFly's Simple Tweaks client ready: 88 enchantments + … (v2.2.4, build=cleanup20)
```

---

## 5. 常见坑

| 症状 | 原因 |
|---|---|
| PCL 里显示文件名 + 方块图标 | 该文件还没被 Modrinth/CurseForge 收录（指纹查不到）。不是 `fabric.mod.json` 的问题，ModMenu 里图标/描述/链接都是正常的 |
| 启动即崩 `NoClassDefFoundError: …ServerWorld` | 传的是 `devlibs/*-dev.jar`（未 remap） |
| 每个 mixin 都报 `target … was not found` | 同上；或 jar 被解压后重新压过（refmap 路径变了） |
| CF 装完提示缺少依赖 | 第 3 步没加 `fabric-api` / `fabric-language-kotlin` |
| 同一版本号出现两份不同哈希 | 忘了 `clean build`（增量产物可能残留旧类，见 `AGENTS.md` §1 硬规则） |
| 客户端启动后有类加载 `ZipException: invalid LOC header` | jar 在游戏运行时被覆盖过。**关闭客户端**再替换文件；正在跑的 JVM 已损坏，必须重启 |

---

## 附录 A：Release 说明（可直接复制）

```markdown
### v2.2.4 — 元素系统成型 / Elements take shape

**中文**

- 元素伤害不再豁免无敌帧，`连击（combo）` 成为唯一能缩短/取消无敌帧的附魔（10 级完全取消）
- 转化率重调为 **1.5% × 等级（上限 7.5%）**，元素伤害重新**穿甲**
- 新增 3 个元素附魔：**元素壁垒**（全元素减伤）、**元素共鸣**（反应伤害）、**元素穿透**（无视元素减伤）
- 分开显示时**每种元素一个数字**，数字纵向错层、互不遮挡
- 元素反应以**浮字**显示在目标头顶，并带上这次反应的伤害，如 `感电 (-28.30)`
- 新增 6 个元素反应：过载 / 圣化 / 涤净 / 灼魂 / 枯萎 / 日蚀（共 18 个反应）
- 修复：天罚的额外伤害会自我放大直到血量变成 `NaN`（怪物打不死）；`flight` / `saturation` 的客户端 desync

**English**

- Elemental damage no longer bypasses the i-frame gate; `combo` is now the only enchantment that can shorten or cancel it (level 10 cancels it entirely)
- Conversion share re-tuned to **1.5% × level (cap 7.5%)**, and elemental damage bypasses armour again
- Three new element enchantments: **Elemental Ward**, **Elemental Resonance**, **Elemental Penetration**
- The split damage display now emits **one number per element**, staggered vertically so they no longer overlap
- Reactions are shown as a **floating label** above the target together with the damage they dealt, e.g. `Conduct (-28.30)`
- Six new reactions: Overload / Sanctify / Purify / Scorch / Blight / Eclipse (18 in total)
- Fixed: heavenly punishment's extra damage could amplify itself until health became `NaN` (unkillable mobs); `flight` / `saturation` client desync

Requires Minecraft **1.21.1**, Fabric Loader, Fabric API, Fabric Language Kotlin.
```

## 附录 B：CurseForge Changelog（可直接复制）

```markdown
## v2.2.4

### 新增 / Added
- 3 个元素附魔：元素壁垒、元素共鸣、元素穿透
- 6 个元素反应：过载、圣化、涤净、灼魂、枯萎、日蚀

### 调整 / Changed
- 转化率改为 1.5% × 等级（上限 7.5%）；元素伤害重新穿甲
- 元素伤害不再豁免无敌帧，只有 `combo` 可以取消
- 分开显示：每种元素各一个数字，纵向错层
- 反应提示改为目标头顶的浮字，并附带该反应的伤害

### 修复 / Fixed
- 天罚的额外伤害自我放大导致血量 `NaN`、怪物打不死
- `flight` / `saturation` 的客户端 desync
- 元素伤害在无敌帧内仍然落地
```
