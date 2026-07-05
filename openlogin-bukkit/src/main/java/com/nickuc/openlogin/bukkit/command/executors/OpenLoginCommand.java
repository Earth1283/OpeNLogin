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
import com.nickuc.openlogin.bukkit.api.events.AsyncAuthenticateEvent;
import com.nickuc.openlogin.bukkit.command.BukkitAbstractCommand;
import com.nickuc.openlogin.bukkit.task.LoginQueue;
import com.nickuc.openlogin.bukkit.ui.chat.ActionbarAPI;
import com.nickuc.openlogin.bukkit.ui.title.TitleAPI;
import com.nickuc.openlogin.common.http.HttpClient;
import com.nickuc.openlogin.common.manager.AccountManagement;
import com.nickuc.openlogin.common.manager.LoginManagement;
import com.nickuc.openlogin.common.model.Account;
import com.nickuc.openlogin.common.security.hashing.BCrypt;
import com.nickuc.openlogin.common.settings.Messages;
import com.nickuc.openlogin.common.settings.Settings;
import com.nickuc.openlogin.common.util.FileUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.StringJoiner;
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

                case "admin": {
                    if (!sender.hasPermission("openlogin.admin")) {
                        sender.sendMessage(Messages.INSUFFICIENT_PERMISSIONS.asString());
                        return;
                    }
                    performAdmin(sender, lb, args);
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

    private void performAdmin(CommandSender sender, String lb, String[] args) {
        if (args.length < 2) {
            sendAdminUsage(sender, lb);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "sessions": {
                sendSessions(sender);
                return;
            }

            case "forcelogin": {
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /" + lb + " admin forcelogin <player>");
                    return;
                }
                forceLogin(sender, args[2]);
                return;
            }

            case "unregister": {
                if (args.length != 3) {
                    sender.sendMessage("§cUsage: /" + lb + " admin unregister <player>");
                    return;
                }
                forceUnregister(sender, args[2]);
                return;
            }

            case "changepassword": {
                if (args.length != 4) {
                    sender.sendMessage("§cUsage: /" + lb + " admin changepassword <player> <newpassword>");
                    return;
                }
                forceChangePassword(sender, args[2], args[3]);
                return;
            }

            default: {
                sendAdminUsage(sender, lb);
            }
        }
    }

    private void sendAdminUsage(CommandSender sender, String lb) {
        sender.sendMessage("§cUsage: /" + lb + " admin <forcelogin|unregister|changepassword|sessions> [player] [args]");
    }

    private void sendSessions(CommandSender sender) {
        LoginManagement loginManagement = plugin.getLoginManagement();
        StringJoiner pending = new StringJoiner("§7, §f");
        int count = 0;
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (!loginManagement.isAuthenticated(online.getName())) {
                pending.add(online.getName());
                count++;
            }
        }

        if (count == 0) {
            sender.sendMessage("§aAll online players are authenticated.");
        } else {
            sender.sendMessage("§eUnauthenticated players (" + count + "): §f" + pending);
        }
    }

    private void forceLogin(CommandSender sender, String playerName) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("§cThat player is not online.");
            return;
        }

        LoginManagement loginManagement = plugin.getLoginManagement();
        String name = target.getName();
        if (loginManagement.isAuthenticated(name)) {
            sender.sendMessage("§c" + name + " is already logged in.");
            return;
        }

        loginManagement.setAuthenticated(name);
        loginManagement.resetFailedAttempts(name);
        LoginQueue.removeFromQueue(name);

        plugin.getFoliaLib().runAtEntity(target, task -> {
            target.setWalkSpeed(0.2F);
            target.setFlySpeed(0.1F);
            target.sendMessage(Messages.SUCCESSFUL_LOGIN.asString());
            TitleAPI.getApi().send(target, Messages.TITLE_AFTER_LOGIN.asTitle());
        });
        new AsyncAuthenticateEvent(target).callEvt();

        sender.sendMessage("§aForced " + name + " to log in.");
    }

    private void forceUnregister(CommandSender sender, String playerName) {
        AccountManagement accountManagement = plugin.getAccountManagement();
        Optional<Account> accountOpt = accountManagement.retrieveOrLoad(playerName);
        if (!accountOpt.isPresent()) {
            sender.sendMessage(Messages.NOT_REGISTERED.asString());
            return;
        }

        if (!accountManagement.delete(playerName)) {
            sender.sendMessage(Messages.DATABASE_ERROR.asString());
            return;
        }

        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target != null) {
            plugin.getLoginManagement().cleanup(target.getName());
            plugin.getFoliaLib().runAtEntity(target, task -> target.kickPlayer(Messages.UNREGISTER_KICK.asString()));
        }

        sender.sendMessage("§aUnregistered " + playerName + ".");
    }

    private void forceChangePassword(CommandSender sender, String playerName, String newPassword) {
        int passwordLength = newPassword.length();
        if (passwordLength <= Settings.PASSWORD_SMALL.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_SMALL.asString());
            return;
        }
        if (passwordLength >= Settings.PASSWORD_LARGE.asInt()) {
            sender.sendMessage(Messages.PASSWORD_TOO_LARGE.asString());
            return;
        }

        AccountManagement accountManagement = plugin.getAccountManagement();
        Optional<Account> accountOpt = accountManagement.retrieveOrLoad(playerName);
        if (!accountOpt.isPresent()) {
            sender.sendMessage(Messages.NOT_REGISTERED.asString());
            return;
        }

        Player target = plugin.getServer().getPlayerExact(playerName);
        String address = target != null ? target.getAddress().getAddress().getHostAddress() : accountOpt.get().getAddress();

        String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        if (!accountManagement.update(playerName, hashedPassword, address)) {
            sender.sendMessage(Messages.DATABASE_ERROR.asString());
            return;
        }

        sender.sendMessage(Messages.PASSWORD_CHANGED.asString());
        if (target != null) {
            target.sendMessage(Messages.PASSWORD_CHANGED.asString());
        }
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
