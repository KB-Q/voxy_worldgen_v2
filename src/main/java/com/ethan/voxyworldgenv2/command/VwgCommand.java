package com.ethan.voxyworldgenv2.command;

import com.ethan.voxyworldgenv2.core.ChunkGenerationManager;
import com.ethan.voxyworldgenv2.core.Config;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// /vwg on|off|toggle turns generation on or off (the "Mod Enabled" option, saved to the config), /vwg status prints the state
public final class VwgCommand {
    private VwgCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vwg")
                // operators, and the owner of a singleplayer or lan world even without cheats
                .requires(src -> src.hasPermission(2) || (src.getPlayer() != null && src.getServer().isSingleplayerOwner(src.getPlayer().getGameProfile())))
                .then(Commands.literal("on").executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("toggle").executes(ctx -> setEnabled(ctx, !Config.DATA.enabled)))
                .then(Commands.literal("status").executes(VwgCommand::status))
                .executes(VwgCommand::status));
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        Config.DATA.enabled = enabled;
        Config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("Voxy World Gen V2 generation " + (enabled ? "§aon" : "§coff")), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var manager = ChunkGenerationManager.getInstance();
        var throttle = manager.getThrottle();
        var stats = manager.getStats();
        int max = Config.DATA.maxActiveTasks;
        String state;
        if (!Config.DATA.enabled) {
            state = "§coff";
        } else if (throttle.isPaused()) {
            state = "§cpaused by the throttle (" + throttle.reason() + ")";
        } else if (throttle.reason() != null) {
            state = "§eslowed to " + Math.min(throttle.allowedTasks(), max) + "/" + max + " tasks (" + throttle.reason() + ")";
        } else {
            state = "§aon";
        }
        String load = String.format("heap %.0f%% (slow %d%%, pause %d%%), tick %.1f ms (target %d), %.1f tps",
                throttle.heapPercent(), Config.DATA.throttleSlowHeapPercent, Config.DATA.throttlePauseHeapPercent,
                throttle.workMs(), Config.DATA.throttleTargetMspt, throttle.tps());
        if (throttle.fps() >= 0) load += ", " + throttle.fps() + " fps (min " + Config.DATA.throttleMinFps + ")";
        String progress = String.format("active %d, completed %d, remaining %d, %.1f chunks/s",
                manager.getActiveTaskCount(), stats.getCompleted(), manager.getRemainingInRadius(), stats.getChunksPerSecond());
        String finalState = state, finalLoad = load;
        ctx.getSource().sendSuccess(() -> Component.literal("Voxy World Gen V2: " + finalState), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + finalLoad + (Config.DATA.throttleEnabled ? "" : ", throttle off")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7" + progress), false);
        return 1;
    }
}
