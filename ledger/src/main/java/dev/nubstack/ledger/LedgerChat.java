package dev.nubstack.ledger;

import dev.nubstack.spine.text.Messages;
import dev.nubstack.spine.text.PrefixedMessages;
import dev.nubstack.spine.ui.ActionBars;
import java.math.BigDecimal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Adventure builders for ledger player feedback (unit-tested click/hover payloads).
 */
public final class LedgerChat {

    private final PrefixedMessages msg = Messages.prefixed("ledger");
    private final CurrencyFormat currency;

    public LedgerChat(CurrencyFormat currency) {
        this.currency = currency;
    }

    /**
     * @param canViewHistory whether the viewer may open this player's history (always true for self)
     */
    public Component balanceLine(String playerName, BigDecimal balance, boolean self, boolean canViewHistory) {
        Component amount = msg.hoverDetail(currency.format(balance), "Exact: " + balance.toPlainString());
        var line = Component.text()
                .append(msg.info(self ? "Balance: " : playerName + " balance: "))
                .append(amount)
                .append(Component.space())
                .append(msg.suggestButton("Pay", self ? "/pay " : "/pay " + playerName + " "));
        if (self) {
            line.append(Component.space()).append(msg.runButton("History", "/ledger history"));
        } else if (canViewHistory) {
            line.append(Component.space()).append(msg.runButton("History", "/ledger history " + playerName));
        }
        return line.build();
    }

    public Component insufficientFunds(BigDecimal shortfall) {
        return msg.error("Insufficient funds")
                .hoverEvent(HoverEvent.showText(
                        Component.text("You need " + currency.format(shortfall) + " more", NamedTextColor.GRAY)));
    }

    public Component paySuccess(BigDecimal amount, String toName, long receiptId) {
        return Component.text()
                .append(msg.success("Paid " + currency.format(amount) + " to " + toName))
                .append(Component.space())
                .append(msg.copyButton("Copy receipt", Long.toString(receiptId)))
                .build();
    }

    /** Action bar text for the receiving player. */
    public Component incomingPay(BigDecimal amount, String fromName) {
        return ActionBars.text("+" + currency.compact(amount) + " " + currency.currencyName() + " from " + fromName);
    }

    /** Replaces the generic spine prompt so the button reads {@code [Confirm pay 500 to Steve]}. */
    public Component confirmPay(String token, BigDecimal amount, String toName) {
        return confirmLine(
                "Pay " + currency.format(amount) + " to " + toName + "?",
                "Confirm pay " + currency.compact(amount) + " to " + toName,
                token);
    }

    public Component confirmSet(String token, String playerName, BigDecimal amount) {
        return confirmLine(
                "Set " + playerName + " balance to " + currency.format(amount) + "?",
                "Confirm set " + playerName + " to " + currency.compact(amount),
                token);
    }

    public Component receiptLine(long id, BigDecimal delta, String reason, String when) {
        return Component.text()
                .append(msg.info("Receipt #" + id + " " + currency.format(delta) + " " + reason + " (" + when + ")"))
                .append(Component.space())
                .append(msg.copyButton("Copy id", Long.toString(id)))
                .build();
    }

    public Component historyLine(long id, BigDecimal delta, String reason) {
        return Component.text()
                .append(msg.info("#" + id + " " + currency.format(delta) + " " + reason))
                .append(Component.space())
                .append(msg.copyButton("Copy", Long.toString(id)))
                .build();
    }

    private static Component confirmLine(String prompt, String confirmLabel, String token) {
        return Component.text()
                .append(Component.text(prompt, NamedTextColor.YELLOW))
                .append(Component.space())
                .append(runBracket(confirmLabel, "/ledger confirm " + token, NamedTextColor.GREEN))
                .append(Component.space())
                .append(runBracket("Cancel", "/ledger cancel " + token, NamedTextColor.RED))
                .build();
    }

    private static Component runBracket(String label, String command, NamedTextColor color) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.DARK_GRAY))
                .append(Component.text(label, color, TextDecoration.BOLD))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Click to run", NamedTextColor.GRAY)))
                .build();
    }
}
