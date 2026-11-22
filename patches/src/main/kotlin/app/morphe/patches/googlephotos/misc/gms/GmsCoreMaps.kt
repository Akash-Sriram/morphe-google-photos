package app.morphe.patches.googlephotos.misc.gms

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal fun BytecodePatchContext.applyGmsCoreMaps() {
    // 1. Places Map photo grid scroll jump
    PlacesMapScrollJumpFingerprint.methodOrNull?.let { method ->
        val mutableClass = mutableClassDefBy(method.definingClass)
        val mutableMethod = mutableClass.findMutableMethodOf(method)
        mutableMethod.addInstruction(
            0,
            "invoke-static {v2}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->adjustScrollPosition(I)I",
        )
        mutableMethod.addInstruction(
            1,
            "move-result v2",
        )
        mutableMethod.addInstruction(
            2,
            "invoke-static {v2, v3}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->adjustScrollOffset(II)I",
        )
        mutableMethod.addInstruction(
            3,
            "move-result v3",
        )
    }

    // 2. Map Explore bottom sheet header photo count
    MapExploreDateRangeFingerprint.methodOrNull?.let { method ->
        val mutableClass = mutableClassDefBy(method.definingClass)
        val mutableMethod = mutableClass.findMutableMethodOf(method)
        val impl = method.implementation
        if (impl != null) {
            var aPIndex = -1
            var ifNezIndex = -1
            impl.instructions.forEachIndexed { idx, instr ->
                if (aPIndex == -1) {
                    if (instr is ReferenceInstruction && instr.opcode.name.lowercase().startsWith("iget-boolean")) {
                        val ref = instr.reference as? FieldReference
                        if (ref != null && ref.name == "aP") {
                            aPIndex = idx
                        }
                    }
                } else if (ifNezIndex == -1 && idx == aPIndex + 1 && instr.opcode.name.lowercase().startsWith("if-nez")) {
                    ifNezIndex = idx
                }
            }
            if (ifNezIndex != -1) {
                mutableMethod.replaceInstruction(ifNezIndex, "nop")
            }
        }
    }

    // 3. Hook photos.killswitch_info_panel_map to prevent crash when MicroG lacks location or map styles
    KillswitchInfoPanelMapFingerprint.methodOrNull?.let { clinitMethod ->
        var foundDist = -1
        var targetField: FieldReference? = null
        clinitMethod.implementation?.instructions?.forEach { instr ->
            val ref = (instr as? ReferenceInstruction)?.reference
            if (ref is StringReference && ref.string == "photos.killswitch_info_panel_map") {
                foundDist = 0
            } else if (foundDist in 0..15 && ref is FieldReference && instr.opcode.name.startsWith("sput", ignoreCase = true)) {
                targetField = ref
                foundDist = -1
            } else if (foundDist >= 0) {
                foundDist++
            }
        }

        if (targetField != null) {
            val killswitchClassDef = classDefBy(clinitMethod.definingClass)
            val supplierClassTypes = mutableSetOf<String>()
            killswitchClassDef.methods.filter { it.name == "<init>" }.forEach { initMethod ->
                initMethod.implementation?.instructions?.forEach { instr ->
                    val ref = (instr as? ReferenceInstruction)?.reference
                    if (ref is TypeReference) {
                        val type = ref.type
                        if (!type.startsWith("Landroid/") && !type.startsWith("Ljava/") && !type.contains("Context")) {
                            supplierClassTypes.add(type)
                        }
                    }
                }
            }

            fun patchMethodIfMatches(cDef: ClassDef, method: Method): Boolean {
                val impl = method.implementation ?: return false
                val instructions = impl.instructions.toList()
                var sgetDistance = -1
                var patched = false
                instructions.forEachIndexed { index, instruction ->
                    val ref = (instruction as? ReferenceInstruction)?.reference
                    if (ref is FieldReference && ref.definingClass == targetField.definingClass && ref.name == targetField.name && instruction.opcode.name.startsWith("sget", ignoreCase = true)) {
                        sgetDistance = 0
                    } else if (sgetDistance in 0..5 && ref is MethodReference && ref.returnType == "Z" && instruction.opcode.name.startsWith("invoke", ignoreCase = true)) {
                        sgetDistance = -1
                        val nextIndex = index + 1
                        if (nextIndex < instructions.size) {
                            val nextIns = instructions[nextIndex]
                            if (nextIns is OneRegisterInstruction && nextIns.opcode.name.startsWith("move-result", ignoreCase = true)) {
                                val reg = nextIns.registerA
                                val mutableMethod = mutableClassDefBy(cDef).findMutableMethodOf(method)
                                mutableMethod.addInstruction(
                                    nextIndex + 1,
                                    "invoke-static {v$reg}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->isInfoPanelMapKillswitched(Z)Z",
                                )
                                mutableMethod.addInstruction(
                                    nextIndex + 2,
                                    "move-result v$reg",
                                )
                                patched = true
                            }
                        }
                    } else if (sgetDistance >= 0) {
                        sgetDistance++
                    }
                }
                return patched
            }

            for (supplierType in supplierClassTypes) {
                classDefByOrNull(supplierType)?.let { sClass ->
                    var didPatch = false
                    sClass.methods.forEach { m ->
                        if (patchMethodIfMatches(sClass, m)) didPatch = true
                    }
                    if (didPatch) return@let
                }
            }
        }
    }

    // 4. CurrentLocationMixin hooks
    val currentLocationClass = mutableClassDefBy(CurrentLocationMixinOnViewCreatedFingerprint.method.definingClass)
    val currentLocationMethodA = currentLocationClass.methods.first {
        it.name == "a" && it.parameterTypes.isEmpty() && it.returnType == "V"
    }
    currentLocationMethodA.apply {
        addInstruction(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->handleCurrentLocation(Ljava/lang/Object;)V",
        )
        addInstruction(
            1,
            "return-void",
        )
    }

    CurrentLocationMixinOnViewCreatedFingerprint.method.addInstruction(
        0,
        "invoke-static {p0}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->initMapLocation(Ljava/lang/Object;)V",
    )

    val currentLocationMethodB = currentLocationClass.methods.first {
        it.name == "b" && it.parameterTypes.toList() == listOf("Z") && it.returnType == "V"
    }
    currentLocationMethodB.addInstruction(
        0,
        "invoke-static {p0, p1}, Lapp/morphe/extension/shared/patches/map/PhotosMapHelper;->onCurrentLocationMixinTintUpdated(Ljava/lang/Object;Z)V",
    )
}
