package ep.xp.checkin;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DailyRandomXP extends JavaPlugin implements Listener, TabCompleter {

    private static final String PREFIX = "§l§a[§7EndlessPixel§l§a] ";
    private File playerFile;
    private FileConfiguration playerData;
    private final Random random = new Random();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("checkin").setTabCompleter(this);
        setupPlayerData();
    }

    private void setupPlayerData() {
        playerFile = new File(getDataFolder(), "player.yml");
        if (!playerFile.exists()) {
            playerFile.mkdirs();
        }
        playerData = YamlConfiguration.loadConfiguration(playerFile);
    }

    private void savePlayerData() {
        try {
            playerData.save(playerFile);
        } catch (IOException e) {
            getLogger().severe("保存 player.yml 失败!");
        }
    }

    private void reloadPlayerData() {
        playerData = YamlConfiguration.loadConfiguration(playerFile);
    }

    private int getFairRandomXP() {
        double r = random.nextDouble();
        return switch ((int) (r * 10)) {
            case 0 -> 1 + random.nextInt(9);
            case 1 -> 10 + random.nextInt(90);
            case 2 -> 100 + random.nextInt(900);
            case 3 -> 1000 + random.nextInt(9000);
            case 4 -> 10000 + random.nextInt(90000);
            case 5 -> 100000 + random.nextInt(900000);
            case 6 -> 1000000 + random.nextInt(9000000);
            case 7 -> 10000000 + random.nextInt(90000000);
            case 8 -> 100000000 + random.nextInt(900000000);
            default -> 1000000000 + random.nextInt(1147483647);
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        boolean remind = playerData.getBoolean(uuid + ".remind", true);
        if (!remind) return;

        LocalDate today = LocalDate.now();
        String todayStr = today.format(DATE_FORMAT);
        String lastDate = playerData.getString(uuid + ".lastCheckInDate", "");

        if (!todayStr.equals(lastDate)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    p.sendMessage(PREFIX + "§e⚠ 你今天还未签到，输入 §f/checkin §e进行签到！");
                }
            }.runTaskLater(this, 20);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("checkin")) return false;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§c☒ 只有玩家可以签到！");
                return true;
            }
            doCheckIn(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("on") || sub.equals("off")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§c☒ 只有玩家可以使用！");
                return true;
            }
            UUID uuid = p.getUniqueId();
            boolean enable = sub.equals("on");
            playerData.set(uuid + ".remind", enable);
            savePlayerData();
            p.sendMessage(PREFIX + "§a☑ 已" + (enable ? "开启" : "关闭") + "签到提醒！");
            return true;
        }

        if (sub.equals("info")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + "§c☒ 只有玩家可以使用！");
                return true;
            }
            showCheckInInfo(p);
            return true;
        }

        if (sub.equals("redata")) {
            if (!sender.isOp()) {
                sender.sendMessage(PREFIX + "§c☒ 你没有权限！");
                return true;
            }
            reloadPlayerData();
            sender.sendMessage(PREFIX + "§a☑ 已重新加载签到数据！");
            return true;
        }

        if (sub.equals("record")) {
            if (!sender.isOp()) {
                sender.sendMessage(PREFIX + "§c☒ 你没有权限！");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "§c☒ 用法: /checkin record <玩家> <日期>");
                return true;
            }

            String targetName = args[1];
            String dateStr = args[2];
            Player targetPlayer = Bukkit.getPlayerExact(targetName);
            UUID uuid;

            if (targetPlayer != null) {
                uuid = targetPlayer.getUniqueId();
            } else {
                try {
                    uuid = UUID.fromString(targetName);
                } catch (Exception ex) {
                    sender.sendMessage(PREFIX + "§c☒ 无效玩家名或UUID！");
                    return true;
                }
            }

            String recordPath = uuid + ".records." + dateStr;
            if (!playerData.contains(recordPath)) {
                sender.sendMessage(PREFIX + "§c☒ 该日期不存在记录！");
                return true;
            }

            int removedXp = playerData.getInt(recordPath + ".xp");
            int totalTimes = playerData.getInt(uuid + ".totalTimes", 0);
            int totalXP = playerData.getInt(uuid + ".totalXP", 0);

            playerData.set(recordPath, null);
            playerData.set(uuid + ".totalTimes", Math.max(0, totalTimes - 1));
            playerData.set(uuid + ".totalXP", Math.max(0, totalXP - removedXp));

            LocalDate lastDate = getLastRecordDate(uuid);
            if (lastDate != null) {
                playerData.set(uuid + ".lastCheckInDate", lastDate.format(DATE_FORMAT));
            } else {
                playerData.set(uuid + ".lastCheckInDate", null);
                playerData.set(uuid + ".streak", 0);
            }

            savePlayerData();
            sender.sendMessage(PREFIX + "§a☑ 已彻底删除记录: " + dateStr);
            return true;
        }

        sender.sendMessage(PREFIX + "§c☒ 用法: /checkin | on | off | info | redata | record <player> <date>");
        return true;
    }

    private LocalDate getLastRecordDate(UUID uuid) {
        ConfigurationSection section = playerData.getConfigurationSection(uuid + ".records");
        if (section == null) return null;

        LocalDate last = null;
        for (String dateStr : section.getKeys(false)) {
            try {
                LocalDate d = LocalDate.parse(dateStr, DATE_FORMAT);
                if (last == null || d.isAfter(last)) last = d;
            } catch (Exception ignored) {}
        }
        return last;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("checkin")) return null;
        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.addAll(Arrays.asList("on", "off", "info"));
            if (sender.isOp()) {
                list.addAll(Arrays.asList("redata", "record"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("record") && sender.isOp()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("record") && sender.isOp()) {
            String playerName = args[1];
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null) {
                ConfigurationSection section = playerData.getConfigurationSection(p.getUniqueId() + ".records");
                if (section != null) {
                    list.addAll(section.getKeys(false));
                }
            }
        }

        return list;
    }

    private void doCheckIn(Player p) {
        UUID uuid = p.getUniqueId();
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DATE_FORMAT);
        String lastDateStr = playerData.getString(uuid + ".lastCheckInDate", "");

        if (todayStr.equals(lastDateStr)) {
            p.sendMessage(PREFIX + "§c☒ 你今天已经签到过了！");
            return;
        }

        int baseXP = getFairRandomXP();
        LocalDate lastDate = lastDateStr.isEmpty() ? null : LocalDate.parse(lastDateStr, DATE_FORMAT);
        int streak = (lastDate != null && lastDate.plusDays(1).equals(today))
                ? playerData.getInt(uuid + ".streak") + 1
                : 1;

        double multi = 1.0 + (streak - 1) * 0.1;
        int finalXP = (int) (baseXP * multi);

        playerData.set(uuid + ".lastCheckInDate", todayStr);
        playerData.set(uuid + ".streak", streak);
        playerData.set(uuid + ".totalTimes", playerData.getInt(uuid + ".totalTimes", 0) + 1);
        playerData.set(uuid + ".totalXP", playerData.getInt(uuid + ".totalXP", 0) + finalXP);

        String recordPath = uuid + ".records." + todayStr;
        playerData.set(recordPath + ".xp", finalXP);
        playerData.set(recordPath + ".streak", streak);
        savePlayerData();
        p.giveExp(finalXP);

        p.sendMessage(PREFIX + "§a☑ 签到成功！");
        p.sendMessage(PREFIX + "§f基础经验: §e" + baseXP);
        p.sendMessage(PREFIX + "§f连续签到: §a" + streak + " 天 §7(+" + ((streak - 1) * 10) + "%)");
        p.sendMessage(PREFIX + "§f最终获得: §b" + finalXP + " XP");
        p.sendMessage(PREFIX + "§f累计: §e" + playerData.getInt(uuid + ".totalTimes") + "次 §f| 总经验: §e" + playerData.getInt(uuid + ".totalXP"));
    }

    private void showCheckInInfo(Player p) {
        UUID uuid = p.getUniqueId();
        p.sendMessage(PREFIX + "§7----- §a你的签到信息 §7-----");
        p.sendMessage(PREFIX + "§f连续签到: §a" + playerData.getInt(uuid + ".streak", 0) + " 天");
        p.sendMessage(PREFIX + "§f累计签到: §a" + playerData.getInt(uuid + ".totalTimes", 0) + " 次");
        p.sendMessage(PREFIX + "§f累计经验: §a" + playerData.getInt(uuid + ".totalXP", 0) + " XP");

        ConfigurationSection sec = playerData.getConfigurationSection(uuid + ".records");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            p.sendMessage(PREFIX + "§7暂无记录");
            return;
        }

        p.sendMessage(PREFIX + "§7最近记录:");
        for (String d : sec.getKeys(false)) {
            int xp = playerData.getInt(uuid + ".records." + d + ".xp");
            int s = playerData.getInt(uuid + ".records." + d + ".streak", 1);
            p.sendMessage(PREFIX + "§f" + d + " §7| 连续" + s + "天 | §a" + xp + " XP");
        }
    }
}