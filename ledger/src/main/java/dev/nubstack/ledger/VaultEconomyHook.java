package dev.nubstack.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class VaultEconomyHook implements Economy {

    private final EconomyService service;
    private final CurrencyFormat currency;

    public VaultEconomyHook(EconomyService service, CurrencyFormat currency) {
        this.service = service;
        this.currency = currency;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "ledger"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) {
        return currency.format(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
    }
    @Override public String currencyNamePlural() { return currency.currencyName(); }
    @Override public String currencyNameSingular() { return currency.currencyName(); }
    @Override public boolean hasAccount(String playerName) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player) { return true; }
    @Override public boolean hasAccount(String playerName, String worldName) { return true; }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return true; }
    @Override public double getBalance(String playerName) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached == null) {
            return 0.0D;
        }
        return getBalance(cached);
    }
    @Override public double getBalance(OfflinePlayer player) {
        return service.balance(player.getUniqueId()).doubleValue();
    }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public boolean has(String playerName, double amount) { return getBalance(playerName) >= amount; }
    @Override public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached == null) {
            return failure(amount, 0, "Unknown player");
        }
        return withdrawPlayer(cached, amount);
    }

    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        try {
            EconomyService.OptionalLongResult result = service.withdraw(
                    player.getUniqueId(), toAmount(amount), "vault-withdraw");
            double bal = getBalance(player);
            if (!result.success()) {
                return failure(amount, bal, "Insufficient funds");
            }
            return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.SUCCESS, null);
        } catch (RuntimeException ex) {
            return failure(amount, safeBalance(player), ex.getMessage());
        }
    }

    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached == null) {
            return failure(amount, 0, "Unknown player");
        }
        return depositPlayer(cached, amount);
    }

    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        try {
            service.deposit(player.getUniqueId(), toAmount(amount), "vault-deposit");
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (RuntimeException ex) {
            return failure(amount, safeBalance(player), ex.getMessage());
        }
    }

    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override public EconomyResponse createBank(String name, String player) { return notSupported(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse deleteBank(String name) { return notSupported(); }
    @Override public EconomyResponse bankBalance(String name) { return notSupported(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notSupported(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return notSupported(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return notSupported(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notSupported(); }
    @Override public List<String> getBanks() { return Collections.emptyList(); }
    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }

    /**
     * Package-visible for unit tests without a Bukkit OfflinePlayer stub.
     */
    EconomyResponse depositUuid(java.util.UUID playerId, double amount) {
        try {
            service.deposit(playerId, toAmount(amount), "vault-deposit");
            double bal = service.balance(playerId).doubleValue();
            return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.SUCCESS, null);
        } catch (RuntimeException ex) {
            return failure(amount, service.balance(playerId).doubleValue(), ex.getMessage());
        }
    }

    EconomyResponse withdrawUuid(java.util.UUID playerId, double amount) {
        try {
            EconomyService.OptionalLongResult result =
                    service.withdraw(playerId, toAmount(amount), "vault-withdraw");
            double bal = service.balance(playerId).doubleValue();
            if (!result.success()) {
                return failure(amount, bal, "Insufficient funds");
            }
            return new EconomyResponse(amount, bal, EconomyResponse.ResponseType.SUCCESS, null);
        } catch (RuntimeException ex) {
            return failure(amount, service.balance(playerId).doubleValue(), ex.getMessage());
        }
    }

    private static BigDecimal toAmount(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private double safeBalance(OfflinePlayer player) {
        try {
            return getBalance(player);
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private static EconomyResponse failure(double amount, double balance, String error) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE, error);
    }

    private static EconomyResponse notSupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks unsupported");
    }
}
