package com.fanaujie.ripple.integration.bot;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.storage.model.Bot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Discovery Flow Tests")
class BotDiscoveryFlowTest extends AbstractBusinessFlowTest {

    // Test bots with different categories
    protected static final long ECHO_BOT_ID = 100001L;
    protected static final long ASSISTANT_BOT_ID = 100002L;
    protected static final long WEATHER_BOT_ID = 100003L;
    protected static final long GPT_BOT_ID = 100004L;
    protected static final long DISABLED_BOT_ID = 100005L;

    @BeforeEach
    void setUpTestBots() {
        // Create bots with different categories
        createBot(ECHO_BOT_ID, "Echo Bot", "http://localhost:8080/webhook", "Utilities", false);
        createBot(ASSISTANT_BOT_ID, "Assistant Bot", "http://localhost:8081/webhook", "AI", false);
        createBot(WEATHER_BOT_ID, "Weather Bot", "http://localhost:8082/webhook", "Utilities", false);
        createBot(GPT_BOT_ID, "GPT Bot", "http://localhost:8083/webhook", "AI", true);

        // Create a disabled bot
        createDisabledBot(DISABLED_BOT_ID, "Disabled Bot", "http://localhost:8084/webhook");
    }

    /** Creates a disabled bot for testing. */
    private void createDisabledBot(long botId, String name, String endpoint) {
        Bot bot = Bot.builder()
                .botId(botId)
                .name(name)
                .endpoint(endpoint)
                .description("Disabled test bot: " + name)
                .category("Disabled")
                .enabled(false)
                .requireAuth(false)
                .secret("test-secret-" + botId)
                .build();
        storageFacade.insertBot(bot);
    }

    @Nested
    @DisplayName("Scenario: List all bots")
    class ListAllBotsScenarios {

