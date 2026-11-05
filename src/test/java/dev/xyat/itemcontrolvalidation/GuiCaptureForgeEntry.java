//? if <1.21 {
package dev.xyat.itemcontrolvalidation;

/** Forge 1.20.1 entry of the GUI capture: it runs inside an installed modpack, where the 1.21 checks cannot. */
@net.minecraftforge.fml.common.Mod("itemcontrol_validation")
public final class GuiCaptureForgeEntry {
    public GuiCaptureForgeEntry() {
        if (Boolean.getBoolean("itemcontrol.curioValidation")) { CurioNativeValidation.install(); return; }
        if (Boolean.getBoolean("itemcontrol.layoutValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> ItemLayoutValidation::install);
            return;
        }
        if (Boolean.getBoolean("itemcontrol.propertyValidation")) ItemPropertyRuntimeChecks.install();
        // The playtest drives the editor itself, so it replaces the GUI capture when both are switched on.
        if (Boolean.getBoolean("itemcontrol.playtest")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> ItemPlaytestValidation::install);
        } else if (Boolean.getBoolean("itemcontrol.guiValidation")) {
            dev.xyat.kineticcore.api.runtime.KineticPlatform.runOnClient(() -> GuiLongTextValidation::install);
        }
    }
}
//?}
