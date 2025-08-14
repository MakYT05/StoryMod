package mak.StoryMine.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScriptManager {
    private static final String SCRIPT_FILE_NAME = "script.json";

    public static void init(MinecraftServer server) {
        try {
            Path root = server.getWorldPath(LevelResource.ROOT);
            Path scriptsDir = root.resolve("scripts");

            if (!Files.exists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                System.out.println("[StoryMine] Создана папка scripts");
            }

            Path scriptFile = scriptsDir.resolve(SCRIPT_FILE_NAME);
            if (!Files.exists(scriptFile)) {
                Files.createFile(scriptFile);
                Files.writeString(scriptFile, "{\"dialogue\":{\"speaker\":\"Система\",\"text\":\"Файл script.json создан!\"}}");
                System.out.println("[StoryMine] Создан script.json");
            }

            String content = Files.readString(scriptFile);
            JsonElement parsed = JsonParser.parseString(content);
            ScriptExecutor.execute(parsed.getAsJsonObject(), server);

        } catch (IOException e) {
            System.err.println("[StoryMine] Ошибка ScriptManager:");
            e.printStackTrace();
        }
    }
}