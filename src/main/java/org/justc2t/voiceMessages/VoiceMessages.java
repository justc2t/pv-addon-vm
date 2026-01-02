package org.justc2t.voiceMessages;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.justc2t.voiceMessages.addon.VoiceMessagesAddon;
import org.justc2t.voiceMessages.audio.RecordingManager;
import org.justc2t.voiceMessages.util.ConfigManager;
import org.justc2t.voiceMessages.util.SchedulerAdapter;
import su.plo.voice.api.server.PlasmoVoiceServer;

import static org.justc2t.voiceMessages.util.ConfigManager.getFormatedMessage;

public final class VoiceMessages extends JavaPlugin implements CommandExecutor {
    private RecordingManager recordingManager;
    private VoiceMessagesAddon addon;
    private SchedulerAdapter scheduler;
    private ConfigManager configManager;
    private boolean discordsrv = false;
    @Override
    public void onLoad() {
        addon = new VoiceMessagesAddon(this);
        PlasmoVoiceServer.getAddonsLoader().load(addon);
    }

@Override
public void onEnable() {
    this.scheduler = new SchedulerAdapter(this);
    this.recordingManager = new RecordingManager(this, scheduler);
    addon.setRecordingManager(recordingManager);
    this.recordingManager.setAddon(addon);
    this.configManager = new ConfigManager(this);
    discordsrv = getConfig().getBoolean("discord-integration", false);
    getCommand("recording").setExecutor(this);
    getCommand("voicemessage").setExecutor(this); // NEW
    getLogger().info("VoiceMessages enabled");
}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getFormatedMessage("messages.only-players"));
            return true;
        }
        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("voicemessage")) {
            if (args.length != 1 ) {
                return false;
            }
            Player target = getServer().getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(getFormatedMessage("messages.player-not-found"));
                return true;
            }
                if(args[0].equals(player.getName())){
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red> Вы не можете записать голосовое сообщение самому себе!"));
                    return true;
                }

                recordingManager.startPrivateRecording(player, target);
                return true;

        }

        if (command.getName().equalsIgnoreCase("recording")) {
            if (args.length != 1) {
                return false;
            }
            String id = args[0];
            recordingManager.togglePlayback(id, player);
            return true;
        }

        return false;
    }

    @Override
    public void onDisable() {
        if (recordingManager != null) recordingManager.shutdown();
    }

    public boolean isDiscordsrv() { return discordsrv; }
}