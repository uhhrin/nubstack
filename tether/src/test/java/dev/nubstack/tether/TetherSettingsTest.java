package dev.nubstack.tether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nubstack.spine.config.YamlConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TetherSettingsTest {

    @TempDir
    Path temp;

    @Test
    void blankToken_parsesAndReportsDisabled() throws Exception {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, """
                database:
                  jdbc-url: jdbc:h2:mem:x
                  username: sa
                  password: ""
                  pool-size: 2
                discord:
                  token: ""
                  channel-id: ""
                  guild-id: ""
                  prefer-slash-commands: true
                link:
                  code-length: 8
                  ttl-seconds: 300
                  clipboard-payload: full
                  max-redeem-attempts: 5
                gate:
                  mode: off
                """);
        TetherSettings settings = TetherSettings.from(YamlConfig.load(file));
        assertFalse(settings.hasDiscordToken());
        assertEquals("", settings.discordToken());
        assertEquals(TetherSettings.GateMode.OFF, settings.gateMode());
        assertEquals(LinkCard.ClipboardPayload.FULL, settings.clipboardPayload());
        assertTrue(settings.preferSlashCommands());
    }

    @Test
    void gateMode_andLegacyRequireLink() throws Exception {
        Path soft = temp.resolve("soft.yml");
        Files.writeString(soft, """
                database:
                  jdbc-url: jdbc:h2:mem:x
                  username: sa
                  password: ""
                discord:
                  token: ""
                gate:
                  mode: soft
                """);
        assertEquals(TetherSettings.GateMode.SOFT, TetherSettings.from(YamlConfig.load(soft)).gateMode());

        Path legacy = temp.resolve("legacy.yml");
        Files.writeString(legacy, """
                database:
                  jdbc-url: jdbc:h2:mem:x
                  username: sa
                  password: ""
                discord:
                  token: ""
                require-link-to-join: true
                """);
        assertEquals(TetherSettings.GateMode.HARD, TetherSettings.from(YamlConfig.load(legacy)).gateMode());
    }

    @Test
    void gateMode_unquotedOffIsReadAsOffDespiteYamlBoolean() throws Exception {
        Path file = temp.resolve("off.yml");
        Files.writeString(file, """
                database:
                  jdbc-url: jdbc:h2:mem:x
                  username: sa
                  password: ""
                gate:
                  mode: off
                """);
        assertEquals(TetherSettings.GateMode.OFF, TetherSettings.from(YamlConfig.load(file)).gateMode());
    }

    @Test
    void clipboardPayload_code() throws Exception {
        Path file = temp.resolve("code.yml");
        Files.writeString(file, """
                database:
                  jdbc-url: jdbc:h2:mem:x
                  username: sa
                  password: ""
                discord:
                  token: ""
                link:
                  clipboard-payload: code
                gate:
                  mode: hard
                """);
        TetherSettings settings = TetherSettings.from(YamlConfig.load(file));
        assertEquals(LinkCard.ClipboardPayload.CODE, settings.clipboardPayload());
        assertEquals(TetherSettings.GateMode.HARD, settings.gateMode());
    }
}
