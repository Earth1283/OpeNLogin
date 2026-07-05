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

package com.nickuc.openlogin.common.manager;

import com.nickuc.openlogin.common.model.Account;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class LoginManagement {

    private final Map<String, Long> lock = new HashMap<>();
    private final HashSet<String> logged = new HashSet<>();
    private final Map<String, FailedAttempts> failedAttempts = new HashMap<>();

    private final AccountManagement accountManagement;

    /**
     * Clears the player cache
     *
     * @param name the name of the player
     */
    public void cleanup(@NonNull String name) {
        String nameLower = name.toLowerCase();
        synchronized (lock) {
            lock.remove(nameLower);
        }
        synchronized (logged) {
            logged.remove(nameLower);
        }
        accountManagement.invalidateCache(nameLower);
    }

    /**
     * Set the player authenticated.
     *
     * @param name the name of the player
     */
    public void setAuthenticated(@NonNull String name) {
        synchronized (logged) {
            logged.add(name.toLowerCase());
        }
    }

    /**
     * Check if the player is authenticated.
     *
     * @param name the name of the player
     * @return true if authenticated
     */
    public boolean isAuthenticated(@NonNull String name) {
        synchronized (logged) {
            return logged.contains(name.toLowerCase());
        }
    }

    /**
     * Checks whether a returning player can skip re-entering their password: the join address must
     * match the address stored on the account and the last login must be within the timeout window.
     *
     * @param account         the player's account
     * @param address         the address the player is currently joining from
     * @param timeoutMinutes  the configured session timeout, in minutes (0 or less disables sessions)
     * @return true if the session is still valid
     */
    public boolean isSessionValid(@NonNull Account account, @NonNull String address, int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            return false;
        }
        if (!address.equals(account.getAddress())) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - account.getLastLogin();
        return elapsed >= 0 && elapsed <= TimeUnit.MINUTES.toMillis(timeoutMinutes);
    }

    /**
     * Checks if the player is unlocked and lock it.
     *
     * @param name the name of the player
     */
    public boolean isUnlocked(@NonNull String name) {
        String toLower = name.toLowerCase();
        synchronized (lock) {
            Long millis = lock.get(toLower);
            if (millis == null || millis - System.currentTimeMillis() < 0) {
                lock.put(name.toLowerCase(), System.currentTimeMillis() + 750L);
                return true;
            }
            return false;
        }
    }

    /**
     * Checks whether the player has exceeded the allowed number of wrong password attempts and
     * is currently locked out. Intentionally not cleared on disconnect, since a brute-force
     * attempt would otherwise just reconnect to reset its own counter.
     *
     * @param name          the name of the player
     * @param maxAttempts   attempts allowed before lockout (0 or less disables this protection)
     * @param resetMinutes  minutes after the last attempt before the counter resets
     * @return true if the player is currently locked out
     */
    public boolean isBlocked(@NonNull String name, int maxAttempts, int resetMinutes) {
        if (maxAttempts <= 0) {
            return false;
        }
        String key = name.toLowerCase();
        synchronized (failedAttempts) {
            FailedAttempts attempts = failedAttempts.get(key);
            if (attempts == null) {
                return false;
            }
            if (attempts.isExpired(resetMinutes)) {
                failedAttempts.remove(key);
                return false;
            }
            return attempts.count >= maxAttempts;
        }
    }

    /**
     * Registers a wrong password attempt for the player.
     *
     * @param name         the name of the player
     * @param resetMinutes minutes after the last attempt before the counter resets
     */
    public void registerFailedAttempt(@NonNull String name, int resetMinutes) {
        String key = name.toLowerCase();
        synchronized (failedAttempts) {
            FailedAttempts attempts = failedAttempts.get(key);
            if (attempts == null || attempts.isExpired(resetMinutes)) {
                attempts = new FailedAttempts();
                failedAttempts.put(key, attempts);
            }
            attempts.count++;
            attempts.lastAttempt = System.currentTimeMillis();
        }
    }

    /**
     * Clears the failed attempt counter for the player, called after a successful login.
     *
     * @param name the name of the player
     */
    public void resetFailedAttempts(@NonNull String name) {
        synchronized (failedAttempts) {
            failedAttempts.remove(name.toLowerCase());
        }
    }

    private static class FailedAttempts {
        private int count;
        private long lastAttempt;

        private boolean isExpired(int resetMinutes) {
            return resetMinutes > 0 && System.currentTimeMillis() - lastAttempt > TimeUnit.MINUTES.toMillis(resetMinutes);
        }
    }
}
