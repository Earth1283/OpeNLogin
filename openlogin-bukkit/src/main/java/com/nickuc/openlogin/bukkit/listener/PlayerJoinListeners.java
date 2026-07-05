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

package com.nickuc.openlogin.bukkit.listener;

import com.nickuc.openlogin.bukkit.OpenLoginBukkit;
import com.nickuc.openlogin.bukkit.api.events.AsyncAuthenticateEvent;
import com.nickuc.openlogin.bukkit.task.LoginQueue;
import com.nickuc.openlogin.bukkit.ui.title.TitleAPI;
import com.nickuc.openlogin.common.model.Account;
import com.nickuc.openlogin.common.settings.Messages;
import com.nickuc.openlogin.common.settings.Settings;
import lombok.AllArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

@AllArgsConstructor
public class PlayerJoinListeners implements Listener {

    private final OpenLoginBukkit plugin;

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String name = player.getName();

        Optional<Account> accountOpt = plugin.getAccountManagement().retrieveOrLoad(name);
        boolean registered = accountOpt.isPresent();

        if (registered && tryResumeSession(player, accountOpt.get())) {
            return;
        }

        LoginQueue.addToQueue(name, registered);

        player.setWalkSpeed(0F);
        player.setFlySpeed(0F);

        if (registered) {
            player.sendMessage(Messages.MESSAGE_LOGIN.asString());
            TitleAPI.getApi().send(player, Messages.TITLE_BEFORE_LOGIN.asTitle());
        } else {
            player.sendMessage(Messages.MESSAGE_REGISTER.asString());
            TitleAPI.getApi().send(player, Messages.TITLE_BEFORE_REGISTER.asTitle());
        }
    }

    /**
     * Auto-authenticates a returning player when they rejoin from the same address within the
     * configured session timeout, skipping the password prompt entirely.
     *
     * @return true if the player was auto-authenticated
     */
    private boolean tryResumeSession(Player player, Account account) {
        if (player.getAddress() == null) {
            return false;
        }

        String address = player.getAddress().getAddress().getHostAddress();
        if (!plugin.getLoginManagement().isSessionValid(account, address, Settings.SESSION_TIMEOUT.asInt())) {
            return false;
        }

        String name = player.getName();
        plugin.getLoginManagement().setAuthenticated(name);

        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);
        player.sendMessage(Messages.SUCCESSFUL_LOGIN.asString());
        TitleAPI.getApi().send(player, Messages.TITLE_AFTER_LOGIN.asTitle());

        plugin.getFoliaLib().runAsync(task -> {
            plugin.getAccountManagement().update(name, account.getHashedPassword(), address);
            new AsyncAuthenticateEvent(player).callEvt();
        });
        return true;
    }
}
