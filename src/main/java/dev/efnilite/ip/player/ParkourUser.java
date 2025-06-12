package dev.efnilite.ip.player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.efnilite.ip.IP;
import dev.efnilite.ip.api.event.ParkourJoinEvent;
import dev.efnilite.ip.api.event.ParkourLeaveEvent;
import dev.efnilite.ip.config.Config;
import dev.efnilite.ip.config.Locales;
import dev.efnilite.ip.config.Option;
import dev.efnilite.ip.generator.ParkourGenerator;
import dev.efnilite.ip.hook.FloodgateHook;
import dev.efnilite.ip.leaderboard.Leaderboard;
import dev.efnilite.ip.leaderboard.Score;
import dev.efnilite.ip.menu.ParkourOption;
import dev.efnilite.ip.mode.Mode;
import dev.efnilite.ip.mode.Modes;
import dev.efnilite.ip.player.data.PreviousData;
import dev.efnilite.ip.session.Session;
import dev.efnilite.ip.storage.Storage;
import dev.efnilite.ip.world.Divider;
import dev.efnilite.vilib.fastboard.FastBoard;
import dev.efnilite.vilib.util.Strings;
import dev.efnilite.ip.util.ScoreboardUtil;
import org.bukkit.scoreboard.Team;
import io.papermc.lib.PaperLib;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.ChannelNotRegisteredException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Superclass of every type of player. This encompasses every player currently in the Parkour world.
 * This includes active players ({@link ParkourPlayer}) and spectators ({@link ParkourSpectator}).
 *
 * @author Efnilite
 */
public abstract class ParkourUser {

    /**
     * Registers a player. This registers the player internally.
     * This automatically unregisters the player if it is already registered.
     *
     * @param player The player
     * @return the ParkourPlayer instance of the newly joined player
     */
    public static @NotNull ParkourPlayer register(@NotNull Player player, @NotNull Session session) {
        PreviousData data = null;
        ParkourUser existing = getUser(player);

        if (existing != null) {
            IP.log("Registering player %s with existing data".formatted(player.getName()));

            data = existing.previousData;
            unregister(existing, false, false, false);
        } else {
            IP.log("Registering player %s".formatted(player.getName()));
        }
        ParkourPlayer pp = new ParkourPlayer(player, session, data);

        // stats
        joinCount++;
        new ParkourJoinEvent(pp).call();

        Storage.readPlayer(pp);
        return pp;
    }

    /**
     * This is the same as {@link #leave(ParkourUser)}, but instead for a Bukkit player instance.
     *
     * @param player The Bukkit player instance that will be removed from the game if the player is active.
     * @see #leave(ParkourUser)
     */
    public static void leave(@NotNull Player player) {
        ParkourUser user = getUser(player);
        if (user == null) {
            return;
        }
        leave(user);
    }

    /**
     * Forces user to leave. Follows behaviour of /parkour leave.
     *
     * @param user The user.
     */
    public static void leave(@NotNull ParkourUser user) {
        unregister(user, true, true, false);
    }

    /**
     * Unregisters a Parkour user instance.
     *
     * @param user                The user to unregister.
     * @param restorePreviousData Whether to restore the data from before the player joined the parkour.
     * @param kickIfBungee        Whether to kick the player if Bungeecord mode is enabled.
     */
    public static void unregister(@NotNull ParkourUser user, boolean restorePreviousData, boolean kickIfBungee, boolean urgent) {
        new ParkourLeaveEvent(user).call();
        IP.log("Unregistering player %s, restorePreviousData = %s, kickIfBungee = %s".formatted(user.getName(), restorePreviousData, kickIfBungee));

        try {
            user.unregister();

            if (user.board != null && !user.board.isDeleted()) {
                user.board.delete();
            }
        } catch (Exception ex) { // safeguard to prevent people from losing data
            IP.logging().stack("Error while trying to make player %s leave".formatted(user.getName()), ex);
            user.send("<red><bold>There was an error while trying to handle leaving.");
        }

        if (restorePreviousData && Config.CONFIG.getBoolean("bungeecord.enabled") && kickIfBungee) {
            sendPlayerToServer(user.player, Config.CONFIG.getString("bungeecord.return_server"));
            return;
        }

        if (!restorePreviousData) return;

        user.previousData.apply(user.player, urgent);

        Mode mode = user.session.generator.getMode();
        if (mode == null) {
            IP.logging().error("Mode is null for %s".formatted(user.getName()));
            mode = Modes.DEFAULT;
        }

        if (user instanceof ParkourPlayer player) {
            Mode finalMode = mode;
            user.previousData.onLeave.forEach(r -> r.execute(player, finalMode));
        }
    }

    // Sends a player to a BungeeCord server. server is the server name.
    private static void sendPlayerToServer(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);