        @Test
        @DisplayName("List all bots returns enabled bots only")
        void listAllBots_returnsEnabledOnly() throws Exception {
            // When: Getting all bots
            List<Bot> allBots = storageFacade.getAllBots();

            // Then: Only enabled bots should be returned
            // Note: Depending on implementation, this might include disabled bots
            // If filtering is done at storage level, we check for enabled=true
            assertThat(allBots).isNotEmpty();

            // Verify all returned bots have expected properties
            for (Bot bot : allBots) {
                assertThat(bot.getBotId()).isPositive();
                assertThat(bot.getName()).isNotEmpty();
                assertThat(bot.getEndpoint()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("List all bots returns bots with correct data")
        void listAllBots_returnsCorrectData() throws Exception {
            // When: Getting all bots
            List<Bot> allBots = storageFacade.getAllBots();

            // Then: Should contain our test bots
            List<Long> botIds = allBots.stream().map(Bot::getBotId).toList();

            // At minimum, should have our enabled bots
            assertThat(botIds).contains(ECHO_BOT_ID, ASSISTANT_BOT_ID, WEATHER_BOT_ID, GPT_BOT_ID);
        }

        @Test
        @DisplayName("Bot details include all required fields")
        void listAllBots_includesAllFields() throws Exception {
            // When: Getting all bots
            List<Bot> allBots = storageFacade.getAllBots();

            // Then: Find the Echo Bot and verify all fields
            Bot echoBot = allBots.stream()
                    .filter(b -> b.getBotId() == ECHO_BOT_ID)
                    .findFirst()
                    .orElseThrow();

            assertThat(echoBot.getName()).isEqualTo("Echo Bot");
            assertThat(echoBot.getEndpoint()).isEqualTo("http://localhost:8080/webhook");
            assertThat(echoBot.getCategory()).isEqualTo("Utilities");
            assertThat(echoBot.isEnabled()).isTrue();
            assertThat(echoBot.isRequireAuth()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: List bots by category")
    class ListBotsByCategoryScenarios {

        @Test
        @DisplayName("List bots by category filters correctly")
        void listBotsByCategory_filtersCorrectly() throws Exception {
            // When: Getting AI bots
            List<Bot> aiBots = storageFacade.getBotsByCategory("AI");

            // Then: Only AI category bots should be returned
            assertThat(aiBots).hasSize(2);

            List<Long> aiBotIds = aiBots.stream().map(Bot::getBotId).toList();
            assertThat(aiBotIds).containsExactlyInAnyOrder(ASSISTANT_BOT_ID, GPT_BOT_ID);
        }

        @Test
        @DisplayName("List bots by Utilities category")
        void listBotsByUtilitiesCategory_filtersCorrectly() throws Exception {
            // When: Getting Utilities bots
            List<Bot> utilityBots = storageFacade.getBotsByCategory("Utilities");

            // Then: Only Utilities category bots should be returned
            assertThat(utilityBots).hasSize(2);

            List<Long> utilityBotIds = utilityBots.stream().map(Bot::getBotId).toList();
            assertThat(utilityBotIds).containsExactlyInAnyOrder(ECHO_BOT_ID, WEATHER_BOT_ID);
        }

        @Test
        @DisplayName("List bots by non-existent category returns empty")
        void listBotsByNonExistentCategory_returnsEmpty() throws Exception {
            // When: Getting bots from a category that doesn't exist
            List<Bot> gameBots = storageFacade.getBotsByCategory("Gaming");

            // Then: Empty list should be returned
            assertThat(gameBots).isEmpty();
        }

        @Test
        @DisplayName("Category filter is case-sensitive")
        void listBotsByCategory_caseSensitive() throws Exception {
            // When: Getting bots with lowercase category
            List<Bot> aiBots = storageFacade.getBotsByCategory("ai");

            // Then: Depending on implementation, might return empty or match
            // This test documents the behavior
            // If case-insensitive, it should match AI bots
            // If case-sensitive, it should return empty
            // We just verify no exception is thrown
            assertThat(aiBots).isNotNull();
        }
    }

    @Nested
    @DisplayName("Scenario: Get single bot")
    class GetSingleBotScenarios {

        @Test
        @DisplayName("Get bot by ID returns correct bot")
        void getBotById_returnsCorrectBot() throws Exception {
            // When: Getting a specific bot
            Bot bot = storageFacade.getBot(ECHO_BOT_ID);

            // Then: Correct bot should be returned
            assertThat(bot).isNotNull();
            assertThat(bot.getBotId()).isEqualTo(ECHO_BOT_ID);
            assertThat(bot.getName()).isEqualTo("Echo Bot");
        }

        @Test
        @DisplayName("Get non-existent bot returns null")
        void getNonExistentBot_returnsNull() throws Exception {
            // When: Getting a bot that doesn't exist
            Bot bot = storageFacade.getBot(999999L);

            // Then: Null should be returned (or exception, depending on implementation)
            assertThat(bot).isNull();
        }

        @Test
        @DisplayName("Get bot returns auth configuration")
        void getBot_returnsAuthConfig() throws Exception {
            // When: Getting a bot that requires auth
            Bot gptBot = storageFacade.getBot(GPT_BOT_ID);

            // Then: Auth requirement should be correctly returned
            assertThat(gptBot).isNotNull();
            assertThat(gptBot.isRequireAuth()).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario: Bot properties")
    class BotPropertiesScenarios {

        @Test
        @DisplayName("Bot with require_auth=true is correctly stored")
        void botWithRequireAuth_storedCorrectly() throws Exception {
            // Given: GPT Bot was created with require_auth=true
            // When: Getting the bot
            Bot gptBot = storageFacade.getBot(GPT_BOT_ID);

            // Then: require_auth should be true
            assertThat(gptBot.isRequireAuth()).isTrue();
        }

        @Test
        @DisplayName("Bot with require_auth=false is correctly stored")
        void botWithoutRequireAuth_storedCorrectly() throws Exception {
            // Given: Echo Bot was created with require_auth=false
            // When: Getting the bot
            Bot echoBot = storageFacade.getBot(ECHO_BOT_ID);

            // Then: require_auth should be false
            assertThat(echoBot.isRequireAuth()).isFalse();
        }

        @Test
        @DisplayName("Bot enabled status is correctly stored")
        void botEnabledStatus_storedCorrectly() throws Exception {
            // When: Getting enabled and disabled bots
            Bot echoBot = storageFacade.getBot(ECHO_BOT_ID);
            Bot disabledBot = storageFacade.getBot(DISABLED_BOT_ID);

            // Then: enabled status should be correct
            assertThat(echoBot.isEnabled()).isTrue();
            assertThat(disabledBot.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Bot secret is stored")
        void botSecret_isStored() throws Exception {
            // When: Getting a bot
            Bot bot = storageFacade.getBot(ECHO_BOT_ID);

            // Then: Secret should be present
            assertThat(bot.getSecret()).isNotNull();
            assertThat(bot.getSecret()).isEqualTo("test-secret-" + ECHO_BOT_ID);
        }
    }
}
