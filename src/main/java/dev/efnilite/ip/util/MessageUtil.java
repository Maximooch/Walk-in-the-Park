package dev.efnilite.ip.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility methods for sending titles and action bars across versions.
 */
public final class MessageUtil {
    private MessageUtil() {}

    public static void sendTitle(Player player, String title, String sub, int fadeIn, int stay, int fadeOut) {
        try {
            player.sendTitle(title, sub, fadeIn, stay, fadeOut);
        } catch (NoSuchMethodError ignored) {
            // Title API not available
        }
    }

    public static void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (NoSuchMethodError ex) {
            sendPacket(player, message);
        }
    }

    private static void sendPacket(Player player, String message) {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split(".")[3];
            Class<?> chatBaseComponent = Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
            Class<?> serializer = Class.forName(chatBaseComponent.getName() + "$ChatSerializer");
            Object comp = serializer.getMethod("a", String.class).invoke(null, "{\"text\":\"" + message.replace("\"", "\\\"") + "\"}");
            Class<?> packetClass = Class.forName("net.minecraft.server." + version + ".PacketPlayOutChat");
            Object packet = packetClass.getConstructor(chatBaseComponent, byte.class).newInstance(comp, (byte) 2);
            Object craft = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = craft.getClass().getField("playerConnection").get(craft);
            connection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + version + ".Packet")).invoke(connection, packet);
        } catch (Throwable ignored) {
            // ignore
        }
    }
}
