package dev.factoryholograms;

import static org.junit.Assert.*;

import cpw.mods.fml.common.Mod;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class ClientOnlyContractTest {
    @Test public void acceptsServersWithoutThisMod() {
        Mod mod = FactoryHolograms.class.getAnnotation(Mod.class);
        assertEquals("*", mod.acceptableRemoteVersions());
        assertEquals(0, FactoryHolograms.class.getInterfaces().length);
        assertThrows(NoSuchFieldException.class, () -> FactoryHolograms.class.getDeclaredField("network"));
    }

    @Test public void editorMasksAllVanillaInputAndClosePaths() throws Exception {
        Class<?> editor = Class.forName("dev.factoryholograms.client.HologramGui", false, getClass().getClassLoader());
        assertEquals("net.minecraft.client.gui.inventory.GuiContainer", editor.getSuperclass().getName());
        editor.getDeclaredMethod("initGui");
        editor.getDeclaredMethod("handleInput");
        editor.getDeclaredMethod("handleKeyboardInput");
        editor.getDeclaredMethod("handleMouseInput");
        editor.getDeclaredMethod("keyTyped", char.class, int.class);
        editor.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        editor.getDeclaredMethod("mouseMovedOrUp", int.class, int.class, int.class);
        editor.getDeclaredMethod("mouseClickMove", int.class, int.class, int.class, long.class);
        editor.getDeclaredMethod("handleMouseClick", net.minecraft.inventory.Slot.class, int.class, int.class, int.class);
        editor.getDeclaredMethod("onGuiClosed");
        editor.getDeclaredMethod("drawScreen", int.class, int.class, float.class);
        assertEquals(HologramLabel.class, editor.getDeclaredMethod("getPreviewLabel").getReturnType());
        assertThrows(NoSuchFieldException.class, () -> editor.getDeclaredField("guiLeft"));
        assertThrows(NoSuchFieldException.class, () -> editor.getDeclaredField("POSITION_FRONT"));
    }

    @Test public void localDisplayContainerHasNoSlotsOrPlayerSideEffects() throws Exception {
        Class<?> type = Class.forName("dev.factoryholograms.client.HologramGui$LocalDisplayContainer");
        net.minecraft.inventory.Container container = (net.minecraft.inventory.Container) type.getDeclaredConstructor().newInstance();
        assertTrue(container.inventorySlots.isEmpty());
        assertNull(container.slotClick(0, 0, 0, null));
        assertNull(container.transferStackInSlot(null, 0));
        container.onContainerClosed(null);
    }

    @Test public void neiCannotPrependADuplicateSuperTick() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader("dev.factoryholograms/client/HologramGui").accept(node, 0);
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.name.equals("updateScreen")) continue;
            AbstractInsnNode first = method.instructions.getFirst();
            while (first.getOpcode() < 0) first = first.getNext();
            assertEquals(Opcodes.ALOAD, first.getOpcode());
            AbstractInsnNode second = first.getNext();
            while (second.getOpcode() < 0) second = second.getNext();
            assertEquals(Opcodes.INVOKESPECIAL, second.getOpcode());
            MethodInsnNode call = (MethodInsnNode) second;
            assertEquals("net/minecraft/client/gui/inventory/GuiContainer", call.owner);
            assertEquals("updateScreen", call.name);
            int superCalls = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode && instruction.getOpcode() == Opcodes.INVOKESPECIAL
                    && ((MethodInsnNode) instruction).name.equals("updateScreen")) superCalls++;
            }
            assertEquals(1, superCalls);
            return;
        }
        fail("Missing tick override");
    }

    @Test public void editorAndBridgeNeverDispatchInventoryOrCheatActions() throws Exception {
        String[] classes = {"dev/factoryholograms/client/HologramGui", "dev/factoryholograms/client/HologramGui$LocalDisplayContainer",
            "dev/factoryholograms/client/nei/NeiItemBrowser"};
        for (String type : classes) {
            ClassNode node = new ClassNode(Opcodes.ASM5); new ClassReader(type).accept(node, 0);
            for (Object value : node.methods) for (AbstractInsnNode instruction : ((MethodNode) value).instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    assertFalse(type, field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("openContainer"));
                }
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertFalse(type + " calls " + call.name, call.name.equals("windowClick") || call.name.equals("closeScreen")
                    || call.name.equals("sendToServer") || call.name.equals("cheatItem") || call.name.equals("sendGiveItem")
                    || call.name.equals("sendDummySlotSet"));
                if (call.owner.equals("codechicken/nei/guihook/GuiContainerManager")) {
                    assertFalse(call.name, call.name.equals("mouseClicked") || call.name.equals("mouseUp")
                        || call.name.equals("firstKeyTyped") || call.name.equals("lastKeyTyped"));
                }
            }
        }
    }

    @Test public void previewGetterCannotPersistOrSendData() throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader("dev/factoryholograms/client/HologramGui").accept(node, 0);
        boolean found = false;
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if (!method.name.equals("getPreviewLabel")) continue;
            found = true;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertFalse(call.owner, call.owner.equals("dev/factoryholograms/LocalLabelStore")
                    || call.owner.startsWith("java/io/") || call.owner.startsWith("java/nio/file/"));
                assertFalse(call.name, call.name.equals("saveDraft") || call.name.equals("sendToServer"));
            }
        }
        assertTrue(found);
    }

    @Test public void noBrowserFallbackIsFullyInert() {
        dev.factoryholograms.client.ItemBrowser browser = dev.factoryholograms.client.ItemBrowser.NONE;
        browser.beforeDraw(null); browser.draw(null, 0, 0); browser.drawTooltips(null, 0, 0); browser.close(null);
        assertNull(browser.stackAt(null, 0, 0));
        assertFalse(browser.mouseDown(null, 0, 0, 0));
        assertFalse(browser.mouseWheel(null, 0, 0, 1));
        assertFalse(browser.keyTyped(null, 'x', 0));
    }
}
