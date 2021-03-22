import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
public class HubListeners implements Listener, PluginMessageListener {
    
    HubMain p;
    Inventory inv;
    List<UUID> cooldown = new ArrayList<>();
    List<UUID> jumpCooldown = new ArrayList<>();
    List<Player> hiddenToggledLoggedIn = new ArrayList<>();
    ItemStack serverItem, showPlayersItem, hidePlayersItem;
    int hidePlayerItemSlot, jumpCooldownTime;
    Material launchPadMaterial;
    double yVel, xVelMul, xVelBase, doubleJumpYVel;
    boolean loginSoundEnabled, loginTitleEnabled, loginFireworkEnabled;
    Sound loginSound;
    String loginTitle, loginSubtitle;
    FireworkMeta loginFirework;
    Scoreboard scoreboard;
    Objective objective;
    Map<String, String> serverCounts = new HashMap<>(); //Server name, player count
    Map<String, String> prefixes = new HashMap<>(); //Team name, player count
    Map<String, Team> teamsToUpdate = new HashMap<>(); //Team name, team
    Map<String, String> teamServers = new HashMap<>(); //Team name, server name
    
    HubListeners(HubMain p){
        this.p = p;
        p.getServer().getMessenger().registerIncomingPluginChannel(p, "BungeeCord", this);
        ConfigurationSection c = p.getConfig();
        
        inv = Bukkit.createInventory(null, InventoryType.PLAYER, "urmom");
        
        serverItem = FracturedMain.loadItem("serverSelector.item", c);
        inv.setItem(c.getInt("serverSelector.slot"), serverItem);
        
        hidePlayersItem = FracturedMain.loadItem("playerVisibility.itemWhenVisible", c);
        showPlayersItem = FracturedMain.loadItem("playerVisibility.itemWhenInvisible", c);
        hidePlayerItemSlot = c.getInt("playerVisibility.slot");
        
        launchPadMaterial = Material.getMaterial(c.getString("launchPads.pressurePlate"));
        yVel = c.getDouble("launchPads.yVelocity");
        xVelBase = c.getDouble("launchPads.xVelocityBaseValue");
        xVelMul = c.getDouble("launchPads.xVelocityMultiplier");
        
        doubleJumpYVel = c.getDouble("doubleJumpYVelocity");
        jumpCooldownTime = (int)(c.getDouble("doubleJumpCooldown") * 20);
        
        loginSoundEnabled = c.getBoolean("loginEffects.sound.enabled");
        loginSound = loginSoundEnabled ? Sound.valueOf(c.getString("loginEffects.sound.soundToPlay")) : null;
    
        loginTitleEnabled = c.getBoolean("loginEffects.title.enabled");
        if(loginTitleEnabled){
            loginTitle = cString(c.getString("loginEffects.title.titleText"));
            loginSubtitle = cString(c.getString("loginEffects.title.subtitleText"));
        }
        
        loginFireworkEnabled = c.getBoolean("loginEffects.firework.enabled");
        if(loginFireworkEnabled){
            World w = HubMain.inst.getServer().getWorlds().get(0);
            loginFirework = ((Firework)w.spawnEntity(new Location(w, 0, 0, 0),
                    EntityType.FIREWORK)).getFireworkMeta();
            loginFirework.setPower(c.getInt("loginEffects.firework.power"));
            List<Color> colours = new ArrayList<>();
            List<Color> fade = new ArrayList<>();
            c.getStringList("loginEffects.firework.colours").forEach(k -> {
                String[] u = k.split(",");
                colours.add(Color.fromRGB(Integer.parseInt(u[0]), Integer.parseInt(u[1]), Integer.parseInt(u[2]))); });
            c.getStringList("loginEffects.firework.fadeColours").forEach(k -> {
                String[] u = k.split(",");
                fade.add(Color.fromRGB(Integer.parseInt(u[0]), Integer.parseInt(u[1]), Integer.parseInt(u[2]))); });
            loginFirework.addEffect(FireworkEffect.builder().flicker(c.getBoolean("loginEffects.firework.flicker"))
                .trail(c.getBoolean("loginEffects.firework.trail")).withColor(colours).withFade(fade)
                .with(FireworkEffect.Type.valueOf(c.getString("loginEffects.firework.effect"))).build());
        }
        
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = scoreboard.registerNewObjective(cString(c.getString("scoreboard.title")), "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = c.getStringList("scoreboard.scoreboardLines");
        AtomicInteger i = new AtomicInteger(lines.size());
        lines.forEach(k -> {
            if(k.contains("{")){
                Team t = scoreboard.registerNewTeam(i.toString());
                t.addEntry("§" + i.toString());
//                t.setPrefix("view");
                String serverName = k.substring(k.indexOf('{') + 1, k.indexOf('}'));
                teamsToUpdate.put(t.getName(), t);
                serverCounts.put(serverName, " ");
                prefixes.put(t.getName(), cString(k.replace('{' + serverName + '}', "{")));
                teamServers.put(t.getName(), serverName);
                objective.getScore("§" + i.toString()).setScore(i.get());
            } else
                objective.getScore(cString(k)).setScore(i.get());
            i.getAndDecrement();
        });
        new BukkitRunnable(){
            public void run(){
                if(Bukkit.getServer().getOnlinePlayers().isEmpty())
                    return;
                serverCounts.keySet().forEach(k -> {
                    ByteArrayDataOutput o = ByteStreams.newDataOutput();
                    o.writeUTF("PlayerCount");
                    o.writeUTF(k);
                    Random r = new Random();
                    Player pl = new ArrayList<Player>(Bukkit.getServer().getOnlinePlayers()).get(r.nextInt(
                            Bukkit.getServer().getOnlinePlayers().size()));
                    pl.sendPluginMessage(p, "BungeeCord", o.toByteArray());
                });
                teamsToUpdate.keySet().forEach(k -> {
                    teamsToUpdate.get(k)
                            .setPrefix(prefixes.get(k).replace("{", serverCounts.get(teamServers.get(k))));
                });
            }
        }.runTaskTimer(p, 20, c.getInt("scoreboard.secondsBetweenUpdates") * 20);
        
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e){
        e.setCancelled(!e.getPlayer().hasPermission("hub.admin"));
        if(e.getAction().equals(Action.PHYSICAL) && e.getClickedBlock().getType().equals(launchPadMaterial)){
            Vector v = e.getPlayer().getVelocity();
            e.getPlayer().setVelocity(v.multiply(xVelMul).add(v.clone().normalize().multiply(xVelBase)).setY(yVel));
            return;
        }
        interactCalc(e.getPlayer(), e.getItem());
    }
    
    @EventHandler
    public void onIntEntity(PlayerInteractEntityEvent e){
        e.setCancelled(!e.getPlayer().hasPermission("hub.admin"));
        interactCalc(e.getPlayer(), e.getPlayer().getItemInHand());
    }
    
    private void interactCalc(Player p, ItemStack i){
        if(i == null)
            return;
        if(i.equals(serverItem))
            p.openInventory(FracturedMain.inst.serverInv);
        else if(i.equals(showPlayersItem))
            showPlayers(p);
        else if(i.equals(hidePlayersItem))
            hidePlayers(p);
        
    }
    
    private void hidePlayers(Player u){
        if(cooldown.contains(u.getUniqueId())){
            u.sendMessage(ChatColor.RED + "Please wait before using that item again!");
            return;
        }
        p.getServer().getOnlinePlayers().forEach(u::hidePlayer);
        cooldown.add(u.getUniqueId());
        new BukkitRunnable(){
            @Override
            public void run() {
                cooldown.remove(u.getUniqueId());
            }
        }.runTaskLater(p, 100);
        if(!HubMain.inst.hiddenToggled.contains(u.getUniqueId().toString()))
            HubMain.inst.hiddenToggled.add(u.getUniqueId().toString());
        hiddenToggledLoggedIn.add(u);
        u.getInventory().setItem(hidePlayerItemSlot, showPlayersItem);
        u.sendMessage(ChatColor.GREEN + "All players have been hidden!");
    }
    
    private void showPlayers(Player u){
        if(cooldown.contains(u.getUniqueId())){
            u.sendMessage(ChatColor.RED + "Please wait before using that item again!");
            return;
        }
        p.getServer().getOnlinePlayers().forEach(u::showPlayer);
        cooldown.add(u.getUniqueId());
        new BukkitRunnable(){
            @Override
            public void run() {
                cooldown.remove(u.getUniqueId());
            }
        }.runTaskLater(p, 100);
        HubMain.inst.hiddenToggled.remove(u.getUniqueId().toString());
        hiddenToggledLoggedIn.add(u);
        u.getInventory().setItem(hidePlayerItemSlot, hidePlayersItem);
        u.sendMessage(ChatColor.GREEN + "All players have been shown!");
    }
    
    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent e){
        new BukkitRunnable(){
            public void run(){
                e.getPlayer().getInventory().setContents(inv.getContents());
                e.getPlayer().getInventory().setItem(hidePlayerItemSlot, HubMain.inst.hiddenToggled.contains(
                        e.getPlayer().getUniqueId().toString()) ? showPlayersItem : hidePlayersItem);
                if(HubMain.inst.spawnLoc != null)
                    e.getPlayer().teleport(HubMain.inst.spawnLoc);
                hiddenToggledLoggedIn.forEach(k -> k.hidePlayer(e.getPlayer()));
                if(HubMain.inst.hiddenToggled.contains(e.getPlayer().getUniqueId().toString()))
                    hidePlayers(e.getPlayer());
    
                if(loginSoundEnabled)
                    e.getPlayer().playSound(e.getPlayer().getLocation(), loginSound, 1, 1);
    
                if(loginTitleEnabled)
                    //noinspection deprecation
                    e.getPlayer().sendTitle(loginTitle, loginSubtitle);
    
                if(loginFireworkEnabled)
                    ((Firework)e.getPlayer().getWorld().spawnEntity(e.getPlayer().getLocation(), EntityType.FIREWORK))
                            .setFireworkMeta(loginFirework);
    
                e.getPlayer().setAllowFlight(true);
                e.getPlayer().setScoreboard(scoreboard);
            }
        }.runTaskLater(p, 3);
        
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        hiddenToggledLoggedIn.remove(e.getPlayer());
    }
    
