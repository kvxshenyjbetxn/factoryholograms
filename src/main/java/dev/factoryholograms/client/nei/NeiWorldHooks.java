package dev.factoryholograms.client.nei;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import dev.factoryholograms.client.WorldRecipeHooks;

/** Opens NEI recipe/usage screens for items shown in world holograms. */
@SideOnly(Side.CLIENT)
public final class NeiWorldHooks implements WorldRecipeHooks {

    @Override
    public int recipeKey() {
        return NEIClientConfig.isLoaded() ? NEIClientConfig.getKeyBinding("recipe.recipe") : 0;
    }

    @Override
    public int usageKey() {
        return NEIClientConfig.isLoaded() ? NEIClientConfig.getKeyBinding("recipe.usage") : 0;
    }

    @Override
    public boolean showRecipe(ItemStack stack) {
        ItemStack copy = copyOf(stack);
        return copy != null && GuiCraftingRecipe.openRecipeGui("item", copy);
    }

    @Override
    public boolean showUsage(ItemStack stack) {
        ItemStack copy = copyOf(stack);
        return copy != null && GuiUsageRecipe.openRecipeGui("item", copy);
    }

    private static ItemStack copyOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null || !NEIClientConfig.isLoaded()) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
