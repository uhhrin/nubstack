package dev.nubstack.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class LedgerChatTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final LedgerChat chat = new LedgerChat(new CurrencyFormat("Credits"));

    @Test
    void balanceShowsExactValueOnHover() {
        Component line = chat.balanceLine("Steve", new BigDecimal("1234.50"), true, true);
        assertTrue(PLAIN.serialize(line).contains("1,234.50 Credits"));
        assertTrue(hovers(line).stream().anyMatch(h -> h.contains("1234.50")));
    }

    @Test
    void selfBalanceOffersPaySuggestAndHistoryRun() {
        Component line = chat.balanceLine("Steve", new BigDecimal("5.00"), true, true);
        assertEquals(List.of(
                new Click(ClickEvent.Action.SUGGEST_COMMAND, "/pay "),
                new Click(ClickEvent.Action.RUN_COMMAND, "/ledger history")), clicks(line));
    }

    @Test
    void otherBalanceSuggestsPayWithName() {
        Component line = chat.balanceLine("Alex", new BigDecimal("5.00"), false, false);
        assertEquals(List.of(new Click(ClickEvent.Action.SUGGEST_COMMAND, "/pay Alex ")), clicks(line));
    }

    @Test
    void otherBalanceHistoryButtonNeedsAuditAccess() {
        Component line = chat.balanceLine("Alex", new BigDecimal("5.00"), false, true);
        assertTrue(clicks(line).contains(new Click(ClickEvent.Action.RUN_COMMAND, "/ledger history Alex")));
    }

    @Test
    void insufficientFundsHoverSaysHowMuchMore() {
        Component line = chat.insufficientFunds(new BigDecimal("40.00"));
        assertTrue(hovers(line).stream().anyMatch(h -> h.contains("You need 40.00 Credits more")));
    }

    @Test
    void paySuccessCopiesReceiptId() {
        Component line = chat.paySuccess(new BigDecimal("25.00"), "Steve", 77L);
        assertTrue(PLAIN.serialize(line).contains("25.00 Credits"));
        assertEquals(List.of(new Click(ClickEvent.Action.COPY_TO_CLIPBOARD, "77")), clicks(line));
    }

    @Test
    void incomingPayReadsLikeActionBarText() {
        assertEquals("+25 Credits from Steve",
                PLAIN.serialize(chat.incomingPay(new BigDecimal("25.00"), "Steve")));
    }

    @Test
    void confirmPayHasNamedConfirmAndCancelButtons() {
        Component line = chat.confirmPay("tok123", new BigDecimal("500.00"), "Steve");
        assertTrue(PLAIN.serialize(line).contains("[Confirm pay 500 to Steve]"));
        assertTrue(PLAIN.serialize(line).contains("[Cancel]"));
        assertEquals(List.of(
                new Click(ClickEvent.Action.RUN_COMMAND, "/ledger confirm tok123"),
                new Click(ClickEvent.Action.RUN_COMMAND, "/ledger cancel tok123")), clicks(line));
    }

    @Test
    void confirmSetNamesPlayerAndAmount() {
        Component line = chat.confirmSet("tok9", "Steve", new BigDecimal("0.00"));
        assertTrue(PLAIN.serialize(line).contains("[Confirm set Steve to 0]"));
        assertEquals(new Click(ClickEvent.Action.RUN_COMMAND, "/ledger confirm tok9"), clicks(line).get(0));
    }

    @Test
    void historyLineCopiesReceiptId() {
        Component line = chat.historyLine(9L, new BigDecimal("-3.00"), "player pay");
        assertTrue(PLAIN.serialize(line).contains("#9"));
        assertTrue(PLAIN.serialize(line).contains("-3.00 Credits"));
        assertEquals(List.of(new Click(ClickEvent.Action.COPY_TO_CLIPBOARD, "9")), clicks(line));
    }

    private record Click(ClickEvent.Action action, String value) {
    }

    private static List<Click> clicks(Component root) {
        List<Click> out = new ArrayList<>();
        walk(root, c -> {
            ClickEvent<?> click = c.clickEvent();
            if (click != null) {
                var payload = assertInstanceOf(ClickEvent.Payload.Text.class, click.payload());
                out.add(new Click(click.action(), payload.value()));
            }
        });
        return out;
    }

    private static List<String> hovers(Component root) {
        List<String> out = new ArrayList<>();
        walk(root, c -> {
            HoverEvent<?> hover = c.hoverEvent();
            if (hover != null && hover.value() instanceof Component text) {
                out.add(PLAIN.serialize(text));
            }
        });
        return out;
    }

    private static void walk(Component node, Consumer<Component> visit) {
        visit.accept(node);
        for (Component child : node.children()) {
            walk(child, visit);
        }
    }
}
