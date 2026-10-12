package dev.nubstack.aegis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AegisCommandParserTest {

    @Test
    void ban_withDurationAndReason() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.BAN, new String[] {"ban", "Steve", "7d", "griefing", "spawn"});
        assertEquals("Steve", parsed.playerName());
        assertEquals(Duration.ofDays(7), parsed.duration());
        assertEquals("griefing spawn", parsed.reason());
        assertFalse(parsed.silent());
        assertFalse(parsed.permanent());
    }

    @Test
    void ban_omittedDurationIsPermanent() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.BAN, new String[] {"ban", "Steve", "cheating"});
        assertNull(parsed.duration());
        assertTrue(parsed.permanent());
        assertEquals("cheating", parsed.reason());
    }

    @Test
    void ban_permTokenIsPermanent() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.BAN, new String[] {"ban", "Steve", "perm", "cheating"});
        assertNull(parsed.duration());
        assertTrue(parsed.permanent());
        assertEquals("cheating", parsed.reason());
    }

    @Test
    void warn_keepsDurationLikeReasonPrefix() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.WARN, new String[] {"warn", "Alex", "7d", "is", "not", "a", "duration"});
        assertNull(parsed.duration());
        assertEquals("7d is not a duration", parsed.reason());
    }

    @Test
    void silentFlag_strippedAnywhereAfterPlayer() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.MUTE, new String[] {"mute", "Alex", "-s", "1h", "spam"});
        assertTrue(parsed.silent());
        assertEquals(Duration.ofHours(1), parsed.duration());
        assertEquals("spam", parsed.reason());
    }

    @Test
    void missingReason_throws() {
        assertThrows(AegisCommandParser.ParseException.class, () ->
                AegisCommandParser.parsePunish(PunishmentType.BAN, new String[] {"ban", "Steve", "7d"}));
    }

    @Test
    void badDuration_leftInReasonWhenNotAlone() {
        AegisCommandParser.PunishArgs parsed = AegisCommandParser.parsePunish(
                PunishmentType.BAN, new String[] {"ban", "Steve", "forever", "cheating"});
        assertNull(parsed.duration());
        assertEquals("forever cheating", parsed.reason());
    }

    @Test
    void historyPage_defaultsToOne() {
        assertEquals(1, AegisCommandParser.parsePage(new String[] {"history", "Steve"}));
        assertEquals(3, AegisCommandParser.parsePage(new String[] {"history", "Steve", "3"}));
    }
}
