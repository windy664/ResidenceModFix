package org.windy.residencemodfix;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

public final class plugin extends JavaPlugin {

    private Logger logger;
    private final Map<String, String> uuidToNameCache = new ConcurrentHashMap<>();
    private final long CACHE_REFRESH_INTERVAL = 5 * 60 * 1000; // 每5分钟更新一次缓存
    private long lastCacheUpdate = 0;

    private String usernameCachePath; // 配置中的路径
    private String message;
    private Boolean debug;

    @Override
    public void onEnable() {
        // 插件启动逻辑
        EVENT_BUS.register(this);
        logger = getLogger();
        debug = this.getConfig().getBoolean("debug",false);

        // 读取配置文件
        saveDefaultConfig(); // 确保配置文件存在
        usernameCachePath = getConfig().getString("path"); // 从配置文件中获取路径
        message = getConfig().getString("message");


        // 如果路径包含反斜杠，替换为正斜杠（跨平台兼容）
        usernameCachePath = usernameCachePath.replace("\\", "/");

        // 定时任务，每5分钟更新一次缓存
        Bukkit.getScheduler().runTaskTimer(this, this::updateCache, 0L, 5 * 60 * 20L);

    }

    @Override
    public void onDisable() {
        EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onPlayerLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {

        String name = event.getEntity().getName().toString();
        if ((name.contains("[MINECRAFT]")) ||
                name.contains("[MEKANISM]") ||
                name.contains("[IF]") ||
                name.contains("[AE2]")) {
            return;
        }
        // 修复重点：获取被点击的方块而不是脚下的方块
        BlockPos clickedPos = event.getPos();
        BlockState clickedBlockState = event.getLevel().getBlockState(clickedPos);

        log("点击的方块 " + clickedBlockState);

        // 检查被点击的方块是否符合条件
        if (!clickedBlockState.toString().contains("ae2")) {
            // 检查是否需要跳过该方块
            if (clickedBlockState.toString().contains("sign") ||
                    clickedBlockState.toString().contains("mail") ||
                    clickedBlockState.toString().contains("chair") ||
                    clickedBlockState.toString().contains("bin")) {
                return;
            }
        }

        String uuid = String.valueOf(event.getEntity().getUUID());

        String playerName = getPlayerNameFromUUID(uuid);

        if (playerName == null) {
            log("找不到玩家: " + uuid);
            return;
        }

        Player player = Bukkit.getPlayer(playerName);
        if (player == null || player.isOp()) {
            return;
        }

        int x = clickedPos.getX();
        int y = clickedPos.getY();
        int z = clickedPos.getZ();

        World world = player.getWorld();
        Location location = new Location(world,x,y,z);
        // 获取当前位置的领地
        ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(location);

        if (residence == null) {
            return;
        }

        // 检查玩家是否可以在该领地内使用方块
        boolean canUse = residence.getPermissions().playerHas(player, "use", true);
        if (!canUse) {
            event.setCanceled(true);
            player.sendMessage(message);
        }
    }

    @SubscribeEvent
    public void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {

        String name = event.getEntity().getName().toString();
        if ((name.contains("[MINECRAFT]")) ||
                name.contains("[MEKANISM]") ||
                name.contains("[IF]") ||
                name.contains("[AE2]")) {
            return;
        }
        // 修复重点：获取被点击的方块而不是脚下的方块
        BlockPos clickedPos = event.getPos();
        BlockState clickedBlockState = event.getLevel().getBlockState(clickedPos);

        log("点击的方块 " + clickedBlockState);

        // 检查被点击的方块是否符合条件
        if (!clickedBlockState.toString().contains("ae2")) {
            // 检查是否需要跳过该方块
            if (clickedBlockState.toString().contains("sign") ||
                    clickedBlockState.toString().contains("mail") ||
                    clickedBlockState.toString().contains("chair") ||
                    clickedBlockState.toString().contains("bin")) {
                return;
            }
        }

        String uuid = String.valueOf(event.getEntity().getUUID());

        String playerName = getPlayerNameFromUUID(uuid);

        if (playerName == null) {
            log("找不到玩家: " + uuid);
            return;
        }

        Player player = Bukkit.getPlayer(playerName);
        if (player == null || player.isOp()) {
            return;
        }
        int x = clickedPos.getX();
        int y = clickedPos.getY();
        int z = clickedPos.getZ();

        World world = player.getWorld();
        Location location = new Location(world,x,y,z);
        // 获取当前位置的领地
        ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(location);

        if (residence == null) {
            return;
        }

        // 检查玩家是否可以在该领地内使用方块
        boolean canUse = residence.getPermissions().playerHas(player, "use", true);
        if (!canUse) {
            event.setCanceled(true);
            player.sendMessage(message);
        }
    }

    /**
     * 根据 UUID 获取玩家名称
     *
     * @param uuid 玩家 UUID
     * @return 玩家名称，如果没有找到则返回 null
     */
    private String getPlayerNameFromUUID(String uuid) {
        // 如果缓存过期或没有缓存，更新缓存
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            updateCache();
        }

        // 从缓存中查找玩家名称
        return uuidToNameCache.get(uuid);
    }

    /**
     * 更新缓存
     */
    private void updateCache() {
        try {
            // 读取 usernamecache.json 文件
            File file = new File(usernameCachePath);

            if (!file.exists()) {
                logger.warning("找不到 usernamecache.json " + usernameCachePath);
                return;
            }

            // 使用 Gson 解析 JSON 文件
            FileReader reader = new FileReader(file);
            Gson gson = new Gson();

            // 将 JSON 转换为 Map 类型，键为 UUID 字符串，值为玩家名称
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> uuidToNameMap = gson.fromJson(reader, type);

            reader.close();

            // 更新缓存
            uuidToNameCache.clear();
            uuidToNameCache.putAll(uuidToNameMap);

            lastCacheUpdate = System.currentTimeMillis();

        } catch (IOException e) {
            log("找不到 usernamecache.json: " + e.getMessage());
        }
    }

    private void log(String message){
        if(debug) {
            logger.info(message);
        }
    }
}
