package mak.StoryMine.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class NPCEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_ANIMATION_STATE =
            SynchedEntityData.defineId(NPCEntity.class, EntityDataSerializers.STRING);

    private int animationTimer;

    public NPCEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANIMATION_STATE, "idle");
    }

    public String getAnimationState() {
        return this.entityData.get(DATA_ANIMATION_STATE);
    }

    public void setAnimationState(String state) {
        this.entityData.set(DATA_ANIMATION_STATE, state);
        this.animationTimer = 20;
    }

    @Override
    public void tick() {
        super.tick();
        if (!"idle".equals(this.getAnimationState()) && --this.animationTimer <= 0) {
            this.entityData.set(DATA_ANIMATION_STATE, "idle");
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.removeAllGoals(null);
        this.targetSelector.removeAllGoals(null);

        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            String npcName = this.getCustomName() != null ?
                    this.getCustomName().getString() : "NPC";

            this.level().players().forEach(p ->
                    p.sendSystemMessage(Component.literal(
                            npcName + ": Привет, " + player.getName().getString() + "!"
                    ))
            );

            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}
}