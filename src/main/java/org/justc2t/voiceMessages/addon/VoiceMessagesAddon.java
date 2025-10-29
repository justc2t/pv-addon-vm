package org.justc2t.voiceMessages.addon;

import org.bukkit.entity.Player;
import org.justc2t.voiceMessages.VoiceMessages;
import org.justc2t.voiceMessages.audio.RecordingManager;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.Optional;
@Addon(
        id = "pv-addon-voicemessages",
        name = "VoiceMessages",
        version = "1.0.0",
        authors = {"Just_c2t"}
)
public final class VoiceMessagesAddon implements AddonInitializer {

    private final VoiceMessages plugin;
    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;
    private RecordingManager recordingManager;
    private ServerActivation activation;
    private ServerSourceLine sourceLine;
    public VoiceMessagesAddon(VoiceMessages plugin) {
        this.plugin = plugin;
    }
    public void setRecordingManager(RecordingManager manager) {
        this.recordingManager = manager;
    }
    @Override
    public void onAddonInitialize() {
        String slName = plugin.getConfig().getString("source-line.name", "voicemessages");
        int slWeight = plugin.getConfig().getInt("source-line.weight", 50);
        double defVol = plugin.getConfig().getDouble("source-line.default-volume", 1.0);
        this.sourceLine = voiceServer.getSourceLineManager().createBuilder(this,
                slName,
                plugin.getConfig().getString("source-line.show"),
                "plasmovoice:textures/icons/speaker_priority.png",
                slWeight
        ).setDefaultVolume((float) defVol).withPlayers(true).build();

        String aName = plugin.getConfig().getString("activation.name",
                "voicemessages");
        String aPerm = plugin.getConfig().getString("activation.permission",
                "voicemessages.activation");
        int aWeight = plugin.getConfig().getInt("activation.weight", 50);
        String icon = plugin.getConfig().getString("activation.icon", "");
        ServerActivation.Builder builder;
        if (icon != null && !icon.isEmpty()) {
            builder = voiceServer.getActivationManager().createBuilder(this,
                    aName,
                    plugin.getConfig().getString("activation.show"),
                    icon,
                    aPerm,
                    aWeight);
        } else {
            builder = voiceServer.getActivationManager().createBuilder(this,
                    aName,
                    plugin.getConfig().getString("activation.show"),
                    "plasmovoice:textures/icons/microphone_priority.png",
                    aPerm, aWeight);
        }
        this.activation = builder.build();
        activation.onPlayerActivationStart(player -> {
            if (recordingManager == null) return;
            Optional<Player> bp = resolveBukkit(player);
            bp.ifPresent(recordingManager::beginRecording);
        });
        activation.onPlayerActivation((player, packet) -> {
            if (recordingManager == null) return
                    ServerActivation.Result.IGNORED;
            recordingManager.ingestFrame(player, packet.getData());
            return ServerActivation.Result.HANDLED;
        });
        activation.onPlayerActivationEnd((player, packet) -> {
            if (recordingManager == null) return
                    ServerActivation.Result.IGNORED;
            Optional<Player> bp = resolveBukkit(player);
            bp.ifPresent(p -> {
                recordingManager.endRecording(p);
            });
            return ServerActivation.Result.HANDLED;
        });
    }
    public ServerSourceLine getSourceLine() {
        return sourceLine;
    }
    public PlasmoVoiceServer getVoiceServer() {
        return voiceServer;
    }
    private Optional<Player> resolveBukkit(VoicePlayer voicePlayer) {
        return
                Optional.ofNullable(plugin.getServer().getPlayer(voicePlayer.getInstance().getUuid()));
    }
}

