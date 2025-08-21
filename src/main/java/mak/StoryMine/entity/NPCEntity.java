package mak.StoryMine.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mak.StoryMine.animation.AnimationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
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

import javax.annotation.Nullable;

public class NPCEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_ANIMATION_STATE =
            SynchedEntityData.defineId(NPCEntity.class, EntityDataSerializers.STRING);

    private int animationTimer;

    private BlockPos walkingTarget;
    private double walkingSpeed = 1.0;

    private JsonArray currentAnimFrames = null;
    private int animTick = 0;
    private int frameDelay = 20;

    private ResourceLocation mimicType;


    public NPCEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
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

        if (currentAnimFrames != null) {
            if (this.tickCount % frameDelay == 0) {
                int index = animTick / frameDelay;
                if (index < currentAnimFrames.size()) {
                    String frameState = currentAnimFrames.get(index).getAsString();
                    this.setAnimationState(frameState);
                    animTick++;
                } else {
                    currentAnimFrames = null;
                    this.setAnimationState("idle");
                }
            }
        } else {
            if (!"idle".equals(this.getAnimationState()) && --this.animationTimer <= 0) {
                this.entityData.set(DATA_ANIMATION_STATE, "idle");
            }
        }

        if (walkingTarget != null) {
            if (this.blockPosition().distSqr(walkingTarget) > 2) {
                this.getNavigation().moveTo(
                        walkingTarget.getX(),
                        walkingTarget.getY(),
                        walkingTarget.getZ(),
                        walkingSpeed
                );
            } else {
                clearWalkingTarget();
            }
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
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {}

    public void setWalkingTarget(BlockPos pos, double speed) {
        this.walkingTarget = pos;
        this.walkingSpeed = speed;
        this.setNoAi(false);
    }

    public BlockPos getWalkingTarget() {
        return this.walkingTarget;
    }

    public double getWalkingSpeed() {
        return this.walkingSpeed;
    }

    public boolean hasWalkingTarget() {
        return this.walkingTarget != null;
    }

    public void clearWalkingTarget() {
        this.walkingTarget = null;
        this.getNavigation().stop();
        this.setNoAi(true);
    }

    public void playAnimation(String animName) {
        JsonObject anim = AnimationRegistry.get(animName);
        if (anim == null) {
            System.out.println("[StoryMine] Animation " + animName + " not found!");
            return;
        }

        if (anim.has("state")) {
            this.setAnimationState(anim.get("state").getAsString());
        }

        if (anim.has("frames")) {
            this.currentAnimFrames = anim.getAsJsonArray("frames");
            this.animTick = 0;
            this.frameDelay = anim.has("frameDelay") ? anim.get("frameDelay").getAsInt() : 20;
        }
    }

    public void setMimicType(ResourceLocation type) {
        this.mimicType = type;
    }

    @Nullable
    public ResourceLocation getMimicType() {
        return this.mimicType;
    }
}