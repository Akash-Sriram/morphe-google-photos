package app.morphe.patches.googlephotos.misc.bento

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction

internal object BentoModelBuilderFingerprint : Fingerprint(
    custom = { method, _ ->
        method.implementation?.instructions?.any { inst ->
            inst.opcode == Opcode.CONST && (inst as? NarrowLiteralInstruction)?.narrowLiteral == 0x7f1402cc
        } == true
    },
)
