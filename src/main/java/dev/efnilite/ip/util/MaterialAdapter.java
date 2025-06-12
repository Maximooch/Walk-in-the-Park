package dev.efnilite.ip.util;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;

import java.util.Optional;

public final class MaterialAdapter {
    private MaterialAdapter() {}

    public static Material adapt(String name) {
        Optional<XMaterial> xMat = XMaterial.matchXMaterial(name);
        return xMat.map(XMaterial::parseMaterial).orElse(Material.STONE);
    }
}
