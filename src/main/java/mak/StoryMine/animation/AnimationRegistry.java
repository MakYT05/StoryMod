package mak.StoryMine.animation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.zeith.hammeranims.api.animation.AnimationHolder;
import org.zeith.hammeranims.api.animation.IAnimationContainer;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AnimationRegistry {
    private static final Map<String, JsonObject> ANIMATIONS = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void loadAnimations(Path animsFolder) {
        try {
            if (Files.exists(animsFolder)) {
                Files.walk(animsFolder)
                        .filter(path -> path.toString().endsWith(".animation.json"))
                        .forEach(AnimationRegistry::registerAnimation);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void registerAnimation(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            String fileName = path.getFileName().toString();
            String animId = fileName.replace(".animation.json", "");

            ANIMATIONS.put(animId, obj);
            System.out.println("[StoryMine] Loaded animation: " + animId);
        } catch (Exception e) {
            System.err.println("[StoryMine] Failed to load animation " + path);
            e.printStackTrace();
        }
    }

    public static JsonObject get(String id) {
        return ANIMATIONS.get(id);
    }
}