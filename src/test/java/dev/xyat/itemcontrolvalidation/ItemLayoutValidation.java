package dev.xyat.itemcontrolvalidation;

/** Opt-in 1080p English/Chinese capture of the changed layouts, with no Save actions. */
public final class ItemLayoutValidation {
    private ItemLayoutValidation() {}
    public static void install() {
        defaults("itemcontrol.guiValidation.phases", "1,3");
        defaults("itemcontrol.guiValidation.pages", "0,5,9,10,14,15,16,17,19,20,21,33");
        defaults("itemcontrol.guiValidation.output", "D:/IDEAWork/ItemControl/build/ui-fixes-20261016/gui-layout");
        GuiLongTextValidation.install();
    }
    private static void defaults(String key, String value) {
        if (System.getProperty(key) == null) System.setProperty(key, value);
    }
}