        try {
            player.sendPluginMessage(IP.getPlugin(), "BungeeCord", out.toByteArray());
        } catch (ChannelNotRegisteredException ex) {
            IP.logging().stack("Error while trying to send %s to server %s. This server is not registered.".formatted(player.getName(), server), ex);
            player.kickPlayer("Couldn't move you to %s. Please rejoin.".formatted(server));
        }
    }

    /**
     * @param player The player.
     * @return True when this player is a {@link ParkourUser}, false if not.
     */
    public static boolean isUser(@Nullable Player player) {
        return player != null && getUsers().stream().anyMatch(other -> other.player == player);
    }

    /**
     * @param player The player.
     * @return player as a {@link ParkourUser}, null if not found.
     */
    public static @Nullable ParkourUser getUser(@NotNull Player player) {
        return getUsers().stream()
                .filter(other -> other.getUUID() == player.getUniqueId())
                .findAny()
                .orElse(null);
    }

    /**
     * @return Set with all users.
     */
    public static Set<ParkourUser> getUsers() {
        return Divider.sections.keySet().stream()
                .flatMap(session -> session.getUsers().stream())
                .collect(Collectors.toSet());
    }

    /**
     * This user's locale
     */
    @NotNull
    public String locale = Option.OPTIONS_DEFAULTS.get(ParkourOption.LANG);

    /**
     * This user's scoreboard
     */
    public FastBoard board;

    /**
     * This user's PreviousData
     */
    @NotNull
    public PreviousData previousData;

    /**
     * The selected {@link Session.ChatType}
     */
    public Session.ChatType chatType = Session.ChatType.PUBLIC;

    /**
     * The {@link Session} this user is in.
     */
    public final Session session;

    /**
     * The Bukkit player instance associated with this user.
     */
    public final Player player;

    /**
     * The {@link Instant} when the player joined.
     */
    public final Instant joined;

    /**
     * The amount of players that have joined while the plugin has been enabled.
     */
    public static int joinCount;

    public ParkourUser(@NotNull Player player, @NotNull Session session, @Nullable PreviousData previousData) {
        this.player = player;
        this.session = session;
        this.joined = Instant.now();
        this.previousData = previousData == null ? new PreviousData(player) : previousData;

        if (Boolean.parseBoolean(Option.OPTIONS_DEFAULTS.get(ParkourOption.SCOREBOARD))) {
            this.board = new FastBoard(player);

            var scoreboard = player.getScoreboard();
            Team team = scoreboard.getTeam("parkour");
            if (team == null) {
                team = scoreboard.registerNewTeam("parkour");
            }
            ScoreboardUtil.setCollisionRule(team, Team.OptionStatus.NEVER);
            team.addEntry(player.getName());
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Unregisters this user.
     */
    protected abstract void unregister();

    /**
     * Teleports the player asynchronously.
     *
     * @param to Where the player will be teleported to
     */
    public void teleport(@NotNull Location to) {
        PaperLib.teleportAsync(player, to);
    }

    /**
     * Sends a message.
     *
     * @param message The message
     */
    public void send(String message) {
        player.sendMessage(Strings.colour(message));
    }

    /**
     * Sends a translated message
     *
     * @param key    The translation key
     * @param format Any objects that may be given to the formatting of the string.
     */
    public void sendTranslated(String key, Object... format) {
        send(Locales.getString(locale, key).formatted(format));
    }

    /**
     * Updates the scoreboard for the specified generator.
     *
     * @param generator The generator.
     */
    public void updateScoreboard(ParkourGenerator generator) {
        // board can be null a few ticks after on player leave
        if (board == null || board.isDeleted() || !generator.profile.get("showScoreboard").asBoolean()) {
            return;
        }

        Leaderboard leaderboard = generator.getMode().getLeaderboard();
        Score top = leaderboard == null ? new Score("?", "?", "?", 0) : leaderboard.getScoreAtRank(1);
        Score high = leaderboard == null ? new Score("?", "?", "?", 0) : leaderboard.get(getUUID());
        if (top == null) {
            top = new Score("?", "?", "?", 0);
        }

        board.updateTitle(replace(Locales.getString(locale, "scoreboard.title"), top, high, generator));
        board.updateLines(replace(Locales.getStringList(locale, "scoreboard.lines"), top, high, generator));
    }

    private List<String> replace(List<String> s, Score top, Score high, ParkourGenerator generator) {
        return s.stream().map(line -> replace(line, top, high, generator)).toList();
    }

    private String replace(String s, Score top, Score high, ParkourGenerator generator) {
        return Strings.colour(translate(player, s)
                .replace("%score%", Integer.toString(generator.score))
                .replace("%time%", generator.getFormattedTime())
                .replace("%difficulty%", Double.toString(generator.getDifficultyScore()))

                .replace("%top_score%", Integer.toString(top.score()))
                .replace("%top_player%", top.name())
                .replace("%top_time%", top.time())

                .replace("%high_score%", Integer.toString(high.score()))
                .replace("%high_score_time%", high.time()));
    }

    // translate papi
    private String translate(Player player, String string) {
        return IP.getPlaceholderHook() == null ? string : PlaceholderAPI.setPlaceholders(player, string);
    }

    /**
     * @return The player's uuid
     */
    public UUID getUUID() {
        return player.getUniqueId();
    }

    /**
     * @return The player's location
     */
    public Location getLocation() {
        return player.getLocation();
    }

    /**
     * @return The player's name
     */
    public String getName() {
        return player.getName();
    }

    /**
     * @param player The player
     * @return true if the player is a Bedrock player, false if not.
     */
    public static boolean isBedrockPlayer(Player player) {
        return Bukkit.getPluginManager().isPluginEnabled("floodgate") && FloodgateHook.isBedrockPlayer(player);
    }
}