package dev.efnilite.ip.util;

import com.cryptomorin.xseries.XSound;
import org.bukkit.Sound;

public final class SoundAdapter {
    private SoundAdapter() {}

    public static Sound adapt(String name) {
        return XSound.matchXSound(name)
                .map(XSound::parseSound)
                .orElse(XSound.BLOCK_NOTE_BLOCK_PLING.parseSound());
    }
}
