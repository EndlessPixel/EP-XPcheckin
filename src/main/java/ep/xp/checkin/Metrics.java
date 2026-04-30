package ep.xp.checkin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

public class Metrics {

    private final Plugin plugin;
    private final MetricsBase metricsBase;

    public Metrics(Plugin plugin, int serviceId) {
        this.plugin = plugin;
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);
            try {
                config.save(configFile);
            } catch (IOException ignored) {
            }
        }
        boolean enabled = config.getBoolean("enabled", true);
        String serverUUID = config.getString("serverUuid");
        boolean logErrors = config.getBoolean("logFailedRequests", false);
        boolean logSentData = config.getBoolean("logSentData", false);
        boolean logResponseStatusText = config.getBoolean("logResponseStatusText", false);
        metricsBase = new MetricsBase(
                "bukkit",
                serverUUID,
                serviceId,
                enabled,
                this::appendPlatformData,
                this::appendServiceData,
                submitDataTask -> Bukkit.getScheduler().runTask(plugin, submitDataTask),
                plugin::isEnabled,
                (message, error) -> this.plugin.getLogger().log(Level.WARNING, message, error),
                (message) -> this.plugin.getLogger().log(Level.INFO, message),
                logErrors,
                logSentData,
                logResponseStatusText
        );
    }

    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }

    private void appendPlatformData(JsonObject builder) {
        builder.addProperty("playerAmount", getPlayerAmount());
        builder.addProperty("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
        builder.addProperty("bukkitVersion", Bukkit.getVersion());
        builder.addProperty("bukkitName", Bukkit.getName());
        builder.addProperty("javaVersion", System.getProperty("java.version"));
        builder.addProperty("osName", System.getProperty("os.name"));
        builder.addProperty("osArch", System.getProperty("os.arch"));
        builder.addProperty("osVersion", System.getProperty("os.version"));
        builder.addProperty("coreCount", Runtime.getRuntime().availableProcessors());
    }

    private void appendServiceData(JsonObject builder) {
        builder.addProperty("pluginVersion", plugin.getDescription().getVersion());
        JsonArray players = new JsonArray();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            players.add(player.getUniqueId().toString());
        }
        builder.add("playerNames", players);
    }

    private int getPlayerAmount() {
        try {
            Method onlinePlayersMethod = Class.forName("org.bukkit.Server").getMethod("getOnlinePlayers");
            return onlinePlayersMethod.getReturnType().equals(Collection.class)
                    ? ((Collection<?>) onlinePlayersMethod.invoke(Bukkit.getServer())).size()
                    : ((Player[]) onlinePlayersMethod.invoke(Bukkit.getServer())).length;
        } catch (Exception e) {
            return Bukkit.getOnlinePlayers().size();
        }
    }

    public static class MetricsBase {

        private final String platform;
        private final String serverUuid;
        private final int serviceId;
        private final Consumer<JsonObject> appendPlatformDataConsumer;
        private final Consumer<JsonObject> appendServiceDataConsumer;
        private final Consumer<Runnable> submitTaskConsumer;
        private final Callable<Boolean> checkEnabled;
        private final BiConsumer<String, Exception> errorLogger;
        private final Consumer<String> infoLogger;
        private final boolean logErrors;
        private final boolean logSentData;
        private final boolean logResponseStatusText;
        private final Set<CustomChart> customCharts = new HashSet<>();
        private final boolean enabled;

        public MetricsBase(
                String platform,
                String serverUuid,
                int serviceId,
                boolean enabled,
                Consumer<JsonObject> appendPlatformDataConsumer,
                Consumer<JsonObject> appendServiceDataConsumer,
                Consumer<Runnable> submitTaskConsumer,
                Callable<Boolean> checkEnabled,
                BiConsumer<String, Exception> errorLogger,
                Consumer<String> infoLogger,
                boolean logErrors,
                boolean logSentData,
                boolean logResponseStatusText
        ) {
            this.platform = platform;
            this.serverUuid = serverUuid;
            this.serviceId = serviceId;
            this.enabled = enabled;
            this.appendPlatformDataConsumer = appendPlatformDataConsumer;
            this.appendServiceDataConsumer = appendServiceDataConsumer;
            this.submitTaskConsumer = submitTaskConsumer;
            this.checkEnabled = checkEnabled;
            this.errorLogger = errorLogger;
            this.infoLogger = infoLogger;
            this.logErrors = logErrors;
            this.logSentData = logSentData;
            this.logResponseStatusText = logResponseStatusText;

            if (!enabled) {
                return;
            }

            startSubmitting();
        }

        public void addCustomChart(CustomChart chart) {
            this.customCharts.add(chart);
        }

        private void startSubmitting() {
            submitTaskConsumer.accept(this::submitData);
        }

        private void submitData() {
            try {
                if (!checkEnabled.call()) {
                    return;
                }

                JsonObject data = new JsonObject();
                appendPlatformDataConsumer.accept(data);
                data.addProperty("serviceId", serviceId);
                if (serviceId > 0) {
                    JsonObject serviceData = new JsonObject();
                    appendServiceDataConsumer.accept(serviceData);
                    JsonArray customCharts = new JsonArray();
                    for (CustomChart chart : this.customCharts) {
                        try {
                            JsonObject chartJson = chart.getRequestJsonObject();
                            if (chartJson != null) {
                                customCharts.add(chartJson);
                            }
                        } catch (Exception e) {
                            if (logErrors) {
                                errorLogger.accept("Failed to get data for custom chart with id " + chart.getChartId(), e);
                            }
                        }
                    }
                    if (customCharts.size() > 0) {
                        serviceData.add("customCharts", customCharts);
                    }
                    data.add("serviceData", serviceData);
                }
                data.addProperty("platform", platform);
                data.addProperty("serverUUID", serverUuid);
                data.addProperty("metricsVersion", "3.0.2");

                if (logSentData) {
                    infoLogger.accept("Sent bStats metrics data: " + data.toString());
                }

                String url = "https://bStats.org/submitData/bukkit";
                byte[] compressedData = compress(data.toString());
                HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.addRequestProperty("Accept", "application/json");
                    connection.addRequestProperty("Connection", "close");
                    connection.addRequestProperty("Content-Encoding", "gzip");
                    connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
                    connection.addRequestProperty("Content-Type", "application/json");
                    connection.addRequestProperty("User-Agent", "Metrics-Service/1");
                    connection.setDoOutput(true);
                    try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                        outputStream.write(compressedData);
                    }
                    int responseCode = connection.getResponseCode();
                    if (logResponseStatusText) {
                        try (InputStream inputStream = connection.getInputStream()) {
                            Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
                            scanner.useDelimiter("\\A");
                            if (scanner.hasNext()) {
                                infoLogger.accept("bStats API returned: " + scanner.next());
                            }
                        } catch (IOException e) {
                            infoLogger.accept("bStats API returned status code " + responseCode);
                        }
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                if (logErrors) {
                    errorLogger.accept("Could not submit bStats metrics data", e);
                }
            }
        }

        private byte[] compress(String str) throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
                gzip.write(str.getBytes(StandardCharsets.UTF_8));
            }
            return outputStream.toByteArray();
        }

    }

    public static abstract class CustomChart {

        private final String chartId;

        protected CustomChart(String chartId) {
            if (chartId == null) {
                throw new IllegalArgumentException("chartId must not be null");
            }
            this.chartId = chartId;
        }

        public JsonObject getRequestJsonObject() throws Exception {
            JsonObject chart = new JsonObject();
            chart.addProperty("chartId", chartId);
            JsonObject data = getChartData();
            if (data == null) {
                return null;
            }
            chart.add("data", data);
            return chart;
        }

        protected abstract JsonObject getChartData() throws Exception;

        public String getChartId() {
            return chartId;
        }

    }

    public static class SimplePie extends CustomChart {

        private final Callable<String> callable;

        public SimplePie(String chartId, Callable<String> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            String value = callable.call();
            if (value == null || value.isEmpty()) {
                return null;
            }
            data.addProperty("value", value);
            return data;
        }

    }

    public static class AdvancedPie extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue;
                }
                allSkipped = false;
                values.addProperty(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                return null;
            }
            data.add("values", values);
            return data;
        }

    }

    public static class DrilldownPie extends CustomChart {

        private final Callable<Map<String, Map<String, Integer>>> callable;

        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        public JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Map<String, Integer>> map = callable.call();
            if (map == null || map.isEmpty()) {
                return null;
            }
            boolean reallyAllSkipped = true;
            for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
                JsonObject value = new JsonObject();
                boolean allSkipped = true;
                for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                    value.addProperty(valueEntry.getKey(), valueEntry.getValue());
                    allSkipped = false;
                }
                if (!allSkipped) {
                    reallyAllSkipped = false;
                    values.add(entryValues.getKey(), value);
                }
            }
            if (reallyAllSkipped) {
                return null;
            }
            data.add("values", values);
            return data;
        }

    }

    public static class SingleLineChart extends CustomChart {

        private final Callable<Integer> callable;

        public SingleLineChart(String chartId, Callable<Integer> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            int value = callable.call();
            if (value == 0) {
                return null;
            }
            data.addProperty("value", value);
            return data;
        }

    }

    public static class MultiLineChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getValue() == 0) {
                    continue;
                }
                allSkipped = false;
                values.addProperty(entry.getKey(), entry.getValue());
            }
            if (allSkipped) {
                return null;
            }
            data.add("values", values);
            return data;
        }

    }

    public static class SimpleBarChart extends CustomChart {

        private final Callable<Map<String, Integer>> callable;

        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, Integer> map = callable.call();
            if (map == null || map.isEmpty()) {
                return null;
            }
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                JsonArray categoryValues = new JsonArray();
                categoryValues.add(entry.getValue());
                values.add(entry.getKey(), categoryValues);
            }
            data.add("values", values);
            return data;
        }

    }

    public static class AdvancedBarChart extends CustomChart {

        private final Callable<Map<String, int[]>> callable;

        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {
            super(chartId);
            this.callable = callable;
        }

        @Override
        protected JsonObject getChartData() throws Exception {
            JsonObject data = new JsonObject();
            JsonObject values = new JsonObject();
            Map<String, int[]> map = callable.call();
            if (map == null || map.isEmpty()) {
                return null;
            }
            boolean allSkipped = true;
            for (Map.Entry<String, int[]> entry : map.entrySet()) {
                if (entry.getValue().length == 0) {
                    continue;
                }
                allSkipped = false;
                JsonArray categoryValues = new JsonArray();
                for (int value : entry.getValue()) {
                    categoryValues.add(value);
                }
                values.add(entry.getKey(), categoryValues);
            }
            if (allSkipped) {
                return null;
            }
            data.add("values", values);
            return data;
        }

    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t);
    }

    @FunctionalInterface
    public interface BiConsumer<T, U> {
        void accept(T t, U u);
    }

}
