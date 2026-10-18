package dev.factoryholograms;

import org.apache.logging.log4j.Logger;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = FactoryHolograms.ID, name = "Factory Holograms", version = "0.7.1", acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*", guiFactory = "dev.factoryholograms.client.HologramGuiFactory")
public class FactoryHolograms {
    public static final String ID = "factoryholograms";
    @SidedProxy(clientSide = "dev.factoryholograms.client.ClientProxy", serverSide = "dev.factoryholograms.CommonProxy")
    public static CommonProxy proxy;
    public static Logger log;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        HologramConfig.load(event.getSuggestedConfigurationFile());
        proxy.init();
    }
}
