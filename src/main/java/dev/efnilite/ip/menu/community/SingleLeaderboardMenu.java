package dev.efnilite.ip.menu.community;

import dev.efnilite.ip.IP;
import dev.efnilite.ip.api.Registry;
import dev.efnilite.ip.config.Locales;
import dev.efnilite.ip.config.Option;
import dev.efnilite.ip.leaderboard.Leaderboard;
import dev.efnilite.ip.leaderboard.Score;
import dev.efnilite.ip.menu.Menus;
import dev.efnilite.ip.menu.ParkourOption;
import dev.efnilite.ip.mode.Mode;
import dev.efnilite.ip.player.ParkourUser;
import dev.efnilite.vilib.inventory.PagedMenu;
import dev.efnilite.vilib.inventory.item.Item;
import dev.efnilite.vilib.inventory.item.MenuItem;
import dev.efnilite.vilib.util.SkullSetter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import de.tr7zw.changeme.nbtapi.NBTItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Menu for a single leaderboard
 */
public class SingleLeaderboardMenu {

    public void open(Player player, Mode mode, Leaderboard.Sort sort) {
        Leaderboard leaderboard = mode.getLeaderboard();

        if (leaderboard == null) {
            return;
        }

        // init vars
        var user = ParkourUser.getUser(player);
        var locale = user == null ? Option.OPTIONS_DEFAULTS.get(ParkourOption.LANG) : user.locale;
        var menu = new PagedMenu(3, Locales.getString(player, "%s.name".formatted(ParkourOption.LEADERBOARDS.path)));

        var items = new ArrayList<MenuItem>();

        var base = Locales.getItem(player, "%s.head".formatted(ParkourOption.LEADERBOARDS.path));

        for (Map.Entry<UUID, Score> entry : leaderboard.sort(sort).entrySet()) {
            int rank = items.size() + 1;

            var uuid = entry.getKey();
            var score = entry.getValue();

            if (score == null) {
                continue;
            }

            Item item = base.clone().material(Material.PLAYER_HEAD)
                    .modifyName(name -> name.replace("%r", Integer.toString(rank))
                            .replace("%s", Integer.toString(score.score()))
                            .replace("%p", score.name())
                            .replace("%t", score.time())
                            .replace("%d", score.difficulty()))
                    .modifyLore(line -> line.replace("%r", Integer.toString(rank))
                            .replace("%s", Integer.toString(score.score()))
                            .replace("%p", score.name())
                            .replace("%t", score.time())
                            .replace("%d", score.difficulty()));

            // Player head gathering
            ItemStack stack = item.build();
            NBTItem nbt = new NBTItem(stack);
            nbt.setString("ip-player", uuid.toString());
            stack = nbt.getItem();
            item.stack(stack);

            // if there are more than 36 players, don't show the heads to avoid server crashing
            // and bedrock has no player skull support
            if (rank <= 20 && !ParkourUser.isBedrockPlayer(player)) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

                if (op.getName() != null && !op.getName().startsWith(".")) { // bedrock players' names with geyser start with a .
                    SkullMeta meta = (SkullMeta) stack.getItemMeta();

                    if (meta != null) {
                        SkullSetter.setPlayerHead(op, meta);
                        item.meta(meta);
                    }
                }
            }

            if (uuid.equals(player.getUniqueId())) {
                menu.item(21, item.clone());
            }

            items.add(item);
        }

        List<String> values = Locales.getStringList(locale, "%s.sort.values".formatted(ParkourOption.LEADERBOARDS.path));

        if (values.size() != 3) {
            IP.logging().stack("Error while trying to get locales for sort values: not enough sort values present",
                    "check your %s locale file".formatted(locale), new IllegalArgumentException());
        }

        String name = switch (sort) {
            case SCORE -> values.get(0);
            case TIME -> values.get(1);
            case DIFFICULTY -> values.get(2);
        };

        // get next sorting type
        var next = switch (sort) {
            case SCORE -> Leaderboard.Sort.TIME;
            case TIME -> Leaderboard.Sort.DIFFICULTY;
            default -> Leaderboard.Sort.SCORE;
        };

        menu.displayRows(0, 1)
                .addToDisplay(items)
                .nextPage(26, new Item(Material.LIME_DYE, "<#0DCB07><bold>»").click(event -> menu.page(1)))
                .prevPage(18, new Item(Material.RED_DYE, "<#DE1F1F><bold>«").click(event -> menu.page(-1)))
                .item(22, Locales.getItem(player, ParkourOption.LEADERBOARDS.path + ".sort", name.toLowerCase()).click(event -> open(player, mode, next)))
                .item(23, Locales.getItem(player, "other.close").click(event -> {
                    List<Mode> modes = Registry.getModes()
                            .stream()
                            .filter(m -> m.getLeaderboard() != null && m.getItem(locale) != null)
                            .toList();

                    if (modes.size() == 1) {
                        Menus.COMMUNITY.open(player);
                        return;
                    }

                    Menus.LEADERBOARDS.open(event.getPlayer());
                }))
                .open(player);
    }
}