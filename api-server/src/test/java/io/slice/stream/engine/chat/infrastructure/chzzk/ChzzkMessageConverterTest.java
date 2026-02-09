package io.slice.stream.engine.chat.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkMessageConverterTest {

    private ChzzkMessageConverter chzzkMessageConverter;
    private final JsonMapper jsonMapper = new JsonMapper();

    @BeforeEach
    void setUp() {
        chzzkMessageConverter = new ChzzkMessageConverter(jsonMapper);
    }

    @Test
    void 일반_채팅_메시지를_ChatMessage로_정상적으로_변환한다() throws JsonProcessingException {
        // given
        String jsonMessage = """
            {
                "cmd": 93101,
                "bdy": [
                    {
                        "profile": "{\\"userIdHash\\":\\"user123\\",\\"nickname\\":\\"testUser\\",\\"profileImageUrl\\":\\"url-to-image\\"}",
                        "msg": "안녕하세요",
                        "msgTime": 1672531200000
                    }
                ]
            }
            """;
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode);

        // then
        assertThat(chatMessages).hasSize(1);
        ChatMessage message = chatMessages.get(0);
        assertThat(message.messageType()).isEqualTo(MessageType.TEXT);
        assertThat(message.author().id()).isEqualTo("user123");
        assertThat(message.author().nickname()).isEqualTo("testUser");
        assertThat(message.message()).isEqualTo("안녕하세요");
    }

    @Test
    void 도네이션_메시지를_ChatMessage로_정상적으로_변환한다() throws JsonProcessingException {
        // given
        String jsonMessage = """
            {
                "cmd": 93102,
                "bdy": [
                    {
                        "profile": "{\\"userIdHash\\":\\"donator456\\",\\"nickname\\":\\"generousFan\\",\\"profileImageUrl\\":\\"url-to-fan-image\\"}",
                        "msg": "화이팅!",
                        "msgTime": 1672531200000
                    }
                ]
            }
            """;
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode);

        // then
        assertThat(chatMessages).hasSize(1);
        ChatMessage message = chatMessages.get(0);
        assertThat(message.messageType()).isEqualTo(MessageType.DONATION);
        assertThat(message.author().id()).isEqualTo("donator456");
        assertThat(message.author().nickname()).isEqualTo("generousFan");
    }

    @Test
    void 지원하지_않는_CMD_타입은_빈_리스트를_반환한다() throws JsonProcessingException {
        // given
        String jsonMessage = "{\"cmd\": 10000, \"bdy\": []}";
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode);

        // then
        assertThat(chatMessages).isEmpty();
    }
    
    @Test
    void 여러_메시지가_포함된_경우_모두_변환한다() throws JsonProcessingException {
        // given
        String jsonMessage = """
            {
                "cmd": 93101,
                "bdy": [
                    {
                        "profile": "{\\"userIdHash\\":\\"user1\\",\\"nickname\\":\\"userA\\",\\"profileImageUrl\\":\\"url1\\"}",
                        "msg": "첫번째 메시지",
                        "msgTime": 1672531200000
                    },
                    {
                        "profile": "{\\"userIdHash\\":\\"user2\\",\\"nickname\\":\\"userB\\",\\"profileImageUrl\\":\\"url2\\"}",
                        "msg": "두번째 메시지",
                        "msgTime": 1672531201000
                    }
                ]
            }
            """;
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode);

        // then
        assertThat(chatMessages).hasSize(2);
        assertThat(chatMessages.get(0).message()).isEqualTo("첫번째 메시지");
        assertThat(chatMessages.get(1).message()).isEqualTo("두번째 메시지");
    }

    @Test
    void body가_null이거나_배열이_아닌_경우_빈_리스트를_반환한다() throws JsonProcessingException {
        // given
        String jsonWithNullBody = "{\"cmd\": 93101, \"bdy\": null}";
        String jsonWithNonArrayBody = "{\"cmd\": 93101, \"bdy\": {}}";

        // when
        List<ChatMessage> result1 = chzzkMessageConverter.convert(jsonMapper.readTree(jsonWithNullBody));
        List<ChatMessage> result2 = chzzkMessageConverter.convert(jsonMapper.readTree(jsonWithNonArrayBody));

        // then
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }

    @Test
    void profile_json이_잘못된_형식일_경우_빈_리스트를_반환한다() throws JsonProcessingException {
        // given
        String jsonMessage = """
            {
                "cmd": 93101,
                "bdy": [
                    {
                        "profile": "this-is-not-a-json",
                        "msg": "안녕하세요",
                        "msgTime": 1672531200000
                    }
                ]
            }
            """;
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> convert = chzzkMessageConverter.convert(rootNode);

        // then
        assertThat(convert).isEmpty();
    }

}
