package com.musicbox.client;

import com.musicbox.music.MusicPlayer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

final class VolumeSlider extends AbstractSliderButton {
    VolumeSlider(int x, int y, int width, int height, double value) {
        super(x, y, width, height, Component.empty(), value);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("gui.musicbox.volume", Math.round(value * 100.0D)));
    }

    @Override
    protected void applyValue() {
        MusicPlayer.get().setVolume((float) value);
    }
}
