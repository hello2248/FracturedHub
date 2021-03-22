import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HubMain extends JavaPlugin {
    
    static HubMain inst;
    
    List<String> hiddenToggled;
    Location spawnLoc;
    File dataFile;
    YamlConfiguration data;
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void onEnable(){//TODO test playerVisibility, spawn commands, launchpads, login title, login sound, login firework, and double jump
        saveDefaultConfig();
        inst = this;
        
        dataFile = new File(getDataFolder(), "data.yml");
        if(!dataFile.exists()){
            try{ dataFile.createNewFile(); }catch(IOException e){ e.printStackTrace();return; }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        hiddenToggled = data.getStringList("hiddenToggled");
        if(hiddenToggled == null)
            hiddenToggled = new ArrayList<>();
        
        if(data.contains("spawnLoc") && data.get("spawnLoc") != null)
            spawnLoc = (Location)data.get("spawnLoc");
    
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        
        getServer().getPluginManager().registerEvents(new HubListeners(this), this);
        
        SpawnCommands s = new SpawnCommands();
        getCommand("setspawn").setExecutor(s);
        getCommand("removespawn").setExecutor(s);
    }
    
    public void onDisable(){
        data.set("hiddenToggled", hiddenToggled);
        data.set("spawnLoc", spawnLoc);
        try{
            data.save(dataFile);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    
}
