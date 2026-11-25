package me.fix.susac;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
 * Addon (not a detection): every time a hopper transfers an item it moves extra items of the SAME type from the same
 * source to the same destination, so hoppers are faster.
 *
 * Why this is done one tick later: InventoryMoveItemEvent fires in the middle of vanilla's own transfer. If we moved
 * items inside the event and vanilla's transfer then failed (e.g. the destination filled up), vanilla restores its saved
 * copy of the source stack - which still contains the items we already moved - and they were duplicated. So the event
 * only records what happened, and the extra moves run after vanilla is completely finished, on the live inventories.
 * An item is only ever removed from the source AFTER the destination has accepted it.
 *
 * Only plain storage is boosted, never furnaces, brewing stands, crafters etc, and only the item type vanilla just moved,
 * so item sorters and slot rules keep working.
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
        final int extra = Math.min(63, plugin.getConfig().getInt("hoppers.items-per-transfer", 4) - 1);
        if (extra <= 0) return;

        Inventory initiator = e.getInitiator();
        if (initiator.getType() != InventoryType.HOPPER || initiator.getHolder(false) instanceof HopperMinecart) return;
        Inventory src = e.getSource(), dst = e.getDestination();
        if (src.equals(dst) || !SAFE.contains(src.getType()) || !SAFE.contains(dst.getType())) return;

        final Location from = src.getLocation(), to = dst.getLocation();
        if (from == null || to == null) return; // not a block (minecart etc)
        final ItemStack proto = e.getItem().clone();
        if (proto.getType().isAir()) return;
        proto.setAmount(1);

        // do NOT touch any inventory here, vanilla has not finished yet
        Bukkit.getScheduler().runTask(plugin, () -> boost(from, to, proto, extra));
    }

    /** The real, current inventory of the container at this location, or null if it is gone / not a container. */
    private static Inventory live(Location l) {
        if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return null;
        BlockState st = l.getBlock().getState(false);
        return st instanceof Container c ? c.getInventory() : null;
    }

    private void boost(Location from, Location to, ItemStack proto, int extra) {
        Inventory src = live(from), dst = live(to);
        if (src == null || dst == null || src.equals(dst)) return;
        if (!SAFE.contains(src.getType()) || !SAFE.contains(dst.getType())) return;
        // shulker boxes can't go into shulker boxes, and addItem would not stop it
        if (dst.getType() == InventoryType.SHULKER_BOX && Tag.SHULKER_BOXES.isTagged(proto.getType())) return;

        int moved = 0;
        for (int i = 0; i < src.getSize() && moved < extra; i++) {
            ItemStack it = src.getItem(i);
            if (it == null || it.getType().isAir() || !it.isSimilar(proto)) continue;
            int left = it.getAmount();
            while (moved < extra && left > 0) {
                ItemStack one = proto.clone();
                one.setAmount(1);
                // destination must accept it first; only then is it taken out of the source
                if (!dst.addItem(one).isEmpty()) {
                    store(src, i, it, left);
                    return;
                }
                left--;
                moved++;
            }
            store(src, i, it, left);
        }
    }

    private static void store(Inventory src, int slot, ItemStack original, int left) {
        if (left <= 0) {
            src.setItem(slot, null);
        } else {
            ItemStack rest = original.clone();
            rest.setAmount(left);
            src.setItem(slot, rest);
        }
    }
}
