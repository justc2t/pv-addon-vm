package org.justc2t.voiceMessages.audio;

import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.justc2t.voiceMessages.VoiceMessages;
import org.justc2t.voiceMessages.addon.VoiceMessagesAddon;
import org.justc2t.voiceMessages.util.SchedulerAdapter;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.provider.ArrayAudioFrameProvider;
import su.plo.voice.api.server.audio.source.AudioSender;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.player.VoicePlayer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.justc2t.voiceMessages.util.ConfigManager.*;

public final class RecordingManager {

    private final VoiceMessages plugin;
    private final SchedulerAdapter scheduler;
    private final Path recordingsDir;
    private final Map<UUID, Recording> activeByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Recording> byId = new ConcurrentHashMap<>();
    private VoiceMessagesAddon addon;
    private final Map<UUID, UUID> privateSessions = new ConcurrentHashMap<>();
    private final Map<String, Recording> recordings = new ConcurrentHashMap<>();




    public RecordingManager(VoiceMessages plugin, SchedulerAdapter scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.recordingsDir = plugin.getDataFolder().toPath().resolve("recordings");
        try {
            Files.createDirectories(recordingsDir);
        } catch (IOException ignored) {}
    }

    public void setAddon(VoiceMessagesAddon addon) {
        this.addon = addon;
    }

    public void beginRecording(Player bukkitPlayer) {
        if (activeByPlayer.containsKey(bukkitPlayer.getUniqueId())) return;

        Path wav = recordingsDir.resolve(UUID.randomUUID().toString().substring(0, 8) + ".wav");
        Recording rec = new Recording(wav, addon.getVoiceServer());
        activeByPlayer.put(bukkitPlayer.getUniqueId(), rec);

        int maxSeconds = plugin.getConfig().getInt("max-seconds", 30);
        scheduler.runLater(() -> {
            if (activeByPlayer.get(bukkitPlayer.getUniqueId()) == rec) {
                endRecording(bukkitPlayer);
            }
        }, maxSeconds * 20L);
    }

    public void ingestFrame(VoicePlayer player, byte[] encryptedFrame) {
        Player bp = Bukkit.getPlayer(player.getInstance().getUuid());
        if (bp == null) return;

        Recording rec = activeByPlayer.get(bp.getUniqueId());
        if (rec == null) return;

        PlasmoVoiceServer vs = addon.getVoiceServer();
        Encryption enc = vs.getDefaultEncryption();
        try {
            byte[] decrypted = enc.decrypt(encryptedFrame);
            rec.addEncodedFrame(decrypted);
        } catch (Exception e) {
            plugin.getLogger().warning("Error in decrypting a message: " + e.getMessage());
        }
    }
public void endRecording(Player bukkitPlayer) {
    Recording rec = activeByPlayer.remove(bukkitPlayer.getUniqueId());
    if (rec == null) return;

    try {
        rec.finalizeToWav();
    } catch (IOException e) {
        plugin.getLogger().severe("Unable to write file: " + e.getMessage());
        return;
    }

    byId.put(rec.getId(), rec);

    long minutes = plugin.getConfig().getLong("retention-minutes", 5);
    scheduler.runLater(() -> deleteRecording(rec.getId()),minutes * 20 * 60);

      UUID targetUUID = privateSessions.remove(bukkitPlayer.getUniqueId());
    if (targetUUID != null) {
        Player recipient = Bukkit.getPlayer(targetUUID);
        if (recipient != null && recipient.isOnline()) {

            try {
                short[] samples = rec.loadPcmSamples();
                String waveform = generateWaveformPreview(samples, 32);
                int maxSeconds = plugin.getConfig().getInt("max-seconds", 30);
                int durationSec = rec.getDurationSeconds();
                String durationStr = formatTime(durationSec);
                String maxStr = formatTime(maxSeconds);
                bukkitPlayer.sendMessage(getMessageWithPlaceholer("messages.private-message-sent", Map.of(
                        "sender", bukkitPlayer.getName(),
                        "player", recipient.getName(),
                        "waveform", waveform,
                        "time", durationStr + "/" + maxStr)).clickEvent(ClickEvent.runCommand("/recording " + rec.getId())));
                recipient.sendMessage(getMessageWithPlaceholer("messages.private-message-received", Map.of(
                        "sender", bukkitPlayer.getName(),
                        "player", recipient.getName(),
                        "waveform", waveform,
                        "time", durationStr + "/" + maxStr)).clickEvent(ClickEvent.runCommand("/recording " + rec.getId())));            } catch (IOException e) {
                plugin.getLogger().warning("Unable to generate waveform preview: " + e.getMessage());
            }

        } else {
            bukkitPlayer.sendMessage(getFormatedMessage("messages.went-offline"));
        }
        return;
    }

    try {
        short[] samples = rec.loadPcmSamples();
        String waveform = generateWaveformPreview(samples, 32);

        int maxSeconds = plugin.getConfig().getInt("max-seconds", 30);
        int durationSec = rec.getDurationSeconds();
        String durationStr = formatTime(durationSec);
        String maxStr = formatTime(maxSeconds);


        Component message = getMessageWithPlaceholer("messages.send-vc", Map.of(
                "player", bukkitPlayer.getName(),
                "id", rec.getId(),
                "waveform", waveform,
                "time", durationStr + "/" + maxStr))
                .clickEvent(ClickEvent.runCommand("/recording " + rec.getId()));
        Bukkit.broadcast(message);
        if(plugin.isDiscordsrv()){
            sendWebhook(new URL("https://mc-heads.net/avatar/" + bukkitPlayer.getName()), bukkitPlayer.getName(), rec.getWavPath().toFile());
        }
    } catch (IOException e) {
        plugin.getLogger().warning("Unable to generate waveform preview: " + e.getMessage());
    }

}
    public boolean togglePlayback(String id, Player requester) {
        Recording rec = byId.get(id);
        if (rec == null || !rec.isFinished()) {
            return false;
        }

        if (!rec.isPlaying()) {
            playFor(requester, rec);
            requester.sendActionBar(getFormatedMessage("messages.start-listening"));
        } else {
            rec.setPlaying(false);
            requester.sendActionBar(getFormatedMessage("messages.already-listening"));
        }
        return true;
    }

