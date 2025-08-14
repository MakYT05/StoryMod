package mak.StoryMine.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mak.StoryMine.entity.NPCEntity;
import mak.StoryMine.entity.NPCEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ScriptExecutor {
    private record TimerTask(int ticksRemaining, JsonObject action) {}
    private static final List<TimerTask> pendingTimers = new ArrayList<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        List<TimerTask> updatedTimers = new ArrayList<>();

        Iterator<TimerTask> it = pendingTimers.iterator();
        while (it.hasNext()) {
            TimerTask task = it.next();
            int newTicks = task.ticksRemaining() - 1;
            it.remove();
            if (newTicks <= 0) {
                execute(task.action(), server);
            } else {
                updatedTimers.add(new TimerTask(newTicks, task.action()));
            }
        }

        pendingTimers.addAll(updatedTimers);
    }


    public static void execute(JsonObject script, MinecraftServer server) {
        for (String key : script.keySet()) {
            JsonElement element = script.get(key);

            if (element.isJsonObject()) {
                JsonObject data = element.getAsJsonObject();

                try {
                    switch (key) {
                        case "check_coords" -> handleCheckCoords(data, server);
                        case "spawn_npc" -> handleSpawnNPC(data, null, server);
                        case "npc_action" -> handleNPCAction(data, server);
                        case "dialogue" -> handleDialogue(data, server);
                        case "check_inventory" -> handleCheckInventory(data, server);
                        case "timer" -> handleTimer(data, server);
                        default -> System.out.println("[StoryMine] Неизвестный ключ в скрипте: " + key);
                    }
                } catch (Exception e) {
                    System.err.println("[StoryMine] Ошибка при обработке действия: " + key);
                    e.printStackTrace();
                }
            }
        }
    }

    private static void handleSpawnNPC(JsonObject data, ServerPlayer player, MinecraftServer server) {
        server.execute(() -> {
            Level level = player != null ? player.level() : server.overworld();
            if (level == null) return;

            double x = player != null ? parseRelativeCoord(data.get("x").getAsString(), player.getX()) : data.get("x").getAsDouble();
            double y = player != null ? parseRelativeCoord(data.get("y").getAsString(), player.getY()) : data.get("y").getAsDouble();
            double z = player != null ? parseRelativeCoord(data.get("z").getAsString(), player.getZ()) : data.get("z").getAsDouble();

            NPCEntity npc = new NPCEntity(NPCEntityType.NPC.get(), level);
            npc.setPos(x, y, z);

            if (data.has("name")) {
                npc.setCustomName(Component.literal(data.get("name").getAsString()));
                npc.setCustomNameVisible(true);
            }

            level.addFreshEntity(npc);

            if (data.has("dialogue")) {
                String text = data.getAsJsonObject("dialogue").get("text").getAsString();
                String speaker = data.getAsJsonObject("dialogue").has("speaker")
                        ? data.getAsJsonObject("dialogue").get("speaker").getAsString()
                        : (npc.getCustomName() != null ? npc.getCustomName().getString() : "NPC");

                level.players().forEach(p -> p.sendSystemMessage(Component.literal(speaker + ": " + text)));
            }

            System.out.println("[StoryMine] NPC заспавнен: " + (npc.getCustomName() != null ? npc.getCustomName().getString() : "Безымянный"));
        });
    }

    private static void handleNPCAction(JsonObject data, MinecraftServer server) {
        String npcName = data.has("npc_name") ? data.get("npc_name").getAsString() : null;
        String actionType = data.get("action").getAsString();

        server.execute(() -> {
            Level level = server.overworld();
            if (level == null) return;

            int minBuildHeight = level.getMinBuildHeight();
            int maxBuildHeight = level.getMaxBuildHeight();

            AABB searchArea = new AABB(
                    level.getWorldBorder().getMinX(), minBuildHeight, level.getWorldBorder().getMinZ(),
                    level.getWorldBorder().getMaxX(), maxBuildHeight, level.getWorldBorder().getMaxZ()
            );

            for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, searchArea)) {
                if (npcName == null || (npc.getCustomName() != null &&
                        npcName.equals(npc.getCustomName().getString()))) {

                    switch (actionType) {
                        case "walk" -> {
                            double x = data.get("x").getAsDouble();
                            double y = data.get("y").getAsDouble();
                            double z = data.get("z").getAsDouble();
                            double speed = data.has("speed") ? data.get("speed").getAsDouble() : 1.0;
                            npc.getNavigation().moveTo(x, y, z, speed);
                        }

                        case "teleport" -> {
                            double x = data.get("x").getAsDouble();
                            double y = data.get("y").getAsDouble();
                            double z = data.get("z").getAsDouble();
                            npc.teleportTo(x, y, z);
                        }

                        case "say" -> {
                            String text = data.get("text").getAsString();
                            Component message = Component.literal(
                                    (npc.getCustomName() != null ?
                                            npc.getCustomName().getString() + ": " : "NPC: ") + text
                            );
                            level.players().stream()
                                    .filter(p -> p.distanceToSqr(npc) < 100)
                                    .forEach(p -> p.sendSystemMessage(message));
                        }

                        case "animate" -> {
                            String animation = data.get("animation").getAsString();
                            npc.setAnimationState(animation);
                        }
                    }
                }
            }
        });
    }

    private static void handleDialogue(JsonObject data, MinecraftServer server) {
        String speaker = data.get("speaker").getAsString();
        String text = data.get("text").getAsString();

        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Component.literal(speaker + ": " + text));
            }
        });
    }

    private static void handleCheckInventory(JsonObject data, MinecraftServer server) {
        if (!data.has("item") || !data.has("count")) {
            return;
        }

        final String itemId = data.get("item").getAsString().replace("minecraft:", "");
        final int requiredCount = data.get("count").getAsInt();

        server.execute(() -> {
            ResourceLocation itemLoc = ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
            Item item = BuiltInRegistries.ITEM.get(itemLoc);

            if (item == null) {
                System.err.println("[StoryMine] Неизвестный предмет: " + itemId);
                return;
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int foundCount = countItems(player, item);

                if (foundCount >= requiredCount && data.has("action")) {
                    executeAction(data.getAsJsonObject("action"), player, server);
                }
            }
        });
    }

    private static void executeAction(JsonObject action, ServerPlayer player, MinecraftServer server) {
        if (action.has("spawn_npc")) {
            handleSpawnNPC(action.getAsJsonObject("spawn_npc"), player, server);
        }
    }

    private static int countItems(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void handleTimer(JsonObject data, MinecraftServer server) {
        int ticks = data.get("ticks").getAsInt();
        JsonObject action = data.get("action").getAsJsonObject();
        pendingTimers.add(new TimerTask(ticks, action));
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

            BlockPos targetPos = BlockPos.containing(x, y, z);

            if (target.equalsIgnoreCase("player")) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.blockPosition().closerToCenterThan(targetPos.getCenter(), radius)) {
                        if (action != null) {
                            execute(action, server);
                        }
                    }
                }
            } else if (target.equalsIgnoreCase("npc")) {
                level.getEntitiesOfClass(NPCEntity.class,
                        new AABB(targetPos).inflate(radius),
                        npc -> true).forEach(npc -> {
                    if (action != null) {
                        executeActionOnNPC(action, npc, server);
                    }
                });
            }
        });
    }

    private static void executeActionOnNPC(JsonObject action, NPCEntity npc, MinecraftServer server) {
        if (action.has("dialogue")) {
            JsonObject dialogue = action.getAsJsonObject("dialogue");
            String text = dialogue.get("text").getAsString();
            npc.level().players().forEach(player ->
                    player.sendSystemMessage(Component.literal(npc.getName().getString() + ": " + text))
            );
        }
    }

    private static double parseRelativeCoord(String coordStr, double playerCoord) {
        if (coordStr.startsWith("~")) {
            String offsetStr = coordStr.substring(1);
            double offset = offsetStr.isEmpty() ? 0 : Double.parseDouble(offsetStr);
            return playerCoord + offset;
        }
        return Double.parseDouble(coordStr);
    }
}