package dev.factoryholograms;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class LocalWorldKeyTest {
    private static MinecraftTestSupport minecraft;
    @BeforeClass public static void bootstrap() throws Exception { minecraft = MinecraftTestSupport.open(LocalWorldKeyTest.class.getName() + "$Fixture"); }
    @AfterClass public static void closeMinecraft() throws Exception { if (minecraft != null) minecraft.close(); }
    @Test public void serverNormalizationIsStableAndLocaleIndependent() throws Exception { minecraft.run("serverNormalizationIsStableAndLocaleIndependent"); }
    @Test public void ipv6BracketsAndPortsAreUnambiguous() throws Exception { minecraft.run("ipv6BracketsAndPortsAreUnambiguous"); }
    @Test public void folderNamesKeepCaseAndUseSeparateNamespace() throws Exception { minecraft.run("folderNamesKeepCaseAndUseSeparateNamespace"); }
    @Test public void filesAreIsolatedWithoutUsingIdentityAsPath() throws Exception { minecraft.run("filesAreIsolatedWithoutUsingIdentityAsPath"); }
    @Test public void blankIdentitiesAreRejected() throws Exception { minecraft.run("blankIdentitiesAreRejected"); }

    public static final class Fixture {
        public static void serverNormalizationIsStableAndLocaleIndependent() {
            String expected = LocalWorldKey.server("example.invalid");
            assertTrue(expected.matches("[0-9a-f]{64}"));
            assertEquals(expected, LocalWorldKey.server("  EXAMPLE.INVALID:25565  "));
            assertNotEquals(expected, LocalWorldKey.server("example.invalid:25566"));
            assertNotEquals(expected, LocalWorldKey.server("other.invalid"));
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(new Locale("tr", "TR"));
                assertEquals(expected, LocalWorldKey.server("EXAMPLE.INVALID"));
            } finally { Locale.setDefault(previous); }
        }

        public static void ipv6BracketsAndPortsAreUnambiguous() {
            String expected = LocalWorldKey.server("2001:db8::1");
            assertEquals(expected, LocalWorldKey.server(" [2001:DB8::1]:25565 "));
            assertEquals(expected, LocalWorldKey.server("[2001:db8::1]"));
            assertNotEquals(expected, LocalWorldKey.server("[2001:db8::1]:25566"));
            // In an unbracketed IPv6 address, :25565 is an address component, not a port.
            assertNotEquals(expected, LocalWorldKey.server("2001:db8::1:25565"));
            assertEquals(LocalWorldKey.server("::1"), LocalWorldKey.server("[::1]:25565"));
        }

        public static void folderNamesKeepCaseAndUseSeparateNamespace() {
            assertEquals(LocalWorldKey.singleplayer("World"), LocalWorldKey.singleplayer(" World "));
            assertNotEquals(LocalWorldKey.singleplayer("World"), LocalWorldKey.singleplayer("world"));
            assertNotEquals(LocalWorldKey.server("world"), LocalWorldKey.singleplayer("world"));
            assertTrue(LocalWorldKey.singleplayer("世界").matches("[0-9a-f]{64}"));
        }

        public static void filesAreIsolatedWithoutUsingIdentityAsPath() throws Exception {
            Path directory = Files.createTempDirectory("hologram-world-key-");
            try {
                File base = directory.toFile();
                File normal = LocalWorldKey.dataFile(base, false, "example.invalid", 0);
                assertEquals(new File(new File(new File(new File(base, "factoryholograms"), "servers"),
                    LocalWorldKey.server("example.invalid")), "DIM0.dat"), normal);
                assertEquals(normal, LocalWorldKey.dataFile(base, false, "EXAMPLE.INVALID:25565", 0));
                assertNotEquals(normal, LocalWorldKey.dataFile(base, false, "example.invalid", -1));
                assertNotEquals(normal, LocalWorldKey.dataFile(base, false, "other.invalid", 0));
                assertNotEquals(normal, LocalWorldKey.dataFile(base, true, "example.invalid", 0));
                assertEquals("DIM-1.dat", LocalWorldKey.dataFile(base, true, "World", -1).getName());
                for (boolean local : new boolean[] {false, true}) {
                    File hostile = LocalWorldKey.dataFile(base, local, "../../outside\\folder:C", Integer.MIN_VALUE);
                    Path category = directory.resolve("factoryholograms").resolve(local ? "singleplayer" : "servers");
                    assertEquals(category, hostile.toPath().getParent().getParent());
                    assertTrue(hostile.getParentFile().getName().matches("[0-9a-f]{64}"));
                }
                assertFalse(normal.exists());
                assertFalse(new File(base, "factoryholograms").exists());
            } finally { Files.delete(directory); }
        }

        public static void blankIdentitiesAreRejected() {
            for (String invalid : new String[] {null, "", "  \t\r\n"}) {
                assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.server(invalid));
                assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.singleplayer(invalid));
                assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.dataFile(new File("."), false, invalid, 0));
                assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.dataFile(new File("."), true, invalid, 0));
            }
            assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.dataFile(null, false, "host", 0));
            assertThrows(IllegalArgumentException.class, () -> LocalWorldKey.server(":25565"));
        }
    }
}
