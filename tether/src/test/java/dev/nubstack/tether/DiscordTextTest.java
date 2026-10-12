package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiscordTextTest {

    @Test
    void escapeMarkdown_escapesFormattingMetacharacters() {
        assertEquals("\\*bold\\* \\_under\\_ \\~strike\\~ \\`code\\` \\|spoiler\\|",
                DiscordText.escapeMarkdown("*bold* _under_ ~strike~ `code` |spoiler|"));
    }

    @Test
    void neutralizeMentions_breaksEveryoneHereAndUserTags() {
        String out = DiscordText.neutralizeMentions("hi @everyone @here <@123>");
        assertFalse(out.contains("@everyone"));
        assertFalse(out.contains("@here"));
        assertTrue(out.contains("@\u200beveryone"));
        assertTrue(out.contains("<@\u200b123>"));
    }

    @Test
    void formatOutbound_escapesPlayerControlledContent() {
        String line = DiscordText.formatOutbound("Steve*", "hello @everyone **admin**");
        assertTrue(line.contains("Steve\\*"));
        assertTrue(line.contains("@\u200beveryone"));
        assertTrue(line.contains("\\*\\*admin\\*\\*"));
    }

    @Test
    void escapeMiniMessage_escapesTagDelimiters() {
        assertEquals("hello \\<red\\>x\\</red\\>", DiscordText.escapeMiniMessage("hello <red>x</red>"));
        assertEquals("a\\\\b", DiscordText.escapeMiniMessage("a\\b"));
    }

    @Test
    void linkedReply_escapesPlayerName() {
        assertTrue(DiscordText.linkedReply("Steve_*").contains("Steve\\_\\*"));
    }

    @Test
    void redeemFailure_hasDistinctMessagePerStatus() {
        String unknown = DiscordText.redeemFailure(LinkService.RedeemStatus.UNKNOWN);
        String expired = DiscordText.redeemFailure(LinkService.RedeemStatus.EXPIRED);
        String locked = DiscordText.redeemFailure(LinkService.RedeemStatus.LOCKED);
        assertEquals(3, java.util.Set.of(unknown, expired, locked).size());
        assertTrue(expired.contains("expired"));
    }

    @Test
    void redeemFailure_rejectsOk() {
        assertThrows(IllegalArgumentException.class, () -> DiscordText.redeemFailure(LinkService.RedeemStatus.OK));
    }

    @Test
    void truncate_capsLength() {
        assertEquals("abc", DiscordText.truncate("abcdef", 3));
        assertEquals("abc", DiscordText.truncate("abc", 3));
    }
}
