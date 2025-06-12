package dev.efnilite.ip.util;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Particle;

public final class ParticleAdapter {
    private ParticleAdapter() {}

    public static Particle adapt(String name) {
        Particle particle = XParticle.getParticle(name);
        return particle != null ? particle : Particle.FLAME;
    }
}
