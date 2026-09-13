package app.morphe.patches.googlephotos.misc.flags

import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patcher.patch.bytecodePatch

val bakeMemoryStyleFlagsPatch = bytecodePatch(
    name = "Bake memory style flags",
    description = "Hard-codes the Styles in Memories feature flags into the DEX.",
    default = false,
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS)
    execute {
        // Disabled completely to prevent crashes on v7.92.
    }
}
