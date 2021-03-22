import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommands implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String s, String[] strings) {
        if(!(sender instanceof Player))
            return false;
        Player p = (Player)sender;
        if(!p.hasPermission("hub.admin")){
            p.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
            return true;
        }
        if(cmd.getName().equals("setspawn")){
            HubMain.inst.spawnLoc = p.getLocation();
            p.sendMessage(ChatColor.GREEN + "Spawn successfully set!");
        } else{
            HubMain.inst.spawnLoc = null;
            p.sendMessage(ChatColor.GREEN + "Spawn successfully removed!");
        }
        return true;
    }
}
