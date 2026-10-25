package com.musicbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.musicbox.MusicBox;
import com.musicbox.music.MusicPlayer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class MusicBoxClient {
    private static KeyMapping openKey;
    private static KeyMapping toggleKey;
    private static KeyMapping previousKey;
    private static KeyMapping nextKey;
    private static KeyMapping volumeDownKey;
    private static KeyMapping volumeUpKey;

    private MusicBoxClient() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = new KeyMapping.Category(
                Identifier.fromNamespaceAndPath(MusicBox.MOD_ID, "main")
        );
        event.registerCategory(category);

        openKey = register(event, "key.musicbox.open", GLFW.GLFW_KEY_M, category);
        toggleKey = register(event, "key.musicbox.toggle", GLFW.GLFW_KEY_Y, category);
        previousKey = register(event, "key.musicbox.previous", GLFW.GLFW_KEY_LEFT_BRACKET, category);
        nextKey = register(event, "key.musicbox.next", GLFW.GLFW_KEY_RIGHT_BRACKET, category);
        volumeDownKey = register(event, "key.musicbox.volume_down", GLFW.GLFW_KEY_PAGE_DOWN, category);
        volumeUpKey = register(event, "key.musicbox.volume_up", GLFW.GLFW_KEY_PAGE_UP, category);
    }

    private static KeyMapping register(RegisterKeyMappingsEvent event, String name, int key,
                                       KeyMapping.Category category) {
        KeyMapping mapping = new KeyMapping(
                name,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                key,
                category
        );
        event.register(mapping);
        return mapping;
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(MusicBox.MOD_ID, "now_playing"),
                (graphics, deltaTracker) -> NowPlayingHud.renderDefault(graphics)
        );
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (openKey == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        MusicPlayer player = MusicPlayer.get();

        // Java Sound is independent of Minecraft's sound engine; stop it when
        // leaving the world instead of letting it continue on the title screen.
        if (minecraft.player == null) {
            if (player.hasCurrent()) {
                player.stopForWorldExit();
            }
            return;
        }

        while (openKey.consumeClick()) {
            if (minecraft.screen == null) {
                player.ensureLoaded();
                minecraft.setScreen(new MusicScreen());
            }
        }

        // Do not fire transport/volume keys while a screen has focus.
        if (minecraft.screen == null) {
            while (toggleKey.consumeClick()) {
                player.toggle();
            }
            while (previousKey.consumeClick()) {
                player.previous();
            }
            while (nextKey.consumeClick()) {
                player.next();
            }
            while (volumeDownKey.consumeClick()) {
                player.adjustVolume(-0.05F);
            }
            while (volumeUpKey.consumeClick()) {
                player.adjustVolume(0.05F);
            }
        }
    }
}
