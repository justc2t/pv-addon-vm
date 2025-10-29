package org.justc2t.voiceMessages.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.justc2t.voiceMessages.VoiceMessages;

import java.util.Map;

public class ConfigManager {

    private static VoiceMessages plugin;
    public ConfigManager(VoiceMessages plugin){
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        plugin.getConfig().options().copyDefaults(true);
    }

    public static Component getFormatedMessage(String path){
        return MiniMessage.miniMessage().deserialize(plugin.getConfig().getString(path));
    }

    public static Component getMessageWithPlaceholer(String path, Map<String, String> placeholders){
        if (path == null) {
            return Component.empty();
        }
        String message = plugin.getConfig().getString(path);

        message = formatPlaceholders(message, placeholders);
        return MiniMessage.miniMessage().deserialize(message);
    }

    public static String formatPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null) return "";
        if (placeholders == null || placeholders.isEmpty()) return input;

        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result = result.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return result;
    }

    public static String getString(String path){
        return plugin.getConfig().getString(path);
    }

}
