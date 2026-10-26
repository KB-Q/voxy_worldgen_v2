package com.ethan.voxyworldgenv2;

import com.ethan.voxyworldgenv2.client.DebugRenderer;
import com.ethan.voxyworldgenv2.core.ChunkGenerationManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class VoxyWorldGenV2Client implements ClientModInitializer {
    // unbound by default, runs /vwg toggle so it works wherever the command is allowed
    private static final KeyMapping TOGGLE_KEY = new KeyMapping("key.voxyworldgenv2.toggle", InputConstants.UNKNOWN.getValue(), "key.categories.voxyworldgenv2");
    
    @Override
    public void onInitializeClient() {
        VoxyWorldGenV2.LOGGER.info("initializing voxy world gen v2 client");
        
        // debug hud renderer
        HudRenderCallback.EVENT.register(DebugRenderer::render);

        // register pause check to stop background worker when game is paused
        ChunkGenerationManager.getInstance().setPauseCheck(() -> {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.isPaused();
        });

        // frame rate for the throttle, only for the host of a singleplayer or lan world with the window focused,
        // unfocused and minimized windows run at a reduced frame rate on purpose
        ChunkGenerationManager.getInstance().setClientFps(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSingleplayerServer() == null || mc.level == null || !mc.isWindowActive()) return -1;
            return mc.getFps();
        });

        KeyBindingHelper.registerKeyBinding(TOGGLE_KEY);

        // register network receivers
        com.ethan.voxyworldgenv2.network.NetworkClientHandler.init();
        
        // reset connection state on disconnect
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.ethan.voxyworldgenv2.network.NetworkState.setServerConnected(false);
        });
        
        // tick network stats
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.ethan.voxyworldgenv2.network.NetworkState.tick();
            while (TOGGLE_KEY.consumeClick()) {
                if (client.player != null) client.player.connection.sendCommand("vwg toggle");
            }
        });
    }
}
