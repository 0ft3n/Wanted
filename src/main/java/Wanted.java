import com.willfp.eco.core.integrations.antigrief.AntigriefManager;
import com.willfp.eco.util.NumberUtils;
import com.willfp.ecoenchants.enchantments.EcoEnchant;
import com.willfp.ecoenchants.enchantments.EcoEnchants;
import com.willfp.ecoenchants.enchantments.meta.EnchantmentType;
import com.willfp.ecoenchants.enchantments.util.EnchantChecks;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Wanted extends EcoEnchant {

    private ArrayList<LivingEntity> friends = new ArrayList<>();

    public Wanted(){
        super("wanted", EnchantmentType.SPECIAL);
    }

    @EventHandler
    public void friendEdit(final PlayerInteractEntityEvent event) {

        if (!(event.getRightClicked() instanceof Player)){
            return;
        }

        Player player = event.getPlayer();
        Player friend = (Player) event.getRightClicked();

        if (!EnchantChecks.mainhand(player, this)) {
            return;
        }

        if (!player.isSneaking()){
            return;
        }

        if (friends.contains(friend)){
            friends.remove(friend);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',this.getConfig().getString(EcoEnchants.CONFIG_LOCATION + "message-friend-removed").replace("{p}",friend.getDisplayName())));
        }
        else {
            friends.add(friend);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',this.getConfig().getString(EcoEnchants.CONFIG_LOCATION + "message-friend-added").replace("{p}",friend.getDisplayName())));
        }
        event.setCancelled(true);

    }

    @EventHandler
    public void aimingLaunch(final ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        if (!(event.getEntity() instanceof Arrow)) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Player player = (Player) event.getEntity().getShooter();
        Arrow arrow = (Arrow) event.getEntity();

        if (!EnchantChecks.mainhand(player, this)) {
            return;
        }

        int level = EnchantChecks.getMainhandLevel(player, this);

        if (this.getDisabledWorlds().contains(player.getWorld())) {
            return;
        }

        double multiplier = this.getConfig().getDouble(EcoEnchants.CONFIG_LOCATION + "distance-per-level");

        double distance = level * multiplier;
        double force = arrow.getVelocity().clone().length() / 3;
        force = NumberUtils.equalIfOver(force, 1);

        if (this.getConfig().getBool(EcoEnchants.CONFIG_LOCATION + "require-full-force") && force < 0.9) {
            return;
        }

        if (this.getConfig().getBool(EcoEnchants.CONFIG_LOCATION + "scale-on-force")) {
            distance *= force;
        }

        final double finalDistance = distance;

        Runnable runnable = this.getPlugin().getRunnableFactory().create(bukkitRunnable -> {
            List<LivingEntity> nearbyEntities = (List<LivingEntity>) (List<?>) Arrays.asList(arrow.getNearbyEntities(finalDistance, finalDistance, finalDistance).stream()
                    .filter(entity -> entity instanceof LivingEntity)
                    .map(entity -> (LivingEntity) entity)
                    .filter(entity -> !entity.equals(player))
                    .filter(entity -> entity instanceof Player)
                    .filter(entity -> friends.contains(entity))
                    .filter(entity -> AntigriefManager.canInjure(player, entity))
                    .filter(entity -> {
                        if (entity instanceof Player) {
                            return ((Player) entity).getGameMode().equals(GameMode.SURVIVAL) || ((Player) entity).getGameMode().equals(GameMode.ADVENTURE);
                        }
                        return true;
                    }).toArray());

            if (nearbyEntities.isEmpty()) {
                return;
            }

            LivingEntity entity = nearbyEntities.get(0);
            double dist = Double.MAX_VALUE;

            for (LivingEntity livingEntity : nearbyEntities) {
                double currentDistance = livingEntity.getLocation().distance(arrow.getLocation());
                if (currentDistance >= dist) {
                    continue;
                }

                dist = currentDistance;
                entity = livingEntity;
            }
            if (entity != null) {
                Vector vector = entity.getEyeLocation().toVector().clone().subtract(arrow.getLocation().toVector()).normalize();
                arrow.setVelocity(vector);
            }
        });

        final int period = this.getConfig().getInt(EcoEnchants.CONFIG_LOCATION + "check-ticks");
        final int checks = this.getConfig().getInt(EcoEnchants.CONFIG_LOCATION + "checks-per-level") * level;
        AtomicInteger checksPerformed = new AtomicInteger(0);

        this.getPlugin().getRunnableFactory().create(bukkitRunnable -> {
            checksPerformed.addAndGet(1);
            if (checksPerformed.get() > checks) {
                bukkitRunnable.cancel();
            }
            if (arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()) {
                bukkitRunnable.cancel();
            }
            this.getPlugin().getScheduler().run(runnable);
        }).runTaskTimer(3, period);
    }
}
