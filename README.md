VoiceMessages

This is an alternative server-side plugin to the existing VoiceMessages mod available on Simple Voice Chat.

This plugin requires [PlasmoVoice](https://modrinth.com/plugin/plasmo-voice).

## How the plugin works:

# Global messages

1. A new activation & source line is registered using the PlasmoVoiceAPI.
2. When a player activates the activation, it starts a recording and in the end the recording is stored in a seperate folder with a custom ID
3. A message is sent to chat(Using [MiniMessage formatting](https://docs.papermc.io/adventure/minimessage/format/)) with a click event.
3.1 IF Discord Integration is enabled, it will send the voice message simillar to a Discord voice message.
4. When a player clicks the message, he starts to listen to the recording.
5. After the retention time has passed(60 sec. by default) the recording file is deleted to reduce storage usage.

# Private messages

1. A player runs the command /vm [OnlinePlayer] and starts to record his message.
2. When the message is recorded, its sent to the receiver and stored in the recordings folder.
3. The receiver and the sender can listen to the recording.
4. After the retention time the recording is deleted to reduce storage usage.

The plugin is currently available for:
Spigot, Paper, Folia, CanvasMC softwares with a SchedulerAdapter

Thanks for viewing the plugin!
