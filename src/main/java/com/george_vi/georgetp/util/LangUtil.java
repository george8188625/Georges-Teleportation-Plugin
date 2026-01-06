package com.george_vi.georgetp.util;

import com.george_vi.georgetp.GTPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class LangUtil {
    YamlConfiguration config;

    final GTPlugin plugin;

    public LangUtil(GTPlugin plugin) {
        this.plugin = plugin;
        plugin.saveResource("messages.yml", false);
        File file = new File(plugin.getDataFolder(), "messages.yml");

        if (!file.exists())
            plugin.getLogger().severe("messages.yml doesn't exist!");
        config = YamlConfiguration.loadConfiguration(file);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));

            config.setDefaults(defConfig);
            config.options().copyDefaults(true);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml");
        }

    }

    public Component getMessage(String id, Map<String, String> placeholders) {
        if (!config.contains(id))
            return Component.empty();
        String text = config.getString(id);
        return text == null ? Component.empty() : parse(text, placeholders);
    }

    public Component getMessage(String id) {
        return getMessage(id, Collections.emptyMap());
    }

    public static Component parse(String string, Map<String, String> placeholders) {
        List<Component> components = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder tagBuilder = new StringBuilder();
        int style = 0xffffff;
        boolean inTag = false;

        char[] arr = string.toCharArray();

        for (int i = 0; i < arr.length; i++) {
            char c = arr[i];
            if (c == '%') {
                if (inTag) {
                    inTag = false;
                    String tagContents = tagBuilder.toString();
                    tagBuilder.setLength(0);
                    int lastStyle = style;

                    if (tagContents.startsWith("c ")) {
                        style = setColor(style, Integer.parseInt(tagContents.substring(2), 16));
                    } else if (tagContents.equals("b")) {
                        style = setStyle(style, BOLD_BIT, true);
                    } else if (tagContents.equals("i")) {
                        style = setStyle(style, ITALIC_BIT, true);
                    } else if (tagContents.equals("u")) {
                        style = setStyle(style, UNDERLINE_BIT, true);
                    } else if (tagContents.equals("s")) {
                        style = setStyle(style, STRIKE_BIT, true);
                    } else if (tagContents.equals("o")) {
                        style = setStyle(style, OBFUSCATED_BIT, true);
                    } else if (tagContents.equals("gblue")) {
                        style = setColor(style, 0x80bfff);
                    } else if (tagContents.equals("glightblue")) {
                        style = setColor(style, 0xb3d9ff);
                    } else if (tagContents.equals("glightred")) {
                        style = setColor(style, 0xff8a8a);
                    } else if (tagContents.equals("r")) {
                        style = 0xffffff;
                    } else {
                        boolean found = false;
                        for (NamedTextColor textColor : NamedTextColor.NAMES.values()) {
                            if (textColor.toString().equals(tagContents)) {
                                style = setColor(style, textColor.value());
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            String replacement = placeholders.get(tagContents);
                            if (replacement != null)
                                textBuilder.append(replacement);
                        }

                    }
                    if (lastStyle != style) {
                        if (textBuilder.isEmpty())
                            continue;
                        String text = textBuilder.toString();
                        textBuilder.setLength(0);
                        components.add(applyPackedStyle(lastStyle, text));
                    }
                } else {
                    inTag = true;
                    tagBuilder.setLength(0);
                }
            } else if (c == '\\') {
                if (i == arr.length - 1)
                    break;
                if (arr[i + 1] == '\\') {
                    if (inTag)
                        tagBuilder.append('\\');
                    else
                        textBuilder.append('\\');
                } else if (arr[i + 1] == '%')
                    if (inTag)
                        tagBuilder.append('%');
                    else
                        textBuilder.append('%');
            } else {
                if (inTag)
                    tagBuilder.append(c);
                else
                    textBuilder.append(c);
            }

        }

        String text = textBuilder.toString();
        components.add(applyPackedStyle(style, text));
        return Component.empty().append(components);
    }

    // I'm a performance freak so instead of creating a class I did this for no reason
    private static final int BOLD_BIT          = 1 << 24;
    private static final int ITALIC_BIT        = 1 << 25;
    private static final int STRIKE_BIT        = 1 << 26;
    private static final int UNDERLINE_BIT     = 1 << 27;

    private static final int OBFUSCATED_BIT    = 1 << 28;

    private static int packStyleState(
            int color,
            boolean bold,
            boolean italic,
            boolean strikethrough,
            boolean underline,
            boolean obfuscated) {
        int state = color & 0xFFFFFF;

        if (bold)          state |= BOLD_BIT;
        if (italic)        state |= ITALIC_BIT;
        if (strikethrough) state |= STRIKE_BIT;
        if (underline)     state |= UNDERLINE_BIT;
        if (obfuscated)    state |= OBFUSCATED_BIT;

        return state;
    }

    private static Component applyPackedStyle(int packedStyle, String text) {
        Style style = Style.style(TextColor.color(packedStyle & 0xFFFFFF));
        if ((packedStyle & BOLD_BIT) != 0) style = style.decorate(TextDecoration.BOLD);
        if ((packedStyle & ITALIC_BIT) != 0) style = style.decorate(TextDecoration.ITALIC);
        if ((packedStyle & STRIKE_BIT) != 0) style = style.decorate(TextDecoration.STRIKETHROUGH);
        if ((packedStyle & UNDERLINE_BIT) != 0) style = style.decorate(TextDecoration.UNDERLINED);
        if ((packedStyle & OBFUSCATED_BIT) != 0) style = style.decorate(TextDecoration.OBFUSCATED);

        return Component.text(text, style);
    }

    private static int setStyle(int style, int bt, boolean val) {
        return val ? (style | bt) : (style & (~bt));
    }

    private static boolean isStyle(int style, int bt) {
        return (style & bt) != 0;
    }

    private static int setColor(int style, int color) {
        return (style & (~0xFFFFFF)) | color;
    }
}
