package mak.StoryMine.script;

import com.google.gson.*;
import mak.StoryMine.StoryMine;
import mak.StoryMine.entity.NPCEntity;
import mak.StoryMine.entity.NPCEntityType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = StoryMine.MOD_ID)
public class ScriptExecutor {
    private record TimerTask(int ticksRemaining, JsonObject action) {}
    private static final List<TimerTask> pendingTimers = new ArrayList<>();
    private static final List<JsonObject> activeInventoryChecks = new ArrayList<>();
    private static int invCheckTimer = 0;

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (!pendingTimers.isEmpty()) {
            List<TimerTask> updatedTimers = new ArrayList<>();
            for (TimerTask task : new ArrayList<>(pendingTimers)) {
                int newTicks = task.ticksRemaining() - 1;
                pendingTimers.remove(task);

                if (newTicks <= 0) {
                    JsonObject act = task.action();
                    if (act.has("_sequence")) {
                        JsonArray arr = act.getAsJsonArray("_sequence");
                        int next = act.get("_nextIndex").getAsInt();
                        executeSequenceStep(arr, next, server, null);
                    } else {
                        execute(act, server);
                    }
                } else {
                    updatedTimers.add(new TimerTask(newTicks, task.action()));
                }
            }
            if (!updatedTimers.isEmpty()) pendingTimers.addAll(updatedTimers);
        }

        if (!activeInventoryChecks.isEmpty() && ++invCheckTimer >= 20) {
            invCheckTimer = 0;
            List<JsonObject> toRemove = new ArrayList<>();
            for (JsonObject checkData : new ArrayList<>(activeInventoryChecks)) {
                if (handleCheckInventory(checkData, server)) {
                    toRemove.add(checkData);
                }
            }
            activeInventoryChecks.removeAll(toRemove);
        }

