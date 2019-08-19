/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.espi.protectionstones.commands;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.RegionTraverse;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArgView implements PSCommandArg {

    private static List<UUID> cooldown = new ArrayList<>();

    @Override
    public List<String> getNames() {
        return Collections.singletonList("view");
    }

    @Override
    public boolean allowNonPlayersToExecute() {
        return false;
    }

    @Override
    public List<String> getPermissionsToExecute() {
        return Arrays.asList("protectionstones.view");
    }

    @Override
    public boolean executeArgument(CommandSender s, String[] args) {
        Player p = (Player) s;

        PSRegion r = PSRegion.fromLocation(p.getLocation());

        if (!p.hasPermission("protectionstones.view")) {
            PSL.msg(p, PSL.NO_PERMISSION_VIEW.msg());
            return true;
        }
        if (r == null) {
            PSL.msg(p, PSL.NOT_IN_REGION.msg());
            return true;
        }
        if (WGUtils.hasNoAccess(r.getWGRegion(), p, WorldGuardPlugin.inst().wrapPlayer(p), true)) {
            PSL.msg(p, PSL.NO_ACCESS.msg());
            return true;
        }
        if (cooldown.contains(p.getUniqueId())) {
            PSL.msg(p, PSL.VIEW_COOLDOWN.msg());
            return true;
        }

        PSL.msg(p, PSL.VIEW_GENERATING.msg());

        // add player to cooldown
        cooldown.add(p.getUniqueId());
        Bukkit.getScheduler().runTaskLaterAsynchronously(ProtectionStones.getInstance(), () -> cooldown.remove(p.getUniqueId()), 20 * ProtectionStones.getInstance().getConfigOptions().psViewCooldown);

        int playerY = p.getLocation().getBlockY(), minY = r.getWGRegion().getMinimumPoint().getBlockY(), maxY = r.getWGRegion().getMaximumPoint().getBlockY();

        BlockData tempBlock = Material.GLOWSTONE.createBlockData();

        // send fake blocks to client

        Bukkit.getScheduler().runTaskAsynchronously(ProtectionStones.getInstance(), () -> {

            List<Block> blocks = new ArrayList<>();

            AtomicInteger wait = new AtomicInteger(0), modU = new AtomicInteger(0);

            RegionTraverse.traverseRegionEdge(new HashSet<>(r.getWGRegion().getPoints()), Collections.singletonList(r.getWGRegion()), tr -> {
                if (tr.isVertex) {
                    handleFakeBlock(p, tr.point.getX(), playerY, tr.point.getZ(), tempBlock, blocks, 1, wait.get());
                    for (int y = minY; y <= maxY; y += 10) {
                        wait.incrementAndGet();
                        handleFakeBlock(p, tr.point.getX(), y, tr.point.getZ(), tempBlock, blocks, 1, wait.get());
                    }
                } else {
                    //if (modU.get() % 4 == 0) {
                        handleFakeBlock(p, tr.point.getX(), playerY, tr.point.getZ(), tempBlock, blocks, 1, wait.get() / 2);
                        handleFakeBlock(p, tr.point.getX(), minY, tr.point.getZ(), tempBlock, blocks, 1, wait.get() / 2);
                        handleFakeBlock(p, tr.point.getX(), maxY, tr.point.getZ(), tempBlock, blocks, 1, wait.get() / 2);
                    //}
                    modU.set((modU.get() + 1) % 7);
                }
            });

            /*// base lines
            for (int x : xs)
                for (int y : ys)
                    for (int z : zs) {
                        handleFakeBlock(p, x, y, z, tempBlock, blocks, 0, 0);
                    }

            int wait = 0;
            // x lines
            for (int x = Math.max(xs[0], playerX - 40); x <= Math.min(xs[1], playerX + 40); x += 7) { // max radius of 40
                for (int z : zs) {
                    for (int y : ys) {
                        wait++;
                        handleFakeBlock(p, x, y, z, tempBlock, blocks, 1, wait / 2);
                    }
                }
            }

            // z lines
            for (int z = Math.max(zs[0], playerZ - 40); z <= Math.min(zs[1], playerZ + 40); z += 7) { // max radius of 40
                for (int x : xs) {
                    for (int y : ys) {
                        wait++;
                        handleFakeBlock(p, x, y, z, tempBlock, blocks, 1, wait / 2);
                    }
                }
            }

            // y lines last
            for (int y = Math.max(ys[1], playerY - 40); y <= Math.min(ys[2], playerY + 40); y += 10) {
                for (int x : xs) {
                    for (int z : zs) {
                        wait++;
                        handleFakeBlock(p, x, y, z, tempBlock, blocks, 1, wait);
                    }
                }
            }*/

            Bukkit.getScheduler().runTaskLater(ProtectionStones.getInstance(), () -> PSL.msg(p, PSL.VIEW_GENERATE_DONE.msg()), wait.get());

            Bukkit.getScheduler().runTaskLaterAsynchronously(ProtectionStones.getInstance(), () -> {
                PSL.msg(p, PSL.VIEW_REMOVING.msg());
                for (Block b : blocks) {
                    if (b.getWorld().isChunkLoaded(b.getLocation().getBlockX() / 16, b.getLocation().getBlockZ() / 16)) {
                        p.sendBlockChange(b.getLocation(), b.getBlockData());
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, 600L); // remove after 10 seconds
        });
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return null;
    }

    private static void handleFakeBlock(Player p, int x, int y, int z, BlockData tempBlock, List<Block> restore, long delay, long multiplier) {
        if (p.getLocation().distance(new Location(p.getWorld(), x, y, z)) > 100) return;
        Bukkit.getScheduler().runTaskLater(ProtectionStones.getInstance(), () -> {
            if (p.getWorld().isChunkLoaded(x / 16, z / 16)) {
                restore.add(p.getWorld().getBlockAt(x, y, z));
                p.sendBlockChange(p.getWorld().getBlockAt(x, y, z).getLocation(), tempBlock);
            }
        }, delay * multiplier);
    }
}
