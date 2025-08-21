package mak.StoryMine;

import mak.StoryMine.animation.AnimationRegistry;
import mak.StoryMine.entity.NPCEntity;
import mak.StoryMine.entity.NPCEntityType;
import mak.StoryMine.entity.NPCRenderer;
import mak.StoryMine.script.ScriptExecutor;
import mak.StoryMine.script.ScriptManager;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.nio.file.Path;

@Mod(StoryMine.MOD_ID)
public class StoryMine {
    public static final String MOD_ID = "storymine";

    public StoryMine(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerAttributes);

        NPCEntityType.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path animsFolder = gameDir.resolve("scripts").resolve("anims");

        AnimationRegistry.loadAnimations(animsFolder);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {}

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(NPCEntityType.NPC.get(), NPCEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ScriptManager.init(event.getServer());
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            EntityRenderers.register(NPCEntityType.NPC.get(), NPCRenderer::new);
        }
    }
}