    private void playFor(Player target, Recording rec) {
        PlasmoVoiceServer vs = addon.getVoiceServer();
        VoicePlayer vp = vs.getPlayerManager().getPlayerById(target.getUniqueId()).orElse(null);
        if (vp == null) {
            target.sendMessage(getFormatedMessage("messages.no-plasmovoice"));
            return;
        }

        ServerSourceLine line = addon.getSourceLine();

        ServerDirectSource source = line.createDirectSource(vp, false);
        ArrayAudioFrameProvider provider = new ArrayAudioFrameProvider(vs, false);

        try {
            short[] samples = rec.loadPcmSamples();
            provider.addSamples(samples);
        } catch (IOException e) {
            plugin.getLogger().severe("Unable to read file: " + e.getMessage());
            source.remove();
            return;
        }

        AudioSender sender = source.createAudioSender(provider);
        rec.setPlaying(true);

        sender.start();
        sender.onStop(() -> {
            rec.setPlaying(false);
            try { provider.close(); } catch (Exception ignored) {}
            source.remove();
        });
    }

    public void deleteRecording(String id) {
        Recording rec = byId.remove(id);
        if (rec == null) return;
        try {
            Files.deleteIfExists(rec.getWavPath());
            Bukkit.getLogger().info("Deleted voice file: " + rec.getId());
        } catch (IOException ignored) {}
    }

    public void shutdown() {
        for (Recording r : byId.values()) {
            try { Files.deleteIfExists(r.getWavPath()); } catch (IOException ignored) {}
        }
        byId.clear();
        activeByPlayer.clear();
    }

    private String generateWaveformPreview(short[] samples, int bars) {
        if (samples.length == 0) return "";

        int chunkSize = Math.max(1, samples.length / bars);
        double[] rmsValues = new double[bars];
        double max = 1.0;

        for (int i = 0; i < bars; i++) {
            int start = i * chunkSize;
            int end = Math.min(samples.length, (i + 1) * chunkSize);

            long sumSq = 0;
            for (int j = start; j < end; j++) {
                sumSq += (long) samples[j] * samples[j];
            }

            double rms = Math.sqrt(sumSq / (double) Math.max(1, end - start));
            rmsValues[i] = rms;
            if (rms > max) max = rms;
        }

        List<Character> levels = new ArrayList<>();
        for(String str : plugin.getConfig().getStringList("messages.waveform-levels")){
            levels.add(str.charAt(0));
        }

        StringBuilder sb = new StringBuilder();

        for (double rms : rmsValues) {
            double norm = rms / max;
            int level = (int) Math.round(norm * (levels.size() - 1));
            sb.append(levels.get(level));
        }

        return sb.toString();
    }
    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    public void startPrivateRecording(Player sender, Player recipient) {
        privateSessions.put(sender.getUniqueId(), recipient.getUniqueId());

        sender.sendMessage(getMessageWithPlaceholer("messages.private-message-window-open", Map.of(
                "player", recipient.getName()
        )));

        int maxSeconds = plugin.getConfig().getInt("max-seconds", 60);
        scheduler.runLater(() -> {
            if (privateSessions.containsKey(sender.getUniqueId())) {
                privateSessions.remove(sender.getUniqueId());
                sender.sendMessage(getFormatedMessage("messages.private-message-window-expired"));
            }
        }, maxSeconds * 20L);
    }



    public void sendWebhook(URL icon, String name, File file) {
        final String WEBHOOK_URL = getString("discord-webhook.url");
        String boundary = "----DiscordBoundary" + System.currentTimeMillis();
        String lineFeed = "\r\n";

        try {
            URL url = new URL(WEBHOOK_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream output = connection.getOutputStream();
                 DataOutputStream writer = new DataOutputStream(output)) {

                // 1. JSON payload (username and avatar)
                String payloadJson = String.format(
                        "{\"username\":\"%s\",\"avatar_url\":\"%s\",\"flags\":8192}",
                        name, icon.toString()
                );

                writer.writeBytes("--" + boundary + lineFeed);
                writer.writeBytes("Content-Disposition: form-data; name=\"payload_json\"" + lineFeed);
                writer.writeBytes("Content-Type: application/json" + lineFeed + lineFeed);
                writer.writeBytes(payloadJson + lineFeed);

                // 2. Attach the file (.wav)
                String fileName = file.getName();
                String mimeType = Files.probeContentType(file.toPath());
                if (mimeType == null) mimeType = "application/octet-stream";

                writer.writeBytes("--" + boundary + lineFeed);
                writer.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + lineFeed);
                writer.writeBytes("Content-Type: " + mimeType + lineFeed + lineFeed);

                Files.copy(file.toPath(), writer);
                writer.writeBytes(lineFeed);

                // 3. End boundary
                writer.writeBytes("--" + boundary + "--" + lineFeed);
                writer.flush();
            }

            // Get the response
            int responseCode = connection.getResponseCode();
            if (responseCode == 200 || responseCode == 204) {
                System.out.println("Voice message sent successfully!");
            } else {
                System.err.println("Failed to send voice message. Response code: " + responseCode);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                    br.lines().forEach(System.err::println);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
