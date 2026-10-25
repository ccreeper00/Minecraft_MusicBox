package com.musicbox;

import com.musicbox.client.MusicBoxClient;
import com.musicbox.config.MusicBoxConfig;
import com.musicbox.music.MusicPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = MusicBox.MOD_ID, dist = Dist.CLIENT)
public final class MusicBox {
    public static final String MOD_ID = "musicbox";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MusicBox(IEventBus modBus, ModContainer modContainer) {
        MusicBoxConfig.load();
        MusicPlayer.get().setVolume((float) MusicBoxConfig.get().volume);

        modBus.addListener(MusicBoxClient::registerKeys);
        modBus.addListener(MusicBoxClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(MusicBoxClient::onClientTick);
    }
}
