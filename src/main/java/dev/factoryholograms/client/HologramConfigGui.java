package dev.factoryholograms.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.config.ConfigElement;

import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.GuiConfigEntries;
import cpw.mods.fml.client.config.IConfigElement;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.FactoryHolograms;
import dev.factoryholograms.HologramConfig;
import dev.factoryholograms.HologramPalette;

@SideOnly(Side.CLIENT)
public final class HologramConfigGui extends GuiConfig {

    public HologramConfigGui(GuiScreen parent) {
        super(parent, displayElements(), FactoryHolograms.ID, false, false, title());
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> displayElements() {
        List<IConfigElement> elements = new ConfigElement<Object>(HologramConfig.getConfiguration().getCategory("display")).getChildElements();
        for (int i = 0; i < elements.size(); i++) {
            if ("color".equals(elements.get(i).getName())) {
                elements.set(i, new ConfigElement<String>(HologramConfig.getConfiguration().getCategory("display").get("color")) {
                    @Override public Class<? extends GuiConfigEntries.IConfigEntry> getConfigEntryClass() {
                        return ColorEntry.class;
                    }
                });
            }
        }
        return elements;
    }

    private static String title() {
        String key = "gui.factoryholograms.config.title";
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : "Factory Holograms - Display";
    }

    @SideOnly(Side.CLIENT)
    public static class ColorEntry extends GuiConfigEntries.SelectValueEntry {
        public ColorEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<String> configElement) {
            super(owningScreen, owningEntryList, configElement, colorChoices(configElement.get().toString()));
        }

        private static Map<Object, String> colorChoices(String current) {
            Map<Object, String> choices = new LinkedHashMap<Object, String>();
            for (HologramPalette entry : HologramPalette.values()) {
                choices.put(entry.id, StatCollector.translateToLocal(entry.languageKey()));
            }
            if (!choices.containsKey(current)) {
                choices.put(current, StatCollector.translateToLocalFormatted("config.factoryholograms.color.custom", current));
            }
            return choices;
        }

        @Override public void updateValueButtonText() {
            String label = selectableValues.get(currentValue);
            btnValue.displayString = label == null ? String.valueOf(currentValue) : label;
        }
    }
}
