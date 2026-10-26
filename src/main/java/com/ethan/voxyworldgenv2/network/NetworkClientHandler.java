package com.ethan.voxyworldgenv2.network;

import com.ethan.voxyworldgenv2.VoxyWorldGenV2;
import com.ethan.voxyworldgenv2.integration.VoxyIntegration;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;

public class NetworkClientHandler {
    
    public static void init() {
        // fabric packet handlers run on the client thread
        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.HandshakePayload.TYPE, (payload, player, responseSender) ->
                NetworkState.setServerConnected(payload.serverHasMod()));

        ClientPlayNetworking.registerGlobalReceiver(NetworkHandler.LODDataPayload.TYPE, (payload, player, responseSender) ->
                handleLODData(payload));
    }

    @SuppressWarnings("unchecked")
    private static void handleLODData(NetworkHandler.LODDataPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // discard LOD data from a different dimension to prevent cross-dimension rendering artifacts (issue #43)
        if (!level.dimension().equals(payload.dimension())) return;
        
        // calculate approximate payload size
        long bytes = 0;
        for (NetworkHandler.LODDataPayload.SectionData sd : payload.sections()) {
            bytes += sd.states().length;
            bytes += sd.biomes().length;
            if (sd.blockLight() != null) bytes += sd.blockLight().length;
            if (sd.skyLight() != null) bytes += sd.skyLight().length;
        }
        NetworkState.incrementReceived(bytes);

        for (NetworkHandler.LODDataPayload.SectionData sectionData : payload.sections()) {
            io.netty.buffer.ByteBuf statesRaw = io.netty.buffer.Unpooled.wrappedBuffer(sectionData.states());
            io.netty.buffer.ByteBuf biomesRaw = io.netty.buffer.Unpooled.wrappedBuffer(sectionData.biomes());
            try {
                // recreate section using the biome registry
                LevelChunkSection section = new LevelChunkSection(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
                
                ((PalettedContainer<BlockState>) section.getStates()).read(new net.minecraft.network.FriendlyByteBuf(statesRaw));
                ((PalettedContainer<Holder<Biome>>) section.getBiomes()).read(new net.minecraft.network.FriendlyByteBuf(biomesRaw));
                
                // ingest into voxy
                DataLayer bl = sectionData.blockLight() != null ? new DataLayer(sectionData.blockLight()) : null;
                DataLayer sl = sectionData.skyLight() != null ? new DataLayer(sectionData.skyLight()) : null;
                
                VoxyIntegration.rawIngest(level, section, payload.pos().x, sectionData.y(), payload.pos().z, bl, sl);
                
            } catch (Exception e) {
                VoxyWorldGenV2.LOGGER.error("failed to handle LOD data for chunk " + payload.pos(), e);
            } finally {
                statesRaw.release();
                biomesRaw.release();
            }
        }
    }
}
