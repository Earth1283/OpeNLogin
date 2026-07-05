/*
 * The MIT License (MIT)
 *
 * Copyright © 2020 - 2026 - OpenLogin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nickuc.openlogin.bukkit.command.executors;

import com.nickuc.openlogin.bukkit.OpenLoginBukkit;
import com.nickuc.openlogin.bukkit.command.BukkitAbstractCommand;
import com.nickuc.openlogin.bukkit.ui.chat.ActionbarAPI;
import com.nickuc.openlogin.common.http.HttpClient;
import com.nickuc.openlogin.common.settings.Messages;
import com.nickuc.openlogin.common.util.FileUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class OpenLoginCommand extends BukkitAbstractCommand {

    private static final String REPOSITORY = "Earth1283/OpeNLogin";

    private final AtomicBoolean downloadLock = new AtomicBoolean();

    public OpenLoginCommand(OpenLoginBukkit plugin) {
        super(plugin, "openlogin");
    }

    protected void perform(CommandSender sender, String lb, String[] args) {
        if (args.length != 0) {
            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case "reload":
                case "rl":
                case "r": {
                    if (sender instanceof Player && !plugin.getLoginManagement().isAuthenticated(sender.getName())) {
                        return;
                    }

                    plugin.reloadConfig();
                    plugin.setupSettings();
                    sender.sendMessage(Messages.PLUGIN_RELOAD_MESSAGE.asString());
                    return;
                }

                case "update": {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(Messages.PLAYER_COMMAND_USAGE.asString());
                        return;
                    }

                    Player player = (Player) sender;
                    String name = player.getName();
                    if (!plugin.getLoginManagement().isAuthenticated(name)) {
                        return;
                    }

                    if (!plugin.isUpdateAvailable()) {
                        sender.sendMessage("§cYou are already using the latest version.");
                        return;
                    }

                    if (downloadLock.getAndSet(true)) {
                        sender.sendMessage("§cDownload in progress...");
                    } else if (!update(player)) {
                        downloadLock.set(false);
                    }
                    return;
                }
            }
        }

        sender.sendMessage("");
        sender.sendMessage(" §eThis server is running §fOpeNLogin v " + plugin.getDescription().getVersion() + ".");
        sender.sendMessage("");
        sender.sendMessage(" §7GitHub: §fhttps://github.com/" + REPOSITORY);
        sender.sendMessage("");
    }

    private boolean update(Player player) {
        File output = new File(plugin.getDataFolder().getParentFile(), "OpeNLogin-" + plugin.getLatestVersion() + ".jar");
        String url = "https://github.com/" + REPOSITORY + "/releases/download/" + plugin.getLatestVersion() + "/OpenLogin.jar";
        return downloadActionbar(player, url, output);
    }

    private boolean downloadActionbar(Player player, String url, File output) {
        player.sendMessage("§eDownloading...");
        ActionbarAPI.getApi().send(player, "§eConnecting...");

        final int barsCount = 40;
        final HttpClient.AsyncDownloadResult downloadResult;
        try {
            if ((downloadResult = HttpClient.DEFAULT.download(url, output)) == null) {
                ActionbarAPI.getApi().send(player, "§cDownload failed!");
                player.sendMessage("§cDownload failed, could not delete old file.");
                return false;
            }
        } catch (IOException exception) {
            exception.printStackTrace();
            return false;
        }

        AtomicBoolean downloadFinished = new AtomicBoolean();
        AtomicBoolean downloadSuccessful = new AtomicBoolean();
        plugin.getFoliaLib().runAtEntityTimer(player, task -> {
            if (downloadFinished.get()) {
                if (downloadSuccessful.get()) {
                    ActionbarAPI.getApi().send(player, "§aDownload finished! §7(§a" + repeatString("|", barsCount) + "§7)");
                    player.sendMessage("§aDownload finished. Please restart your server.");
                } else {
                    ActionbarAPI.getApi().send(player, "§cDownload failed! §7(§a" + repeatString("|", barsCount) + "§7)");
                    player.sendMessage("§cDownload failed, please try again.");
                }
                task.cancel();
                return;
            }
            int bars = (int) (barsCount * (downloadResult.downloaded() / downloadResult.contentLength()));
            String progressBar = "§a" + repeatString("|", bars) + "§c" + repeatString("|", barsCount - bars);
            ActionbarAPI.getApi().send(player, "§eDownloading... §7(" + progressBar + "§7)");
        }, 0, 200, TimeUnit.MILLISECONDS);

        try {
            downloadSuccessful.set(downloadResult.startDownload());
            if (downloadSuccessful.get()) {
                File pluginFile = FileUtils.getSelfJarFile();
                pluginFile.deleteOnExit();
            }
        } catch (IOException e) {
            downloadLock.set(false);
            e.printStackTrace();
            String msg = "§cFailed to download new version. Update manually at: https://github.com/" + REPOSITORY + "/releases";
            plugin.sendMessage(msg);
            player.sendMessage(msg);
        } finally {
            downloadFinished.set(true);
        }
        return downloadSuccessful.get();
    }

    private String repeatString(String str, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(str);
        }
        return builder.toString();
    }
}
