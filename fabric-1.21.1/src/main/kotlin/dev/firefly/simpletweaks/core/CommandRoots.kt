package dev.firefly.simpletweaks.core

/**
 * The root literals every mod command is registered under.
 *
 * ## Why two roots
 * `simple_tweaks` is the mod's own name and `fst` is the short alias (FireFly's Simple Tweaks) — the
 * same relationship `/enchantinfo` had with `/ei` in 1.12.2, except that aliases now live **inside**
 * the root instead of as independent top-level commands.
 *
 * ## Why the client and the server must NOT both register these names
 * `stconfig` / `enchantinfo` need to open client-only screens and `enchant` needs to mutate item
 * components server-side, so the obvious design is "register the screen ones client-side and share the
 * root". **That does not work, and it fails in a way that looks like a typo rather than a design
 * error.**
 *
 * <p>A client-side name is resolved against a client-only dispatcher *before* the server's tree is
 * consulted, and only two exception types fall through to the server —
 * `ClientCommandInternals#executeCommand` calls `#isIgnoredException`, which admits just
 * `dispatcherUnknownCommand` and `dispatcherParseException` (its own `TODO` notes it should check for
 * server commands first). So with `simple_tweaks` registered on both sides, `/simple_tweaks enchant …`
 * matched the *client's* node, failed on the unknown `enchant` child with `dispatcherUnknownArgument`,
 * and was reported to the player **without ever being sent to the server**. Observed in a real client
 * log as `Syntax exception for client-sided command 'fst enchant @s'` / `错误的命令参数 at position 4`.
 *
 * <p>The consequence for this file: **every** command is registered server-side, and a screen is
 * opened by packet ([dev.firefly.simpletweaks.network.packets.PacketOpenModScreen]) — which is what
 * 1.12.2 did. [CommandRoots.ALL] is still the single source for the root names, but both names are now
 * registered by the **server** tree only.
 *
 * <p>For the record, the node merge itself was never the problem: Brigadier's `CommandNode#addChild`
 * looks the name up in `children` and merges on a hit (the only rejection in that method is a
 * `RootCommandNode`, and requirements are not compared at all). Execution is what never got that far.
 */
object CommandRoots {

    val ALL: List<String> = listOf("simple_tweaks", "fst")
}
