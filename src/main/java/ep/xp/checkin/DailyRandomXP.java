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
import java.util.concurrent.TimeUnit;

public final class DailyRandomXP extends JavaPlugin implements Listener, TabCompleter {

    // 配置 & 语言
    private FileConfiguration langConfig;
    private String PREFIX;

    // 设置项
    private int COOLDOWN;
    private int JOIN_DELAY;
    private double STREAK_MULTI;
    private int BROADCAST_LIMIT;
    private Map<Integer, List<Integer>> XP_RANGES;

    // 数据
    private File playerFile;
    private FileConfiguration playerData;
    private final Map<UUID, Long> cooldownMap = new HashMap<>();
    private final Random random = new Random();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultLanguage();
        reloadAll();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("checkin").setTabCompleter(this);
        setupPlayerData();

        int pluginId = 31016;
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info("=====================================");
        getLogger().info(" EP-XPcheckin 1.3 已成功加载！");
        getLogger().info(" 作者: system_mini | EndlessPixel");
        getLogger().info("=====================================");
    }

    // ====================== 重载 ======================
    public void reloadAll() {
        reloadConfig();
        loadConfig();
        loadLanguage();
    }

    // Load Configuration Settings
    private void loadConfig() {
        COOLDOWN = getConfig().getInt("settings.command-cooldown", 5);
        JOIN_DELAY = getConfig().getInt("settings.join-remind-delay", 20);
        STREAK_MULTI = getConfig().getDouble("settings.streak-multiplier", 0.1);
        BROADCAST_LIMIT = getConfig().getInt("settings.broadcast-above-xp", 1000000);
        
        XP_RANGES = new HashMap<>();
        ConfigurationSection xpRangesSection = getConfig().getConfigurationSection("xp-ranges");
        if (xpRangesSection != null) {
            for (String key : xpRangesSection.getKeys(false)) {
                int weight = Integer.parseInt(key);
                List<Integer> range = xpRangesSection.getIntegerList(key);
                if (range.size() >= 2) {
                    XP_RANGES.put(weight, range);
                }
            }
        }
        if (XP_RANGES.isEmpty()) {
            XP_RANGES = getDefaultXpRanges();
        }
    }
    
    private Map<Integer, List<Integer>> getDefaultXpRanges() {
        Map<Integer, List<Integer>> defaultRanges = new HashMap<>();
        defaultRanges.put(0, Arrays.asList(1, 9));
        defaultRanges.put(1, Arrays.asList(10, 99));
        defaultRanges.put(2, Arrays.asList(100, 999));
        defaultRanges.put(3, Arrays.asList(1000, 9999));
        defaultRanges.put(4, Arrays.asList(10000, 99999));
        defaultRanges.put(5, Arrays.asList(100000, 999999));
        defaultRanges.put(6, Arrays.asList(1000000, 9999999));
        defaultRanges.put(7, Arrays.asList(10000000, 99999999));
        defaultRanges.put(8, Arrays.asList(100000000, 999999999));
        defaultRanges.put(9, Arrays.asList(1000000000, 2147483647));
        return defaultRanges;
    }

    // ====================== 语言文件 ======================
    private void saveDefaultLanguage() {
     List<String> builtInLangs = Arrays.asList("zh-CN", "en");
     for (String lang : builtInLangs) {
        File langFile = new File(getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("lang/" + lang + ".yml", false);
        }
      }
    }

    private void loadLanguage() {
        String lang = getConfig().getString("language.default", "zh-CN");
        File langFile = new File(getDataFolder(), "lang/" + lang + ".yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        PREFIX = langConfig.getString("prefix", "§a[§7EP-XPcheckin§a] ");
    }

    private String msg(String key) {
        return langConfig.getString(key, "§c缺失消息: " + key);
    }

    // ====================== 玩家数据 ======================
    private void setupPlayerData() {
        playerFile = new File(getDataFolder(), "player.yml");
        if (!playerFile.exists()) {
            playerFile.getParentFile().mkdirs();
            try {
                playerFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe(langConfig.getString("log-create-playerdata-fail", "创建 player.yml 失败!"));
            }
        }
        playerData = YamlConfiguration.loadConfiguration(playerFile);
    }

    private void savePlayerData() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                playerData.save(playerFile);
            } catch (IOException e) {
                getLogger().severe(langConfig.getString("log-save-playerdata-fail", "保存 player.yml 失败!"));
            }
        });
    }

    // ====================== 随机经验 ======================
    // 对数均匀分布：在配置整体区间的 log 空间内均匀随机。
    // 小值密集、大值稀疏且稀有（保留"大奖稀有合理"特性），
    // 同时彻底消除离散档位跳变，分布平滑连续、统计均匀。
    private long getFairRandomXP() {
        if (XP_RANGES == null || XP_RANGES.isEmpty()) return 10;

        long gMin = Long.MAX_VALUE;
        long gMax = Long.MIN_VALUE;
        for (List<Integer> range : XP_RANGES.values()) {
            if (range != null && range.size() >= 2) {
                gMin = Math.min(gMin, range.get(0));
                gMax = Math.max(gMax, range.get(1));
            }
        }
        if (gMin <= 0 || gMin > gMax) return 10;

        double logMin = Math.log10(gMin);
        double logMax = Math.log10(gMax);
        double value = Math.pow(10, logMin + random.nextDouble() * (logMax - logMin));
        long result = (long) Math.floor(value);
        if (result < gMin) result = gMin;
        if (result > gMax) result = gMax;
        return result;
    }

    // ====================== 进服提醒 ======================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!playerData.getBoolean(uuid + ".remind", true)) return;

        LocalDate today = LocalDate.now();
        String todayStr = today.format(DATE_FORMAT);
        String lastDate = playerData.getString(uuid + ".lastCheckInDate", "");

        if (!todayStr.equals(lastDate)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    p.sendMessage(PREFIX + msg("join-remind"));
                }
            }.runTaskLater(this, JOIN_DELAY);
        }
    }

    // ====================== 指令 ======================
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("checkin")) return false;

        // 冷却
        if (sender instanceof Player p) {
            UUID uuid = p.getUniqueId();
            long now = System.currentTimeMillis();
            if (cooldownMap.containsKey(uuid)) {
                long last = cooldownMap.get(uuid);
                if (now - last < COOLDOWN * 50L) {
                    p.sendMessage(PREFIX + msg("too-fast"));
                    return true;
                }
            }
            cooldownMap.put(uuid, now);
        }

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(PREFIX + msg("only-player"));
                return true;
            }
            doCheckIn(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "on":
            case "off": {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(PREFIX + msg("only-player"));
                    return true;
                }
                UUID uuid = p.getUniqueId();
                boolean enable = sub.equals("on");
                playerData.set(uuid + ".remind", enable);
                savePlayerData();
                p.sendMessage(PREFIX + msg(enable ? "remind-on" : "remind-off"));
                return true;
            }
            case "info": {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(PREFIX + msg("only-player"));
                    return true;
                }
                showInfo(p);
                return true;
            }
            case "reload": {
                if (!sender.isOp()) {
                    sender.sendMessage(PREFIX + msg("no-permission"));
                    return true;
                }
                reloadAll();
                sender.sendMessage(PREFIX + msg("reloaded"));
                return true;
            }
            case "record": {
                if (!sender.isOp()) {
                    sender.sendMessage(PREFIX + msg("no-permission"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(PREFIX + msg("usage-record"));
                    return true;
                }
                deleteRecord(sender, args[1], args[2]);
                return true;
            }
            case "top": {
                showTop(sender, args);
                return true;
            }
            case "look": {
                if (!sender.isOp()) {
                    sender.sendMessage(PREFIX + msg("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(PREFIX + msg("usage-look"));
                    return true;
                }
                opLook(sender, args[1]);
                return true;
            }
            default: {
                sender.sendMessage(PREFIX + msg("usage-main"));
                return true;
            }
        }
    }

    // ====================== 功能 ======================
    private void doCheckIn(Player p) {
        UUID uuid = p.getUniqueId();
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DATE_FORMAT);
        String last = playerData.getString(uuid + ".lastCheckInDate", "");

        if (todayStr.equals(last)) {
            p.sendMessage(PREFIX + msg("already-checkin"));
            return;
        }

        playerData.set(uuid + ".name", p.getName());
        long base = getFairRandomXP();
        int streak = 1;

        if (!last.isEmpty()) {
            try {
                LocalDate lastDate = LocalDate.parse(last, DATE_FORMAT);
                if (lastDate.plusDays(1).isEqual(today)) {
                    streak = playerData.getInt(uuid + ".streak") + 1;
                }
            } catch (Exception ignored) {}
        }

        long finalXP = (long) (base * (1 + (streak - 1) * STREAK_MULTI));

        playerData.set(uuid + ".lastCheckInDate", todayStr);
        playerData.set(uuid + ".streak", streak);
        playerData.set(uuid + ".totalTimes", playerData.getInt(uuid + ".totalTimes", 0) + 1);
        playerData.set(uuid + ".totalXP", playerData.getLong(uuid + ".totalXP", 0) + finalXP);
        playerData.set(uuid + ".records." + todayStr + ".xp", finalXP);
        playerData.set(uuid + ".records." + todayStr + ".streak", streak);
        savePlayerData();
        p.giveExp((int) Math.min(finalXP, Integer.MAX_VALUE));

        p.sendMessage(PREFIX + msg("checkin-success"));
        p.sendMessage(PREFIX + msg("base-xp").replace("%num%", String.valueOf(base)));
        p.sendMessage(PREFIX + msg("streak").replace("%num%", String.valueOf(streak)).replace("%percent%", String.valueOf((streak - 1) * 10)));
        p.sendMessage(PREFIX + msg("final-xp").replace("%num%", String.valueOf(finalXP)));
        p.sendMessage(PREFIX + msg("total-info")
                .replace("%times%", String.valueOf(playerData.getInt(uuid + ".totalTimes")))
                .replace("%xp%", String.valueOf(playerData.getLong(uuid + ".totalXP"))));

        if (finalXP >= BROADCAST_LIMIT) {
            Bukkit.broadcastMessage(PREFIX + msg("broadcast-lucky")
                    .replace("%player%", p.getName()).replace("%num%", String.valueOf(finalXP)));
        }
    }

    private void showInfo(Player p) {
        UUID uuid = p.getUniqueId();
        p.sendMessage(PREFIX + msg("info-header"));
        p.sendMessage(PREFIX + msg("info-streak").replace("%num%", String.valueOf(playerData.getInt(uuid + ".streak", 0))));
        p.sendMessage(PREFIX + msg("info-total-times").replace("%num%", String.valueOf(playerData.getInt(uuid + ".totalTimes", 0))));
        p.sendMessage(PREFIX + msg("info-total-xp").replace("%num%", String.valueOf(playerData.getLong(uuid + ".totalXP", 0))));

        ConfigurationSection sec = playerData.getConfigurationSection(uuid + ".records");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            p.sendMessage(PREFIX + msg("no-record"));
            return;
        }

        p.sendMessage(PREFIX + msg("recent-record"));
        List<String> dates = new ArrayList<>(sec.getKeys(false));
        dates.sort(Collections.reverseOrder());
        for (String d : dates) {
            int xp = playerData.getInt(uuid + ".records." + d + ".xp");
            int s = playerData.getInt(uuid + ".records." + d + ".streak", 1);
            p.sendMessage(PREFIX + "§f" + d + " §7| §a" + xp + " XP §7| 连续" + s + "天");
        }
    }

    private static final int TOP_PAGE_SIZE = 10;

    private void showTop(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                sender.sendMessage(PREFIX + msg("top-invalid-page"));
                return;
            }
        }
        if (page < 1) page = 1;

        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (String key : playerData.getKeys(false)) {
            try {
                UUID.fromString(key);
                long total = playerData.getLong(key + ".totalXP", 0);
                if (total > 0) list.add(new AbstractMap.SimpleEntry<>(key, total));
            } catch (Exception ignored) {}
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        int totalPages = Math.max(1, (int) Math.ceil((double) list.size() / TOP_PAGE_SIZE));
        if (page > totalPages) page = totalPages;

        sender.sendMessage(PREFIX + msg("top-header").replace("%page%", String.valueOf(page)).replace("%pages%", String.valueOf(totalPages)));
        int start = (page - 1) * TOP_PAGE_SIZE;
        int end = Math.min(start + TOP_PAGE_SIZE, list.size());
        int rank = start + 1;
        for (int i = start; i < end; i++) {
            Map.Entry<String, Long> entry = list.get(i);
            String name = playerData.getString(entry.getKey() + ".name", "未知玩家");
            sender.sendMessage(PREFIX + "§7#" + rank + " §f" + name + " §7| §a" + entry.getValue() + " XP");
            rank++;
        }

        if (totalPages > 1) {
            sender.sendMessage(PREFIX + msg("top-footer")
                    .replace("%page%", String.valueOf(page))
                    .replace("%pages%", String.valueOf(totalPages))
                    .replace("%next%", String.valueOf(page < totalPages ? page + 1 : page))
                    .replace("%prev%", String.valueOf(page > 1 ? page - 1 : page)));
        }
    }

    private void opLook(CommandSender sender, String target) {
        UUID uuid = null;
        String name = target;

        try {
            uuid = UUID.fromString(target);
            name = playerData.getString(uuid + ".name", "未知玩家");
        } catch (Exception ex) {
            for (String key : playerData.getKeys(false)) {
                try {
                    UUID.fromString(key);
                    String n = playerData.getString(key + ".name", "");
                    if (n.equalsIgnoreCase(target)) {
                        uuid = UUID.fromString(key);
                        name = n;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (uuid == null || !playerData.contains(uuid.toString())) {
            sender.sendMessage(PREFIX + msg("player-not-found"));
            return;
        }

        sender.sendMessage(PREFIX + msg("look-header").replace("%player%", name));
        sender.sendMessage(PREFIX + msg("info-streak").replace("%num%", String.valueOf(playerData.getInt(uuid + ".streak", 0))));
        sender.sendMessage(PREFIX + msg("info-total-times").replace("%num%", String.valueOf(playerData.getInt(uuid + ".totalTimes", 0))));
        sender.sendMessage(PREFIX + msg("info-total-xp").replace("%num%", String.valueOf(playerData.getLong(uuid + ".totalXP", 0))));
    }

    private void deleteRecord(CommandSender sender, String t, String date) {
        UUID uuid;
        try {
            uuid = UUID.fromString(t);
        } catch (Exception ex) {
            Player p = Bukkit.getPlayerExact(t);
            if (p == null) {
                sender.sendMessage(PREFIX + msg("invalid-player"));
                return;
            }
            uuid = p.getUniqueId();
        }

        String path = uuid + ".records." + date;
        if (!playerData.contains(path)) {
            sender.sendMessage(PREFIX + msg("record-not-found"));
            return;
        }

        long removed = playerData.getLong(path + ".xp");
        playerData.set(path, null);
        playerData.set(uuid + ".totalTimes", Math.max(0, playerData.getInt(uuid + ".totalTimes", 0) - 1));
        playerData.set(uuid + ".totalXP", Math.max(0L, playerData.getLong(uuid + ".totalXP", 0) - removed));

        LocalDate last = null;
        ConfigurationSection sec = playerData.getConfigurationSection(uuid + ".records");
        if (sec != null) {
            for (String d : sec.getKeys(false)) {
                try {
                    LocalDate ld = LocalDate.parse(d, DATE_FORMAT);
                    if (last == null || ld.isAfter(last)) last = ld;
                } catch (Exception ignored) {}
            }
        }

        if (last != null) {
            playerData.set(uuid + ".lastCheckInDate", last.format(DATE_FORMAT));
        } else {
            playerData.set(uuid + ".lastCheckInDate", "");
            playerData.set(uuid + ".streak", 0);
        }

        savePlayerData();
        sender.sendMessage(PREFIX + msg("record-deleted").replace("%date%", date));
    }

    // ====================== 补全 ======================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("checkin")) return null;
        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.addAll(Arrays.asList("on", "off", "info", "top"));
            if (sender.isOp()) list.addAll(Arrays.asList("reload", "record", "look"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("record") && sender.isOp()) {
            for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("record") && sender.isOp()) {
            Player p = Bukkit.getPlayerExact(args[1]);
            if (p != null) {
                ConfigurationSection sec = playerData.getConfigurationSection(p.getUniqueId() + ".records");
                if (sec != null) list.addAll(sec.getKeys(false));
            }
        }
        return list;
    }
}