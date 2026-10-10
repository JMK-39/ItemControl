package dev.xyat.itemcontrol.item.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;

/** Detached selective property edits, shared by batch, copy and templates. */
public final class ItemRuleDrafts {
    public static final List<String> FIELDS=List.of("attack_damage","attack_speed","armor","armor_toughness","knockback_resistance","attributes","equipment_slot",
            "mining_speed","mining_level","max_damage","max_stack_size","enchantability","rarity","edible","nutrition","saturation","eat_seconds","always_eat","non_consumable",
            "food_effects","food_effects_mode","food_remainder","block_hardness","block_explosion_resistance","fire_resistant","explosion_immune","glowing","no_gravity","persistent",
            "curio.enabled","curio.slots","curio.can_unequip","curio.attributes","curio.slot_modifiers");
    private ItemRuleDrafts() {}
    public static JsonElement value(JsonObject rule,String path){
        if(!FIELDS.contains(path))throw new IllegalArgumentException("property path");
        int dot=path.indexOf('.');if(dot<0)return rule.get(path);
        JsonElement owner=rule.get(path.substring(0,dot));return owner!=null&&owner.isJsonObject()?owner.getAsJsonObject().get(path.substring(dot+1)):null;
    }
    public static JsonObject merge(JsonObject target,JsonObject source,Set<String> selected){
        JsonObject result=target.deepCopy();
        for(String path: selected){JsonElement next=value(source,path);int dot=path.indexOf('.');
            if(dot<0){if(next==null)result.remove(path);else result.add(path,next.deepCopy());continue;}
            String root=path.substring(0,dot),key=path.substring(dot+1);JsonElement original=result.get(root);
            if(original!=null&&!original.isJsonObject())throw new IllegalArgumentException("malformed nested properties");
            JsonObject nested=original==null?new JsonObject():original.getAsJsonObject();
            if(next==null)nested.remove(key);else nested.add(key,next.deepCopy());
            if(nested.size()==0)result.remove(root);else result.add(root,nested);
        }return result;
    }
    public record Template(String name,JsonObject fields,Set<String> selectedFields) {public Template{selectedFields=Set.copyOf(selectedFields);}}
    public static String encodeTemplate(String name,JsonObject source,Set<String> selected){
        validateName(name);JsonObject opaque=source.deepCopy();
        for(String path:FIELDS){int dot=path.indexOf('.');if(dot<0)opaque.remove(path);else {String owner=path.substring(0,dot);var nested=opaque.get(owner);if(nested!=null&&nested.isJsonObject()){nested.getAsJsonObject().remove(path.substring(dot+1));if(nested.getAsJsonObject().size()==0)opaque.remove(owner);}}}
        JsonObject root=new JsonObject();root.addProperty("format","itemcontrol-properties");root.addProperty("version",1);root.addProperty("name",name.trim());root.add("fields",merge(opaque,source,selected));
        var paths=new com.google.gson.JsonArray();FIELDS.stream().filter(selected::contains).forEach(paths::add);root.add("selected_fields",paths);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
    public static Template decodeTemplate(String raw){
        try{
            if(raw==null||raw.length()>262144)throw new IllegalArgumentException("template size");var root=JsonParser.parseString(raw).getAsJsonObject();
            if(!root.get("format").getAsString().equals("itemcontrol-properties")||ItemCapabilitySettings.integer(root.get("version"),1,1)!=1||!root.get("fields").isJsonObject())throw new IllegalArgumentException("template format");
            String name=ItemCapabilitySettings.optionalString(root,"name");validateName(name);var fields=root.getAsJsonObject("fields").deepCopy();var selected=new java.util.LinkedHashSet<String>();
            if(root.has("selected_fields")){var paths=root.get("selected_fields");if(!paths.isJsonArray()||paths.getAsJsonArray().size()>FIELDS.size())throw new IllegalArgumentException("selected fields");for(var path:paths.getAsJsonArray()){if(!path.isJsonPrimitive()||!path.getAsJsonPrimitive().isString()||!FIELDS.contains(path.getAsString()))throw new IllegalArgumentException("property path");selected.add(path.getAsString());}}
            else for(String path:FIELDS)if(value(fields,path)!=null)selected.add(path);
            return new Template(name.trim(),fields,selected);
        }catch(RuntimeException error){throw new IllegalArgumentException("Invalid item property template",error);}
    }
    private static void validateName(String name){if(name==null||name.trim().isEmpty()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("template name");}
}
