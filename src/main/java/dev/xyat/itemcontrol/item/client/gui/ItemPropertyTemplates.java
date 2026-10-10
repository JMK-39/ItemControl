package dev.xyat.itemcontrol.item.client.gui;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import dev.xyat.itemcontrol.item.config.ItemRuleDrafts;
import dev.xyat.kineticcore.api.runtime.KineticPaths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-local reusable templates; importing never activates or saves server item rules. */
final class ItemPropertyTemplates {
    private static final String FILE="kineticcore/item_property_templates.json";
    private ItemPropertyTemplates(){}
    static Map<String,ItemRuleDrafts.Template> load() throws java.io.IOException {
        var result=new LinkedHashMap<String,ItemRuleDrafts.Template>();if(!KineticPaths.configFileExists(FILE))return result;
        var json=JsonParser.parseString(KineticPaths.readConfigText(FILE)).getAsJsonObject();
        for(var raw:json.entrySet()){var template=ItemRuleDrafts.decodeTemplate(raw.getValue().toString());result.put(template.name(),template);}return result;
    }
    static void save(Map<String,ItemRuleDrafts.Template> templates) throws java.io.IOException{
        var root=new JsonObject();for(var template:templates.values())root.add(template.name(),JsonParser.parseString(ItemRuleDrafts.encodeTemplate(template.name(),template.fields(),template.selectedFields())));
        KineticPaths.writeConfigTextsAtomic(Map.of(FILE,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)));
    }
    static Path selectFile(boolean export){
        try(var memory=org.lwjgl.system.MemoryStack.stackPush()){
            var filters=memory.mallocPointer(1);filters.put(memory.UTF8("*.json")).flip();
            String selected=export?org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(ItemRuleLabels.text("templates.export").getString(),"物品规则模板.json",filters,"JSON")
                    :org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(ItemRuleLabels.text("templates.import").getString(),"",filters,"JSON",false);
            return selected==null?null:Path.of(selected);
        }
    }
    static ItemRuleDrafts.Template read(Path path)throws java.io.IOException{if(Files.size(path)>262144)throw new java.io.IOException("Template too large");var template=ItemRuleDrafts.decodeTemplate(Files.readString(path,StandardCharsets.UTF_8));if(!dev.xyat.itemcontrol.item.config.ItemPropertyConfig.validateRule(template.fields()).isEmpty())throw new java.io.IOException("Invalid template properties");return template;}
    static void write(Path path,String encoded)throws java.io.IOException{Files.writeString(path,encoded,StandardCharsets.UTF_8);}
}
