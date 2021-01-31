package io.github.lokka30.levelledmobs.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.FileUtil;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FileLoader {

    public static final int SETTINGS_FILE_VERSION = 25; // Last changed: 2.1.0 PRE 9
    public static final int MESSAGES_FILE_VERSION = 1; // Last changed: 2.1.0

    private FileLoader() {
        throw new UnsupportedOperationException();
    }

    public static YamlConfiguration loadFile(final Plugin plugin, String cfgName, final int compatibleVersion, boolean doMigrate) {
        cfgName = cfgName + ".yml";

        Utils.logger.info("&fFile Loader: &7Loading file '&b" + cfgName + "&7'...");

        final File file = new File(plugin.getDataFolder(), cfgName);

        saveResourceIfNotExists(plugin, file);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.options().copyDefaults(true);

        final int fileVersion = cfg.getInt("file-version");

        if (fileVersion < compatibleVersion && doMigrate){
            final File backedupFile = new File(plugin.getDataFolder(), "settings-" + fileVersion + ".old");
            // copy settings.yml to settings.old
            FileUtil.copy(file, backedupFile);
            Utils.logger.info("settings.yml backed up to " + backedupFile.getName());
            // overwrite settings.yml from new version
            plugin.saveResource(file.getName(), true);

            // copy supported values from old file to new
            Utils.logger.info("migrating settings from old version to new settings.yml");
            copyYmlValues(backedupFile, file, fileVersion);

            // reload cfg from the updated values
            cfg = YamlConfiguration.loadConfiguration(file);

        } else{
            checkFileVersion(file, compatibleVersion, cfg.getInt("file-version"));
        }

        return cfg;
    }

    public static void saveResourceIfNotExists(final Plugin instance, final File file) {
        if (!file.exists()) {
            Utils.logger.info("&fFile Loader: &7Configuration file '&b" + file.getName() + "&7' doesn't exist, creating it now...");
            instance.saveResource(file.getName(), false);
        }
    }

    private static void checkFileVersion(final File file, final int compatibleVersion, final int installedVersion) {
        if (compatibleVersion == installedVersion) {
            return;
        }

        String what;
        if (installedVersion < compatibleVersion) {
            what = "outdated";
        } else {
            what = "ahead of the compatible version of this file for this version of the plugin";
        }

        Utils.logger.error("&fFile Loader: &7The version of &b" + file.getName() + "&7 you have installed is " + what + "! Fix this as soon as possible, else the plugin will most likely malfunction.");
        Utils.logger.error("&fFile Loader: &8(&7You have &bv" + installedVersion + "&7 installed but you are meant to be running &bv" + compatibleVersion + "&8)");
    }

    private static int getFieldDepth(String line){
        int whiteSpace = 0;

        for (int i = 0; i < line.length(); i++){
            if (line.charAt(i) != ' ') break;
            whiteSpace++;
        }
        return whiteSpace == 0 ? 0 : whiteSpace / 2;
    }

    private static class FieldInfo{
        public String simpleValue;
        public List<String> valueList;
        public int depth;

        public FieldInfo(String value, int depth){
            this.simpleValue = value;
            this.depth = depth;
        }

        public FieldInfo(String value, int depth, boolean isListValue){
            if (isListValue) addListValue(value);
            else this.simpleValue = value;
            this.depth = depth;
        }

        public boolean isList(){
            return valueList != null;
        }

        public void addListValue(String value){
            if (valueList == null) valueList = new ArrayList<>();
            valueList.add(value);
        }

        public String toString() {
            if (this.isList()){
                if (this.valueList == null || this.valueList.isEmpty())
                    return super.toString();
                else
                    return String.join(",", this.valueList);
            }

            if (this.simpleValue == null)
                return super.toString();
            else
                return this.simpleValue;
        }
    }

    private static String getKeyFromList(List<String> list, String currentKey){
        if (list.size() == 0) return currentKey;

        String result = String.join(".", list);
        if (currentKey != null) result += "." + currentKey;

        return result;
    }

    private static void copyYmlValues(File from, File to, int oldVersion) {

        // version 20 = 1.34 - last version before 2.0
        final List<String> version20KeysToKeep = Arrays.asList(
                "level-passive",
                "fine-tuning.min-level",
                "fine-tuning.max-level",
                "spawn-distance-levelling.active",
                "spawn-distance-levelling.variance.enabled",
                "spawn-distance-levelling.variance.max",
                "spawn-distance-levelling.variance.min",
                "spawn-distance-levelling.increase-level-distance",
                "spawn-distance-levelling.start-distance",
                "use-update-checker");

        // version 2.1.0 - these fields should be reset to default
        final List<String> version24Resets = Arrays.asList(
                "fine-tuning.additions.movement-speed",
                "fine-tuning.additions.attack-damage"
        );

        try {
            final List<String> oldConfigLines = Files.readAllLines(from.toPath(), StandardCharsets.UTF_8);
            final List<String> newConfigLines = Files.readAllLines(to.toPath(), StandardCharsets.UTF_8);

            final Map<String, FieldInfo> oldConfigMap = getMapFromConfig(oldConfigLines);
            final Map<String, FieldInfo> newConfigMap = getMapFromConfig(newConfigLines);

            final List<String> currentKey = new ArrayList<>();
            int keysMatched = 0;
            int valuesUpdated = 0;
            int valuesMatched = 0;

            for (int currentLine = 0; currentLine < newConfigLines.size(); currentLine++) {
                String line = newConfigLines.get(currentLine);
                final int depth = getFieldDepth(line);
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;

                if (line.contains(":")) {
                    final String[] lineSplit = line.split(":", 2);
                    String key = lineSplit[0].replace("\t","").trim();
                    final String keyOnly = key;

                    if (depth == 0)
                        currentKey.clear();
                    else if (currentKey.size() > depth) {
                        while (currentKey.size() > depth) currentKey.remove(currentKey.size() - 1);
                        key = getKeyFromList(currentKey, key);
                    }
                    else
                        key = getKeyFromList(currentKey, key);

                    int splitLength = lineSplit.length;
                    if (splitLength == 2 && lineSplit[1].isEmpty()) splitLength = 1;
                    if (splitLength == 1) {
                        // no value on a key, means we're likely increasing depth
                        currentKey.add(keyOnly);

                        if (oldVersion <= 20 && !version20KeysToKeep.contains(key)) continue;

                        // arrays go here:
                        if (oldConfigMap.containsKey(key) && newConfigMap.containsKey(key) && oldConfigMap.get(key).isList()){
                            final FieldInfo fiOld = oldConfigMap.get(key);
                            final FieldInfo fiNew = newConfigMap.get(key);
                            if (fiNew.isList()){
                                // add any values present in old list that might not be present in new
                                final String padding = IntStream.range(1, (depth + 1) * 3 - 1).mapToObj(index -> "" + ' ').collect(Collectors.joining());
                                for (String oldValue : fiOld.valueList){
                                    if (!fiNew.valueList.contains(oldValue)) {
                                        final String newline = padding + "- " + oldValue; // + "\r\n" + line;
                                        newConfigLines.add(currentLine + 1, newline);
                                        Utils.logger.info("added array value: " + oldValue);
                                    }
                                }
                            }
                        }
                    }
                    else if (splitLength == 2 && oldConfigMap.containsKey(key)){
                        keysMatched++;
                        final String value = lineSplit[1].trim();
                        final FieldInfo fi = oldConfigMap.get(key);
                        final String migratedValue = fi.simpleValue;

                        if (oldVersion <= 20 && !version20KeysToKeep.contains(key)) continue;
                        if (oldVersion < 24 && version24Resets.contains(key)) continue;
                        if (key.startsWith("file-version")) continue;

                        if (!value.equals(migratedValue)) {
                            valuesUpdated++;
                            Utils.logger.info("key: " + key + ", replacing: " + value + ", with: " + migratedValue);
                            line = line.replace(value, migratedValue);
                            newConfigLines.set(currentLine, line);
                        }
                        else
                            valuesMatched++;
                    }
                }
                else if (line.trim().startsWith("-")){
                    final String key = getKeyFromList(currentKey, null);
                    final String value = line.trim().substring(1).trim();

                    // we have an array value present in the new config but not the old, so it must've been removed
                    if (oldConfigMap.containsKey(key) && oldConfigMap.get(key).isList() && !oldConfigMap.get(key).valueList.contains(value)){
                        newConfigLines.remove(currentLine);
                        currentLine--;
                        Utils.logger.info("key: " + key + " removing value: " + value);
                    }
                }
            } // loop to next line

            Files.write(to.toPath(), newConfigLines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            Utils.logger.info("Migrated settings.yml successfully");
            Utils.logger.info(String.format("keys matched: %s, values matched: %s, values updated: %s", keysMatched, valuesMatched, valuesUpdated));
        } catch (Exception e) {
            Utils.logger.info("Failed to migrate config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Nonnull
    private static Map<String, FieldInfo> getMapFromConfig(List<String> input){
        final Map<String, FieldInfo> configMap = new HashMap<>();
        final List<String> currentKey = new ArrayList<>();

        for (String line : input) {
            // step 1, collect level 1 key-pairs from old config
            final int depth = getFieldDepth(line);
            line = line.replace("\t", "").trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.contains(":")) {
                final String[] lineSplit = line.split(":", 2);
                String key = lineSplit[0].replace("\t", "").trim();
                final String origKey = key;

                if (depth == 0)
                    currentKey.clear();
                else {
                    if (currentKey.size() > depth)
                        while (currentKey.size() > depth) currentKey.remove(currentKey.size() - 1);
                    key = getKeyFromList(currentKey, key);
                }

                int splitLength = lineSplit.length;
                if (splitLength == 2 && lineSplit[1].isEmpty()) splitLength = 1;
                if (splitLength == 1) {
                    currentKey.add(origKey);
                }
                else if (splitLength == 2){
                    final String value = lineSplit[1].trim();
                    configMap.put(key, new FieldInfo(value, depth));
                }
            }
            else if (line.startsWith("-")){
                final String key = getKeyFromList(currentKey, null);
                final String value = line.trim().substring(1).trim();
                if (configMap.containsKey(key))
                    configMap.get(key).addListValue(value);
                else
                    configMap.put(key, new FieldInfo(value, depth));
            }
        }

        return configMap;
    }
}
