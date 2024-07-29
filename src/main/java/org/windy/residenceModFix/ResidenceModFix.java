package org.windy.residenceModFix;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.ResidencePlayer;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public final class ResidenceModFix extends JavaPlugin implements Listener {

    private Logger logger;
    private List<String> entityBlacklist = Arrays.asList("ZOMBIE", "SKELETON"); // 黑名单中的实体类型

    @Override
    public void onEnable() {
        // 插件启动逻辑
        Bukkit.getPluginManager().registerEvents(this, this);
        logger = getLogger();
    }

    @Override
    public void onDisable() {
        // 插件关闭逻辑
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer(); // 获取玩家

        // 检查玩家是否执行了右键操作
        if (event.getAction().name().contains("RIGHT_CLICK")) {
            // 如果玩家是管理员则不进行任何操作
            if (player.isOp()) {
                return;
            }

            // 获取被点击的方块
            Block block = event.getClickedBlock();

            // 确保被点击的方块不是null（例如，点击空气时）
            if (block != null) {
                // 手动实现光线投射检查是否正在与实体交互
                if (isTargetingEntity(player, 5)) {
                    logger.info("Player " + player.getName() + " is targeting an entity. Interaction allowed.");
                    return; // 如果正在与实体交互，则不做任何处理
                }

                // 获取当前位置的领地
                ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(block.getLocation());

                // 如果当前位置不在任何领地内，则不进行任何操作
                if (residence == null) {
                    return;
                }

                // 获取ResidencePlayer对象
                ResidencePlayer rPlayer = Residence.getInstance().getPlayerManager().getResidencePlayer(player);

                // 检查玩家是否可以在该领地内使用方块
                boolean canUse = residence.getPermissions().playerHas(player, "use", true);

                // 如果玩家不能在当前位置使用方块，则取消事件并发送消息
                if (!canUse) {
                    event.setUseInteractedBlock(Event.Result.DENY); // 禁止与被点击方块的交互
                    event.setUseItemInHand(Event.Result.DENY); // 禁止使用手中的物品
                    event.setCancelled(true); // 取消事件
                    player.sendMessage("你不能在这里使用该物品！"); // 发送无法使用的提示信息
                    logger.info("Player " + player.getName() + " tried to interact with a block but was denied.");
                }
            }
        }
    }

    private boolean isTargetingEntity(Player player, int range) {
        // 获取玩家的视线方向
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();

        // 遍历玩家附近的所有实体
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            // 检查实体是否在玩家的视线范围内
            Location entityLocation = entity.getLocation();
            Vector toEntity = entityLocation.toVector().subtract(eyeLocation.toVector()).normalize();

            // 计算方向向量与目标实体向量的点积
            double dotProduct = direction.dot(toEntity);
            logger.info("Checking entity " + entity.getType() + " with dot product: " + dotProduct);

            // 如果点积接近于1，说明玩家正在对准这个实体
            if (dotProduct > 0.8) { // 调整这个值以更严格地控制允许的角度
                // 检查玩家与实体之间的距离
                double distance = eyeLocation.distance(entityLocation);
                if (distance <= 2) { // 设置最大交互距离
                    // 检查实体是否在黑名单中
                    if (entityBlacklist.contains(entity.getType().name())) {
                        logger.info("Entity " + entity.getType() + " is blacklisted. Interaction denied.");
                        return false; // 如果是黑名单中的实体则不允许交互
                    }
                    logger.info("Player " + player.getName() + " is targeting entity " + entity.getType());
                    return true; // 如果找到目标实体则返回true
                }
            }
        }
        return false; // 如果没有找到目标实体则返回false
    }
}