    @EventHandler
    public void onFlyToggle(PlayerToggleFlightEvent e){
        e.setCancelled(!e.getPlayer().hasPermission("hub.admin"));
        if(e.isCancelled() && !jumpCooldown.contains(e.getPlayer().getUniqueId())){
            e.getPlayer().setVelocity(e.getPlayer().getVelocity().setY(doubleJumpYVel));
            jumpCooldown.add(e.getPlayer().getUniqueId());
            new BukkitRunnable(){
                public void run(){
                    jumpCooldown.remove(e.getPlayer().getUniqueId());
                }
            }.runTaskLater(p, jumpCooldownTime);
        }
    }
    
    @Override
    public void onPluginMessageReceived(String s, Player player, byte[] bytes) {
        if(!s.equals("BungeeCord"))
            return;
        ByteArrayDataInput i = ByteStreams.newDataInput(bytes);
        String sub = i.readUTF();
        if(sub.equals("PlayerCount")){
            serverCounts.replace(i.readUTF(), Integer.toString(i.readInt()));
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        e.setCancelled(!e.getPlayer().hasPermission("hub.admin"));
    }
    
    @EventHandler
    public void onWeatherChange(WeatherChangeEvent e){
        e.setCancelled(true);
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e){
        e.setCancelled(!e.getPlayer().hasPermission("hub.admin"));
    }
    
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent e){
        e.setCancelled(true);
    }
    
    @EventHandler
    public void onPlayerHunger(FoodLevelChangeEvent e){
        e.setCancelled(true);
    }
    
    @EventHandler
    public void onItemThrow(PlayerDropItemEvent e){
        e.setCancelled(true);
    }
    
    private String cString(String s){
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
