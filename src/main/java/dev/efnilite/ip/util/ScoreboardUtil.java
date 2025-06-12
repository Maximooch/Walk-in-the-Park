package dev.efnilite.ip.util;

import org.bukkit.scoreboard.Team;

public final class ScoreboardUtil {
    private ScoreboardUtil() {}

    public static void setCollisionRule(Team team, Team.OptionStatus status) {
        try {
            team.setOption(Team.Option.COLLISION_RULE, status);
        } catch (NoSuchMethodError ignored) {
            // 1.8 does not support collision rule
        }
    }

    public static String truncate(String name) {
        return name.length() > 16 ? name.substring(0, 16) : name;
    }
}
