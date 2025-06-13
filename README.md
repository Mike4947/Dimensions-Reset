# 🌌 DimensionsReset

[![Build Status](https://img.shields.io/github/actions/workflow/status/Mike4947/DimensionsReset/build.yml?branch=main&style=for-the-badge)](https://github.com/Mike4947/DimensionsReset/actions)
[![Latest Stable Release](https://img.shields.io/github/v/release/Mike4947/DimensionsReset?label=stable&style=for-the-badge)](https://github.com/Mike4947/DimensionsReset/releases)
[![License](https://img.shields.io/github/license/Mike4947/DimensionsReset?style=for-the-badge)](./LICENSE)

> **ℹ️ NOTE FOR DEVELOPERS**
>
> You are currently viewing the **`main`** branch, which contains the source code for the latest **stable release**.
>
> All new features and future updates are developed on the **[`master` branch](https://github.com/Mike4947/Dimensions-Reset/tree/master)**. Please submit all pull requests and contributions to that branch.

**DimensionsReset** is a professional-grade server utility designed to handle the complex task of resetting dimensions. It provides a powerful, stable, and user-friendly solution for managing your server's worlds, from fully automated recurring resets to safe, manual commands.

This plugin is built for **Minecraft 1.21+** and modern Paper servers.

## ✨ Core Features

* **🌀 Multi-Dimension Resets:** Reliably reset both `the_end` and `the_nether` with simple commands.
* **🗓️ Full Automation:** A powerful "set it and forget it" scheduler allows you to configure recurring resets based on an interval or a specific day and time.
* **🖥️ GUI Control Panel:** An intuitive and easy-to-use `/dr gui` command opens a menu to manage all major plugin functions with simple clicks.
* **🛡️ Stable Reset Process:** Dimension resets are handled safely to ensure portals and game mechanics work perfectly after completion without requiring a server restart.
* **🔭 Spectator Preview Mode:** Use `/dr preview before <dimension>` to safely inspect the current state of a world in spectator mode.
* **⌨️ Smart Tab Completion:** A professional command interface that suggests commands and arguments as you type.
* **📢 Engaging Announcements:** Keep your players informed with configurable countdowns, server-wide messages, and sound effects.
* **✅ Confirmation System:** A mandatory `/dr confirm` step for instant manual resets prevents accidents.


## 🛠️ Installation

1.  Download the latest stable release from the [Releases](https://github.com/Mike4947/Dimensions-Reset/releases/latest) page.
2.  Place the `DimensionsReset-vX.X.X.jar` file into your server's `/plugins` directory.
3.  Start your server. The plugin will generate a `plugins/DimensionsReset/` folder containing the `config.yml` and `data.yml` files.
4.  **Important:** For the automated restart feature to work, you must be using a startup script (like `start.sh` or `start.bat`) with a loop and have it configured in your `spigot.yml` file.

## 📋 Commands & Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/dr gui` | Opens the main control panel GUI. | `dimensionsreset.admin` |
| `/dr reset <dim\|all> <time\|now>` | Schedules or initiates a reset for a dimension. | `dimensionsreset.admin` |
| `/dr cancel <dim\|all>` | Cancels a scheduled reset for a dimension. | `dimensionsreset.admin` |
| `/dr status <dim\|all>` | Checks the time remaining on a scheduled reset. | `dimensionsreset.admin` |
| `/dr confirm` | Confirms an instant manual reset. | `dimensionsreset.admin` |
| `/dr preview <dim\|seed> [before\|exit]`| Enters preview mode or checks the world seed. | `dimensionsreset.preview` |
| `/dr reload` | Reloads the `config.yml` file from disk. | `dimensionsreset.reload` |

## ⚙️ Configuration (`config.yml`)

The plugin is highly configurable. The main sections are:

  * **`automated_resets`:** The heart of the plugin. Define multiple recurring schedules here using either an `INTERVAL` (e.g., `"7d"`) or a specific `DAY_AND_TIME` (e.g., `"FRIDAY-20:00"`).
  * **`restart_manager`:** Control the automated server restart that occurs after a reset. You can configure the delay and the broadcast message.
  * **`dimension_reset_messages`:** Customize all messages related to standard End/Nether resets.
  * **`preview`:** Customize messages for the preview commands.
  * **`countdown_broadcast_times` & `sounds`:** Configure the server-wide announcements.

## 🚀 Future Roadmap

DimensionsReset is under active development\! Based on community feedback, here are some of the exciting features planned for future updates:

  * **🗳️ Player Voting System:** A `/dr vote` command that will allow the community to initiate a vote to reset a dimension.
  * **📊 Detailed Reset Logs & Analytics:** An in-game `/dr history` command to view statistics about past resets, such as when they occurred and how long each world cycle lasted.
  * **💬 Interactive Chat Buttons:** Upgrading announcements to use modern, clickable chat components. For example, a status message could include a `[Cancel]` button that an admin can click directly in chat.

Have another idea? Let us know on the [GitHub Issues](https://github.com/Mike4947/Dimensions-Reset/issues) page\!

## 📜 License

This project is licensed under the MIT License. See the [LICENSE](https://github.com/Mike4947/Dimensions-Reset/blob/main/LICENSE) file for details.

-----

*README updated on June 12*
