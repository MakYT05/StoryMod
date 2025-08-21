package mak.StoryMine.entity;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import mak.StoryMine.StoryMine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class NPCRenderer extends LivingEntityRenderer<NPCEntity, PlayerModel<NPCEntity>> {
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");

    public NPCRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(NPCEntity entity) {
        ResourceLocation custom = getCustomTexture(entity);
        return custom != null ? custom : DEFAULT_TEXTURE;
    }

    @Override
    public void render(NPCEntity npc, float yaw, float partialTicks, PoseStack stack, MultiBufferSource buffer, int light) {
        ResourceLocation mimicId = npc.getMimicType();
        if (mimicId != null) {
            EntityType<?> mimicType = BuiltInRegistries.ENTITY_TYPE.get(mimicId);
            if (mimicType != null) {
                Entity fakeEntity = mimicType.create(npc.level());
                if (fakeEntity != null) {
                    fakeEntity.setPos(npc.getX(), npc.getY(), npc.getZ());
                    fakeEntity.setYRot(yaw);

                    var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                    var renderer = dispatcher.getRenderer(fakeEntity);
                    renderer.render(fakeEntity, yaw, partialTicks, stack, buffer, light);
                    return;
                }
            }
        }

        ResourceLocation tex = getCustomTexture(npc);
        if (tex != null) {
            Minecraft.getInstance().getTextureManager().bindForSetup(tex);
        }

        super.render(npc, yaw, partialTicks, stack, buffer, light);
    }

    private ResourceLocation getCustomTexture(NPCEntity entity) {
        if (entity.getCustomName() == null) return null;

        String displayName = entity.getCustomName().getString().trim();
        String safeName = transliterate(displayName)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]", "_");

        Path localTexture = getScriptsDir().resolve("textures").resolve(safeName + ".png");
        if (Files.exists(localTexture)) {
            try {
                NativeImage img = NativeImage.read(Files.newInputStream(localTexture));
                DynamicTexture dynTexture = new DynamicTexture(img);
                ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(StoryMine.MOD_ID, "scripts/textures/" + safeName);
                Minecraft.getInstance().getTextureManager().register(loc, dynTexture);
                return loc;
            } catch (IOException e) {
                System.err.println("[StoryMine] Ошибка загрузки текстуры NPC: " + e);
            }
        }

        ResourceLocation rl;
        if (safeName.contains(":")) {
            String[] parts = safeName.split(":", 2);
            rl = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        } else {
            rl = ResourceLocation.fromNamespaceAndPath("minecraft", safeName);
        }

        return rl;
    }

    private String transliterate(String text) {
        char[] abcCyr = {'А','Б','В','Г','Д','Е','Ё','Ж','З','И','Й','К','Л','М','Н','О','П','Р','С','Т','У','Ф','Х','Ц','Ч','Ш','Щ','Ъ','Ы','Ь','Э','Ю','Я',
                'а','б','в','г','д','е','ё','ж','з','и','й','к','л','м','н','о','п','р','с','т','у','ф','х','ц','ч','ш','щ','ъ','ы','ь','э','ю','я'};
        String[] abcLat = {"A","B","V","G","D","E","E","ZH","Z","I","I","K","L","M","N","O","P","R","S","T","U","F","KH","TS","CH","SH","SCH","","Y","","E","YU","YA",
                "a","b","v","g","d","e","e","zh","z","i","i","k","l","m","n","o","p","r","s","t","u","f","kh","ts","ch","sh","sch","","y","","e","yu","ya"};
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            boolean replaced = false;
            for (int i = 0; i < abcCyr.length; i++) {
                if (c == abcCyr[i]) {
                    sb.append(abcLat[i]);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) sb.append(c);
        }
        return sb.toString();
    }

    private Path getScriptsDir() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("scripts");
        } else {
            return mc.gameDirectory.toPath().resolve("scripts");
        }
    }
}