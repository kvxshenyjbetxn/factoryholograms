package dev.factoryholograms.client;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Optional recipe lookup for hovered world icons; implemented only when NEI is present. */
@SideOnly(Side.CLIENT)
public interface WorldRecipeHooks {
    int recipeKey();

    int usageKey();

    boolean showRecipe(ItemStack stack);

    boolean showUsage(ItemStack stack);
}
