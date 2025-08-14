package mak.StoryMine.entity;

import mak.StoryMine.StoryMine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class NPCEntityType {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, StoryMine.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<NPCEntity>> NPC = ENTITY_TYPES.register("npc",
            () -> EntityType.Builder.of(NPCEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build("storymine:npc"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}