package dev.factoryholograms.client;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.HologramLabel;

/** The item icon of a world hologram currently under the crosshair. */
@SideOnly(Side.CLIENT)
public final class HologramHover {
    public final HologramLabel label;
    /** Inputs first, then outputs, in occupied-slot order. */
    public final int slot;
    public final ItemStack stack;
    public final double distance;

    public HologramHover(HologramLabel label, int slot, ItemStack stack, double distance) {
        this.label = label;
        this.slot = slot;
        this.stack = stack;
        this.distance = distance;
    }
}
