package dev.factoryholograms;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;

import cpw.mods.fml.relauncher.FMLInjectionData;

import net.minecraftforge.common.config.Configuration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HologramConfigTest {
    private static Field minecraftHome;
    private static Object previousHome;

    @BeforeClass public static void initializeForgeConfigEnvironment() throws Exception {
        // FML normally supplies this before mod preInit; headless JUnit has no launcher.
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        previousHome = minecraftHome.get(null);
        minecraftHome.set(null, new File("."));
    }

    @AfterClass public static void restoreForgeConfigEnvironment() throws Exception {
        minecraftHome.set(null, previousHome);
    }

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void defaultsAreGlobalAndSaved() throws Exception {
        File file = new File(temporary.newFolder(), "factoryholograms.cfg");
        HologramConfig.load(file);
        assertTrue(file.isFile());
        assertTrue(HologramConfig.enabled);
        assertEquals(12, HologramConfig.renderDistance);
        assertEquals(0.85F, HologramConfig.opacity, 0.00001F);
        assertEquals(0x55DDFF, HologramConfig.color);
        assertEquals(0xFFFFFF, HologramConfig.textColor);
        assertEquals(5, HologramConfig.getConfiguration().getCategory("display").size());
                assertEquals("cyan", HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                assertEquals("cyan", HologramConfig.getConfiguration().getCategory("display").get("color").getDefault());
                HologramConfig.load(file);
                assertEquals(0x55DDFF, HologramConfig.color);
                assertEquals("cyan", HologramConfig.getConfiguration().getCategory("display").get("color").getString());
    }

    @Test public void textColorsPersistIndependentlyFromBlackBackground() throws Exception {
        File file = new File(temporary.newFolder(), "factoryholograms.cfg");
        HologramConfig.load(file);
        HologramConfig.getConfiguration().getCategory("display").get("color").set("black");
        for (String value : new String[] { "white", "black", " RED ", "#123456", "invalid" }) {
            HologramConfig.getConfiguration().getCategory("display").get("textColor").set(value);
            HologramConfig.sync();
            int expected = "invalid".equals(value) ? 0xFFFFFF : HologramConfig.parseColor(value);
            assertEquals(expected, HologramConfig.textColor);
            HologramConfig.load(file);
            assertEquals(expected, HologramConfig.textColor);
            assertEquals(0, HologramConfig.color);
        }
        for (HologramPalette entry : HologramPalette.values()) {
            HologramConfig.getConfiguration().getCategory("display").get("textColor").set(entry.id);
            HologramConfig.sync();
            HologramConfig.load(file);
            assertEquals(entry.rgb, HologramConfig.textColor);
            assertEquals(entry.id, HologramConfig.getConfiguration().getCategory("display").get("textColor").getString());
            assertEquals(0, HologramConfig.color);
        }
    }

    @Test public void togglePersistsAcrossReload() throws Exception {
        File file = new File(temporary.newFolder(), "factoryholograms.cfg");
        HologramConfig.load(file);
        HologramConfig.setEnabled(false);
        HologramConfig.load(file);
        assertFalse(HologramConfig.enabled);
        HologramConfig.setEnabled(true);
        HologramConfig.load(file);
        assertTrue(HologramConfig.enabled);
    }

    @Test public void guiChangesApplyToAllDisplayProperties() throws Exception {
        HologramConfig.load(new File(temporary.newFolder(), "factoryholograms.cfg"));
        Configuration config = HologramConfig.getConfiguration();
        config.get("display", "enabled", true).set(false);
        config.get("display", "renderDistance", 12).set(32);
        config.get("display", "opacity", 0.85).set(0.4);
        config.get("display", "color", "cyan").set("black");
        HologramConfig.sync();
        assertFalse(HologramConfig.enabled);
        assertEquals(32, HologramConfig.renderDistance);
        assertEquals(0.4F, HologramConfig.opacity, 0.00001F);
        assertEquals(0x000000, HologramConfig.color);
                assertEquals("black", config.getCategory("display").get("color").getString());
    }

    @Test public void badValuesAreClampedAndSavedInCanonicalForm() throws Exception {
        HologramConfig.load(new File(temporary.newFolder(), "factoryholograms.cfg"));
        Configuration config = HologramConfig.getConfiguration();
        config.get("display", "renderDistance", 12).set(Integer.MAX_VALUE);
        config.get("display", "opacity", 0.85).set(-1.0);
        config.get("display", "color", "cyan").set("wrong");
        HologramConfig.sync();
        assertEquals(64, HologramConfig.renderDistance);
        assertEquals(0, HologramConfig.opacity, 0);
        assertEquals("cyan", config.getCategory("display").get("color").getString());
        config.getCategory("display").get("renderDistance").set(-10);
        config.getCategory("display").get("opacity").set(2.0);
        HologramConfig.sync();
        assertEquals(4, HologramConfig.renderDistance);
        assertEquals(1, HologramConfig.opacity, 0);
    }

    @Test public void nonFiniteOpacityCannotReachRenderer() throws Exception {
        HologramConfig.load(new File(temporary.newFolder(), "factoryholograms.cfg"));
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            HologramConfig.getConfiguration().getCategory("display").get("opacity").set(value);
            HologramConfig.sync();
            assertEquals(HologramConfig.DEFAULT_OPACITY, HologramConfig.opacity, 0);
        }
    }

    @Test public void namedColorsPersistAcrossReload() throws Exception {
            File file = new File(temporary.newFolder(), "factoryholograms.cfg");
            HologramConfig.load(file);
            for (HologramPalette entry : HologramPalette.values()) {
                HologramConfig.getConfiguration().getCategory("display").get("color").set(" " + entry.id.toUpperCase(Locale.ROOT) + " ");
                HologramConfig.sync();
                assertEquals(entry.rgb, HologramConfig.color);
                assertEquals(entry.id, HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                HologramConfig.load(file);
                assertEquals(entry.rgb, HologramConfig.color);
                assertEquals(entry.id, HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                assertEquals(5, HologramConfig.getConfiguration().getCategory("display").size());
            }
        }

        @Test public void legacyPaletteHexMigratesWithoutChangingRgb() throws Exception {
            for (HologramPalette entry : HologramPalette.values()) {
                File file = new File(temporary.newFolder(), "factoryholograms.cfg");
                Configuration legacy = new Configuration(file);
                legacy.load();
                legacy.get("display", "color", "55DDFF").set("#" + String.format(Locale.ROOT, "%06x", entry.rgb));
                legacy.save();
                HologramConfig.load(file);
                assertEquals(entry.rgb, HologramConfig.color);
                assertEquals(entry.id, HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                HologramConfig.load(file);
                assertEquals(entry.rgb, HologramConfig.color);
                assertEquals(entry.id, HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                assertEquals(5, HologramConfig.getConfiguration().getCategory("display").size());
            }
        }

        @Test public void legacyCustomHexRemainsExactAcrossMigrationAndReload() throws Exception {
            for (String hex : new String[] { "000001", "010203", "123456", "FFaa33", "aBcDeF" }) {
                File file = new File(temporary.newFolder(), "factoryholograms.cfg");
                Configuration legacy = new Configuration(file);
                legacy.load();
                legacy.get("display", "enabled", true).set(false);
                legacy.get("display", "renderDistance", 12).set(24);
                legacy.get("display", "opacity", 0.85).set(0.5);
                legacy.get("display", "color", "55DDFF").set(" #" + hex + " ");
                legacy.save();
                for (int reload = 0; reload < 2; reload++) {
                    HologramConfig.load(file);
                    assertEquals(Integer.parseInt(hex, 16), HologramConfig.color);
                    assertEquals(hex.toUpperCase(Locale.ROOT), HologramConfig.getConfiguration().getCategory("display").get("color").getString());
                    assertFalse(HologramConfig.enabled);
                    assertEquals(24, HologramConfig.renderDistance);
                    assertEquals(0.5F, HologramConfig.opacity, 0);
                    assertEquals(5, HologramConfig.getConfiguration().getCategory("display").size());
                }
            }
        }

        @Test public void colorsAcceptNamedTokensAndLegacyRgbHex() {
            for (HologramPalette entry : HologramPalette.values()) {
                assertEquals(entry.rgb, HologramConfig.parseColor(entry.id));
                assertEquals(entry.rgb, HologramConfig.parseColor(" " + entry.id.toUpperCase(Locale.ROOT) + " "));
            }
        assertEquals(0, HologramConfig.parseColor("000000"));
        assertEquals(0xFFFFFF, HologramConfig.parseColor(" #ffffff "));
        assertEquals(0xFFAA33, HologramConfig.parseColor("FFaa33"));
        for (String text : new String[] { null, "", "#FFF", "-12345", "1234567", "0xAABBCC", "XXFF00", "FFFFFFFF", "unknown", "#black", "grey" }) {
            assertEquals(HologramConfig.DEFAULT_COLOR, HologramConfig.parseColor(text));
        }
    }
}