        Level level = server.overworld();
        if (level != null) {
            for (NPCEntity npc : level.getEntitiesOfClass(
                    NPCEntity.class,
                    new AABB(
                            level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                            level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
                    ))) {
                if (npc.hasWalkingTarget()) {
                    BlockPos target = npc.getWalkingTarget();
                    if (npc.blockPosition().distSqr(target) > 2) {
                        npc.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), npc.getWalkingSpeed());
                    } else {
                        npc.clearWalkingTarget();
                    }
                }
            }
        }
    }

    public static void execute(JsonObject script, MinecraftServer server) {
        for (String key : script.keySet()) {
            JsonElement element = script.get(key);
            if (!element.isJsonObject()) continue;
            JsonObject data = element.getAsJsonObject();
            try {
                switch (key) {
                    case "check_coords" -> handleCheckCoords(data, server);
                    case "spawn_npc" -> handleSpawnNPC(data, null, server);
                    case "npc_action" -> handleNPCAction(data, server);
                    case "dialogue" -> handleDialogue(data, server, null);
                    case "check_inventory" -> activeInventoryChecks.add(data);
                    case "sequence" -> handleSequence(data.getAsJsonArray(), server, null);
                    case "delay" -> handleDelay(data, server);
                    default -> System.out.println("[StoryMine] Неизвестный ключ в скрипте: " + key);
                }
            } catch (Exception e) {
                System.err.println("[StoryMine] Ошибка при обработке действия: " + key);
                e.printStackTrace();
            }
        }
    }

    private static void handleSpawnNPC(JsonObject data, ServerPlayer player, MinecraftServer server) {
        server.execute(() -> {
            Level level = player != null ? player.level() : server.overworld();
            if (level == null) return;

            double baseX = player != null ? player.getX() : 0;
            double baseY = player != null ? player.getY() : 0;
            double baseZ = player != null ? player.getZ() : 0;

            double x = parseCoordinate(data, "x", baseX);
            double y = parseCoordinate(data, "y", baseY);
            double z = parseCoordinate(data, "z", baseZ);

            NPCEntity npc = new NPCEntity(NPCEntityType.NPC.get(), level);
            npc.setPos(x, y, z);
            npc.setNoAi(true);

            if (data.has("name")) {
                String name = data.get("name").getAsString();
                ChatFormatting formatting = ChatFormatting.WHITE;
                if (data.has("color")) {
                    try { formatting = ChatFormatting.valueOf(data.get("color").getAsString().toUpperCase()); } catch (IllegalArgumentException ignored) {}
                }
                npc.setCustomName(Component.literal(name).withStyle(formatting));
                npc.setCustomNameVisible(true);
            }

            if (data.has("model")) {
                String modelStr = data.get("model").getAsString();
                if (!"default".equalsIgnoreCase(modelStr)) {
                    ResourceLocation rl = ResourceLocation.tryParse(modelStr);
                    if (rl != null) npc.setMimicType(rl);
                    else System.err.println("[StoryMine] Неверный формат model: " + modelStr);
                }
            }

            level.addFreshEntity(npc);
            System.out.println("[StoryMine] NPC заспавнен: "
                    + (npc.getCustomName() != null ? npc.getCustomName().getString() : "Безымянный")
                    + (npc.getMimicType() != null ? " (модель: " + npc.getMimicType() + ")" : ""));
        });
    }

    private static void handleNPCAction(JsonObject data, MinecraftServer server) {
        String npcName = data.has("npc_name") ? data.get("npc_name").getAsString() : null;
        String actionType = data.get("action").getAsString();

        server.execute(() -> {
            Level level = server.overworld();
            if (level == null) return;

            AABB searchArea = new AABB(level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ());

            for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, searchArea)) {
                if (npcName == null || (npc.getCustomName() != null && npcName.equals(npc.getCustomName().getString()))) {
                    switch (actionType) {
                        case "walk" -> {
                            double x = data.get("x").getAsDouble();
                            double y = data.get("y").getAsDouble();
                            double z = data.get("z").getAsDouble();
                            double speed = data.has("speed") ? data.get("speed").getAsDouble() : 1.0;
                            npc.setNoAi(false);
                            npc.setWalkingTarget(new BlockPos((int)x, (int)y, (int)z), speed);
                        }
                        case "teleport" -> npc.teleportTo(data.get("x").getAsDouble(), data.get("y").getAsDouble(), data.get("z").getAsDouble());
                        case "say" -> {
                            String text = data.get("text").getAsString();
                            Component message = Component.literal((npc.getCustomName() != null ? npc.getCustomName().getString() + ": " : "NPC: ") + text);
                            level.players().stream().filter(p -> p.distanceToSqr(npc) < 100).forEach(p -> p.sendSystemMessage(message));
                        }
                        case "animate" -> npc.playAnimation(data.get("anim").getAsString());
                    }
                }
            }
        });
    }

    private static void handleDialogue(JsonObject data, MinecraftServer server, ServerPlayer player) {
        String speaker = data.has("speaker") ? data.get("speaker").getAsString() : "NPC";
        String text = data.get("text").getAsString();
        server.execute(() -> {
            if (player != null) player.sendSystemMessage(Component.literal(speaker + ": " + text));
            else server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(Component.literal(speaker + ": " + text)));
        });
    }

    private static boolean handleCheckInventory(JsonObject data, MinecraftServer server) {
        if (!data.has("item") || !data.has("count")) return false;

        String rawItemId = data.get("item").getAsString();
        ResourceLocation itemLoc;
        try { itemLoc = rawItemId.contains(":") ? ResourceLocation.parse(rawItemId)
                : ResourceLocation.fromNamespaceAndPath("minecraft", rawItemId); }
        catch (Exception e) { return false; }

        int requiredCount = data.get("count").getAsInt();
        Item item = BuiltInRegistries.ITEM.get(itemLoc);
        if (item == Items.AIR) return false;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int foundCount = countItems(player, item);
            if (foundCount >= requiredCount && data.has("action")) {
                executeAction(data.getAsJsonObject("action"), player, server);
                return true;
            }
        }
        return false;
    }

    private static void executeAction(JsonObject action, ServerPlayer player, MinecraftServer server) {
        if (action.has("sequence")) handleSequence(action.getAsJsonArray("sequence"), server, player);
        if (action.has("dialogue")) handleDialogue(action.getAsJsonObject("dialogue"), server, player);
    }

    private static int countItems(Player player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void handleCheckCoords(JsonObject data, MinecraftServer server) {
        double x = data.get("x").getAsDouble();
        double y = data.get("y").getAsDouble();
        double z = data.get("z").getAsDouble();
        String target = data.get("target").getAsString();
        JsonObject action = data.has("action") ? data.getAsJsonObject("action") : null;
        double radius = data.has("radius") ? data.get("radius").getAsDouble() : 2.0;

        server.execute(() -> {
            Level level = server.overworld();
            if (level == null) return;

            BlockPos targetPos = new BlockPos((int) x, (int) y, (int) z);

            if (target.equalsIgnoreCase("player")) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.blockPosition().closerToCenterThan(targetPos.getCenter(), radius)) {
                        if (action != null) execute(action, server);
                    }
                }
            } else if (target.equalsIgnoreCase("npc")) {
                level.getEntitiesOfClass(NPCEntity.class, new AABB(targetPos).inflate(radius), npc -> true)
                        .forEach(npc -> {
                            if (action != null) executeActionOnNPC(action, npc, server);
                        });
            }
        });
    }

    private static void handleSequence(JsonArray actions, MinecraftServer server, ServerPlayer player) {
        executeSequenceStep(actions, 0, server, player);
    }

    private static void executeSequenceStep(JsonArray actions, int index, MinecraftServer server, ServerPlayer player) {
        if (index >= actions.size()) return;

        JsonElement elem = actions.get(index);

        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            String key = obj.keySet().iterator().next();
            JsonElement data = obj.get(key);

            if ("sequence".equals(key) && data.isJsonArray()) {
                JsonArray sub = data.getAsJsonArray();
                executeSequenceStep(sub, 0, server, player);
                executeSequenceStep(actions, index + 1, server, player);
                return;
            }

            if ("delay".equals(key) && data.isJsonObject()) {
                JsonObject delayData = data.getAsJsonObject();
                int ticks = delayData.get("ticks").getAsInt();
                JsonObject innerAction = delayData.has("action") ? delayData.getAsJsonObject("action") : null;

                JsonObject wrapper = new JsonObject();
                if (innerAction != null) {
                    for (String k : innerAction.keySet()) {
                        wrapper.add(k, innerAction.get(k));
                    }
                }
                wrapper.add("_nextIndex", new JsonPrimitive(index + 1));
                wrapper.add("_sequence", actions);

                pendingTimers.add(new TimerTask(ticks, wrapper));
                return;
            }

            if (data.isJsonObject()) {
                JsonObject dataObj = data.getAsJsonObject();
                server.execute(() -> {
                    switch (key) {
                        case "spawn_npc" -> handleSpawnNPC(dataObj, player, server);
                        case "npc_action" -> handleNPCAction(dataObj, server);
                        case "dialogue" -> handleDialogue(dataObj, server, player);
                    }
                });
            }

        } else if (elem.isJsonArray()) {
            executeSequenceStep(elem.getAsJsonArray(), 0, server, player);
        }

        executeSequenceStep(actions, index + 1, server, player);
    }

    private static void handleDelay(JsonObject data, MinecraftServer server) {
        int ticks = data.get("ticks").getAsInt();
        JsonObject action = data.getAsJsonObject("action");
        pendingTimers.add(new TimerTask(ticks, action));
    }

    private static void executeActionOnNPC(JsonObject action, NPCEntity npc, MinecraftServer server) {
        if (action.has("dialogue")) {
            JsonObject dialogue = action.getAsJsonObject("dialogue");
            String text = dialogue.get("text").getAsString();
            npc.level().players().forEach(player -> player.sendSystemMessage(Component.literal(npc.getName().getString() + ": " + text)));
        }
    }

    private static double parseCoordinate(JsonObject data, String key, double base) {
        if (!data.has(key)) return base;

        String raw = data.get(key).getAsString();
        if (raw.startsWith("~")) {
            if (raw.length() == 1) return base;
            return base + Double.parseDouble(raw.substring(1));
        } else {
            return Double.parseDouble(raw);
        }
    }
}