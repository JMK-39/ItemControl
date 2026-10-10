package dev.xyat.itemcontrolvalidation;

import com.google.gson.JsonParser;
import dev.xyat.itemcontrol.item.config.ItemRuleDrafts;
import java.util.Set;

/** Pure draft operations: selective batch/template edits cannot overwrite unrelated rule fields. */
public final class ItemRuleDraftsTest {
    public static void main(String[] args){
        var target=JsonParser.parseString("{\"armor\":3,\"nutrition\":6,\"future_field\":{\"a\":1},\"curio\":{\"slots\":[\"ring\"],\"can_unequip\":false}}").getAsJsonObject();
        var source=JsonParser.parseString("{\"armor\":8,\"equipment_slot\":\"chest\",\"curio\":{\"slots\":[\"head\"],\"attributes\":[{\"attribute\":\"minecraft:armor\",\"mode\":\"remove\"}]}}").getAsJsonObject();
        var selected=Set.of("armor","curio.attributes","max_damage");
        var result=ItemRuleDrafts.merge(target,source,selected);
        require(result.get("armor").getAsInt()==8 && result.get("nutrition").getAsInt()==6 && !result.has("equipment_slot"),"batch only checked fields");
        require(result.getAsJsonObject("curio").getAsJsonArray("slots").get(0).getAsString().equals("ring") && !result.getAsJsonObject("curio").get("can_unequip").getAsBoolean(),"nested accessory edits preserve slots and lock");
        require(target.get("armor").getAsInt()==3 && !target.getAsJsonObject("curio").has("attributes"),"draft operations do not mutate input/cancel state");
        require(result.get("future_field").equals(target.get("future_field")),"unknown fields retained");
        var remove=ItemRuleDrafts.merge(target,source,Set.of("nutrition"));require(!remove.has("nutrition"),"checked inherited field clears its target override");
        String encoded=ItemRuleDrafts.encodeTemplate("战士加餐",source,Set.of("armor","curio.attributes"));
        var decoded=ItemRuleDrafts.decodeTemplate(encoded);require(decoded.name().equals("战士加餐") && decoded.fields().has("armor") && !decoded.fields().has("equipment_slot"),"named selective template round trip");
        var inherited=ItemRuleDrafts.decodeTemplate(ItemRuleDrafts.encodeTemplate("继承耐久",source,Set.of("max_damage")));
        require(inherited.selectedFields().contains("max_damage")&&!inherited.fields().has("max_damage"),"template preserves a checked inherited field");
        boolean rejected=false;try{ItemRuleDrafts.decodeTemplate("{\"format\":\"other\",\"version\":1,\"name\":\"x\",\"fields\":{}}");}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"reject unrelated imported document");
        rejected=false;try{ItemRuleDrafts.merge(target,source,Set.of("future_field.a"));}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"only editable property paths accepted");
        System.out.println("ITEM_RULE_DRAFTS_PASS");
    }
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}
}
