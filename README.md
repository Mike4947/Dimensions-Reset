# 🌌 DimensionsReset

[](https://www.google.com/search?q=https://github.com/Mike4947/DimensionsReset/actions)
[](https://www.google.com/search?q=https://github.com/Mike4947/DimensionsReset/releases)
[](https://www.google.com/search?q=./LICENSE)

A powerful and feature-rich PaperMC plugin designed to safely reset The End dimension on a schedule or with a single command. Packed with safety features and engaging server-wide announcements to make dimension resets a seamless and anticipated event.

This plugin is built for **Minecraft 1.21+**.

## ✨ Features

  * **🌀 Safe Dimension Resets:** Reliably resets The End by unloading, deleting, and regenerating the dimension files.
  * **⏰ Flexible Scheduling:** Reset The End immediately (`now`) or schedule it for a future time (`1h30m`).
  * **✅ Confirmation System:** A mandatory confirmation step for immediate resets prevents catastrophic accidents.
  * **📢 Live Countdown:** Automatically broadcasts server-wide countdown messages at configurable intervals.
  * **🔊 Sound Effects:** Plays configurable sounds for all players at key moments to enhance immersion.
  * **❓ Status Command:** Check the time remaining on a scheduled reset at any time.
  * **🔄 Configuration Reload:** Reload the plugin's configuration on the fly without a server restart.

## 🛠️ Installation

1.  Download the latest release from the [Releases](https://www.google.com/search?q=https://github.com/Mike4947/DimensionsReset/releases) page.
2.  Place the `DimensionsReset-vX.X.jar` file into your server's `/plugins` directory.
3.  Restart or start your server. The default `config.yml` will be generated.

## 📋 Commands & Permissions

The main command is `/dr` (aliases: `/dims`, `/dreset`).

| Command                               | Description                                                  | Permission                  |
| ------------------------------------- | ------------------------------------------------------------ | --------------------------- |
| `/dr reset the_end <time\|now>`       | Schedules a reset for The End or initiates an immediate reset. | `dimensionsreset.admin`     |
| `/dr cancel`                          | Cancels any currently scheduled reset.                       | `dimensionsreset.admin`     |
| `/dr confirm`                         | Confirms an immediate reset after using the `now` argument.  | `dimensionsreset.admin`     |
| `/dr status`                          | Checks the time remaining until the next scheduled reset.    | `dimensionsreset.admin`     |
| `/dr reload`                          | Reloads the `config.yml` file from disk.                     | `dimensionsreset.reload`    |

By default, all permissions are granted to server operators (OPs).

## ⚙️ Configuration

The `config.yml` file is heavily commented and allows you to customize nearly every aspect of the plugin.

### Confirmation

Configure the messages for the `/dr confirm` safety system.

### Messages

Customize all server-wide broadcasts and command responses. The `%time%` placeholder will be replaced with a human-readable time (e.g., "1h 30m 0s").

### Countdown Times

The `countdown_broadcast_times` section lets you define exactly when countdown messages are sent. The times are in **seconds**.

```yaml
countdown_broadcast_times:
  - 3600  # 1 hour
  - 600   # 10 minutes
  - 60    # 1 minute
  - 10    # 10 seconds
```

### Sounds

Customize the sound effects played during events.

  * You must use the official Minecraft sound keys.
  * A full list can be found on the [Minecraft Wiki Sounds.json page](https://minecraft.wiki/w/Sounds.json).

## 👨‍💻 Building from Source

If you wish to build the plugin yourself:

1.  Clone this repository: `git clone https://github.com/Mike4947/DimensionsReset.git`
2.  Navigate into the directory: `cd DimensionsReset`
3.  Build with Maven: `mvn clean package`
4.  The finished JAR will be in the `/target` directory.

## ❤️ Contributing

Contributions are welcome\! If you find a bug or have a feature request, please open an issue on the [GitHub Issues](https://www.google.com/search?q=https://github.com/Mike4947/DimensionsReset/issues) page.

## 📜 License

This project is licensed under the MIT License. See the [LICENSE](https://www.google.com/search?q=./LICENSE) file for details.

-----

*This README was generated with help from Google AI on June 10, 2025.*
