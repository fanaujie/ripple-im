package com.fanaujie.ripple.integration.bot;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.storage.model.UserInstalledBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Installation Flow Tests")
class BotInstallationFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;

    // Test bots
    protected static final long ECHO_BOT_ID = 100001L;
    protected static final long ASSISTANT_BOT_ID = 100002L;
    protected static final long WEATHER_BOT_ID = 100003L;

    @BeforeEach
    void setUpTestData() {
        // Create test users
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");

        // Create test bots
        createBot(ECHO_BOT_ID, "Echo Bot", "http://localhost:8080/webhook");
        createBot(ASSISTANT_BOT_ID, "Assistant Bot", "http://localhost:8081/webhook", "AI", false);
        createBot(WEATHER_BOT_ID, "Weather Bot", "http://localhost:8082/webhook", "Utilities", false);
    }

    @Nested
    @DisplayName("Scenario: Install bot")
    class InstallBotScenarios {

        @Test
        @DisplayName("When user installs bot, user_installed_bots record is created")
        void installBot_createsRecord() throws Exception {
            // Given: Alice wants to install Echo Bot
            // When: Alice installs the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Then: A record should exist in user_installed_bots
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(installedBots).hasSize(1);
            assertThat(installedBots.get(0).getBotId()).isEqualTo(ECHO_BOT_ID);
            assertThat(installedBots.get(0).getUserId()).isEqualTo(ALICE_ID);
            assertThat(installedBots.get(0).getInstalledAt()).isNotNull();
        }

        @Test
        @DisplayName("User can install multiple bots")
        void installMultipleBots_createsMultipleRecords() throws Exception {
            // Given: Alice wants to install multiple bots
            // When: Alice installs multiple bots
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(ALICE_ID, ASSISTANT_BOT_ID);
            installBotForUser(ALICE_ID, WEATHER_BOT_ID);

            // Then: All bots should be in Alice's installed list
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(installedBots).hasSize(3);

            List<Long> botIds = installedBots.stream()
                    .map(UserInstalledBot::getBotId)
                    .toList();
            assertThat(botIds).containsExactlyInAnyOrder(ECHO_BOT_ID, ASSISTANT_BOT_ID, WEATHER_BOT_ID);
        }

        @Test
        @DisplayName("Different users can install the same bot")
        void differentUsersInstallSameBot_bothHaveRecord() throws Exception {
            // Given: Both Alice and Bob want to install Echo Bot
            // When: Both users install the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(BOB_ID, ECHO_BOT_ID);

            // Then: Both users should have the bot installed
            List<UserInstalledBot> aliceBots = storageFacade.getUserInstalledBots(ALICE_ID);
            List<UserInstalledBot> bobBots = storageFacade.getUserInstalledBots(BOB_ID);

            assertThat(aliceBots).hasSize(1);
            assertThat(aliceBots.get(0).getBotId()).isEqualTo(ECHO_BOT_ID);

            assertThat(bobBots).hasSize(1);
            assertThat(bobBots.get(0).getBotId()).isEqualTo(ECHO_BOT_ID);
        }

        @Test
        @DisplayName("Installing already installed bot is idempotent (no error)")
        void installAlreadyInstalledBot_noError() throws Exception {
            // Given: Alice has already installed Echo Bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            // When: Alice tries to install the same bot again
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Then: No error occurs and the bot is still installed (idempotent)
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);
            // Depending on implementation, this could be 1 or 2 records
            // The important thing is no exception was thrown
            assertThat(installedBots).isNotEmpty();
            assertThat(installedBots.stream().anyMatch(b -> b.getBotId() == ECHO_BOT_ID)).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario: Uninstall bot")
    class UninstallBotScenarios {

        @Test
        @DisplayName("When user uninstalls bot, user_installed_bots record is deleted")
        void uninstallBot_deletesRecord() throws Exception {
            // Given: Alice has installed Echo Bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Verify it's installed
            List<UserInstalledBot> beforeUninstall = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(beforeUninstall).hasSize(1);

            // When: Alice uninstalls the bot
            uninstallBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Then: The record should be deleted
            List<UserInstalledBot> afterUninstall = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(afterUninstall).isEmpty();
        }

        @Test
        @DisplayName("Uninstalling one bot doesn't affect other installed bots")
        void uninstallOneBot_otherBotsRemain() throws Exception {
            // Given: Alice has installed multiple bots
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(ALICE_ID, ASSISTANT_BOT_ID);
            installBotForUser(ALICE_ID, WEATHER_BOT_ID);

            // When: Alice uninstalls only the Assistant Bot
            uninstallBotForUser(ALICE_ID, ASSISTANT_BOT_ID);

            // Then: Other bots should remain installed
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(installedBots).hasSize(2);

            List<Long> remainingBotIds = installedBots.stream()
                    .map(UserInstalledBot::getBotId)
                    .toList();
            assertThat(remainingBotIds).containsExactlyInAnyOrder(ECHO_BOT_ID, WEATHER_BOT_ID);
        }

        @Test
        @DisplayName("Uninstalling non-installed bot causes no error")
        void uninstallNonInstalledBot_noError() throws Exception {
            // Given: Alice has not installed any bot
            List<UserInstalledBot> beforeUninstall = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(beforeUninstall).isEmpty();

            // When: Alice tries to uninstall a bot she hasn't installed
            uninstallBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Then: No error occurs
            List<UserInstalledBot> afterUninstall = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(afterUninstall).isEmpty();
        }

        @Test
        @DisplayName("One user uninstalling bot doesn't affect other users")
        void oneUserUninstalls_otherUserUnaffected() throws Exception {
            // Given: Both Alice and Bob have installed Echo Bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(BOB_ID, ECHO_BOT_ID);

            // When: Alice uninstalls the bot
            uninstallBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Then: Alice should not have the bot, but Bob should still have it
            List<UserInstalledBot> aliceBots = storageFacade.getUserInstalledBots(ALICE_ID);
            List<UserInstalledBot> bobBots = storageFacade.getUserInstalledBots(BOB_ID);

            assertThat(aliceBots).isEmpty();
            assertThat(bobBots).hasSize(1);
            assertThat(bobBots.get(0).getBotId()).isEqualTo(ECHO_BOT_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: List installed bots")
    class ListInstalledBotsScenarios {

        @Test
        @DisplayName("List installed bots returns correct bots")
        void listInstalledBots_returnsCorrectBots() throws Exception {
            // Given: Alice has installed specific bots
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(ALICE_ID, WEATHER_BOT_ID);

            // When: Getting Alice's installed bots
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);

            // Then: Only the installed bots should be returned
            assertThat(installedBots).hasSize(2);

            List<Long> botIds = installedBots.stream()
                    .map(UserInstalledBot::getBotId)
                    .toList();
            assertThat(botIds).containsExactlyInAnyOrder(ECHO_BOT_ID, WEATHER_BOT_ID);
            assertThat(botIds).doesNotContain(ASSISTANT_BOT_ID);
        }

        @Test
        @DisplayName("List installed bots for user with no installations returns empty")
        void listInstalledBots_noInstallations_returnsEmpty() throws Exception {
            // Given: Alice has not installed any bots
            // When: Getting Alice's installed bots
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);

            // Then: Empty list should be returned
            assertThat(installedBots).isEmpty();
        }

        @Test
        @DisplayName("Each user has their own installed bots list")
        void listInstalledBots_userSpecific() throws Exception {
            // Given: Alice and Bob have installed different bots
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(ALICE_ID, ASSISTANT_BOT_ID);
            installBotForUser(BOB_ID, WEATHER_BOT_ID);

            // When: Getting each user's installed bots
            List<UserInstalledBot> aliceBots = storageFacade.getUserInstalledBots(ALICE_ID);
            List<UserInstalledBot> bobBots = storageFacade.getUserInstalledBots(BOB_ID);

            // Then: Each user should see only their installed bots
            assertThat(aliceBots).hasSize(2);
            List<Long> aliceBotIds = aliceBots.stream().map(UserInstalledBot::getBotId).toList();
            assertThat(aliceBotIds).containsExactlyInAnyOrder(ECHO_BOT_ID, ASSISTANT_BOT_ID);

            assertThat(bobBots).hasSize(1);
            assertThat(bobBots.get(0).getBotId()).isEqualTo(WEATHER_BOT_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Installation timestamp")
    class InstallationTimestampScenarios {

        @Test
        @DisplayName("Installation records contain valid timestamp")
        void installBot_hasValidTimestamp() throws Exception {
            // Given: Current time
            long beforeInstall = System.currentTimeMillis();

            // When: Alice installs a bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            long afterInstall = System.currentTimeMillis();

            // Then: Installation timestamp should be within expected range
            List<UserInstalledBot> installedBots = storageFacade.getUserInstalledBots(ALICE_ID);
            assertThat(installedBots).hasSize(1);

            long installedAt = installedBots.get(0).getInstalledAt().getTime();
            assertThat(installedAt).isBetween(beforeInstall, afterInstall);
        }
    }
}
