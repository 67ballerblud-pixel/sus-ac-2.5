package me.fix.susac;

import org.bukkit.Tag;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * Addon (not a detection): every time a hopper transfers an item it moves extra items from the same
 * source to the same destination, so hoppers are faster. Only plain storage is boosted, never furnaces,
 * brewing stands, crafters and so on, so slot rules are not broken.
 */
final class HopperBoost implements Listener {

    private static final Set<InventoryType> SAFE = EnumSet.of(InventoryType.CHEST, InventoryType.BARREL,
            InventoryType.SHULKER_BOX, InventoryType.HOPPER, InventoryType.DROPPER, InventoryType.DISPENSER);

    private final SusAC plugin;

    HopperBoost(SusAC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (!plugin.getConfig().getBoolean("hoppers.enabled", true)) return;
        int extra = Math.min(63, plugin.getConfig().getInt("hoppers.items-per-transfer", 4) - 1);
        if (extra <= 0) return;

        Inventory initiator = e.getInitiator();
        if (initiator.getType() != InventoryType.HOPPER || initiator.getHolder(false) instanceof HopperMinecart) return;
        Inventory src = e.getSource(), dst = e.getDestination();
        if (src.equals(dst) || !SAFE.contains(src.getType()) || !SAFE.contains(dst.getType())) return;

        int moved = 0;
        for (int i = 0; i < src.getSize() && moved < extra; i++) {
            while (moved < extra) {
                ItemStack it = src.getItem(i);
                if (it == null || it.getType().isAir()) break;
                // shulker boxes can't go into shulker boxes, and addItem would not stop it
                if (dst.getType() == InventoryType.SHULKER_BOX && Tag.SHULKER_BOXES.isTagged(it.getType())) break;
                ItemStack one = it.clone();
                one.setAmount(1);
                if (!dst.addItem(one).isEmpty()) break;
                int left = it.getAmount() - 1;
                if (left <= 0) {
                    src.setItem(i, null);
                } else {
                    ItemStack rest = it.clone();
                    rest.setAmount(left);
                    src.setItem(i, rest);
                }
                moved++;
            }
        }
    }
}
