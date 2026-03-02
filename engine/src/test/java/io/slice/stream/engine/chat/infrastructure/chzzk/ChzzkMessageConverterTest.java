package io.slice.stream.engine.chat.infrastructure.chzzk;

import static org.assertj.core.api.Assertions.assertThat;

import io.slice.stream.engine.chat.domain.model.ChatMessage;
import io.slice.stream.engine.chat.domain.model.MessageType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ChzzkMessageConverterTest {

    private ChzzkMessageConverter chzzkMessageConverter;
    private final JsonMapper jsonMapper = new JsonMapper();
    private final String TARGET_CHANNEL_ID = "7ce8032370ac5121dcabce7bad375ced";

    @BeforeEach
    void setUp() {
        chzzkMessageConverter = new ChzzkMessageConverter(jsonMapper);
    }

    @Test
    void 일반_채팅_메시지를_ChatMessage로_정상적으로_변환한다() throws Exception {
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
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(chatMessages).hasSize(1);
        ChatMessage message = chatMessages.get(0);
        assertThat(message.messageType()).isEqualTo(MessageType.TEXT);
        assertThat(message.author().id()).isEqualTo("user123");
        assertThat(message.author().nickname()).isEqualTo("testUser");
        assertThat(message.message()).isEqualTo("안녕하세요");
        assertThat(message.streamId()).isEqualTo(TARGET_CHANNEL_ID); // streamId 정상 할당 확인
    }

    @Test
    void 도네이션_메시지를_ChatMessage로_정상적으로_변환한다() throws Exception {
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
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(chatMessages).hasSize(1);
        ChatMessage message = chatMessages.get(0);
        assertThat(message.messageType()).isEqualTo(MessageType.DONATION);
        assertThat(message.author().id()).isEqualTo("donator456");
        assertThat(message.author().nickname()).isEqualTo("generousFan");
        assertThat(message.streamId()).isEqualTo(TARGET_CHANNEL_ID);
    }

    @Test
    void profile이_null이거나_없는_경우_익명_유저로_정상_변환한다() throws Exception {
        // given
        String jsonMessage = """
            {
                "cmd": 93102,
                "bdy": [
                    {
                        "profile": null,
                        "msg": "익명 후원입니다",
                        "msgTime": 1672531200000
                    }
                ]
            }
            """;
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(chatMessages).hasSize(1);
        ChatMessage message = chatMessages.get(0);
        assertThat(message.author().id()).isEqualTo("anonymous");
        assertThat(message.author().nickname()).isEqualTo("익명");
        assertThat(message.message()).isEqualTo("익명 후원입니다");
        assertThat(message.streamId()).isEqualTo(TARGET_CHANNEL_ID);
    }

    @Test
    void 지원하지_않는_CMD_타입은_빈_리스트를_반환한다() throws Exception {
        // given
        String jsonMessage = "{\"cmd\": 10000, \"bdy\": []}";
        JsonNode rootNode = jsonMapper.readTree(jsonMessage);

        // when
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(chatMessages).isEmpty();
    }

    @Test
    void 여러_메시지가_포함된_경우_모두_변환한다() throws Exception {
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
        List<ChatMessage> chatMessages = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(chatMessages).hasSize(2);
        assertThat(chatMessages.get(0).message()).isEqualTo("첫번째 메시지");
        assertThat(chatMessages.get(0).streamId()).isEqualTo(TARGET_CHANNEL_ID);
        assertThat(chatMessages.get(1).message()).isEqualTo("두번째 메시지");
        assertThat(chatMessages.get(1).streamId()).isEqualTo(TARGET_CHANNEL_ID);
    }

    @Test
    void body가_null이거나_배열이_아닌_경우_빈_리스트를_반환한다() throws Exception {
        // given
        String jsonWithNullBody = "{\"cmd\": 93101, \"bdy\": null}";
        String jsonWithNonArrayBody = "{\"cmd\": 93101, \"bdy\": {}}";

        // when
        List<ChatMessage> result1 = chzzkMessageConverter.convert(jsonMapper.readTree(jsonWithNullBody), TARGET_CHANNEL_ID);
        List<ChatMessage> result2 = chzzkMessageConverter.convert(jsonMapper.readTree(jsonWithNonArrayBody), TARGET_CHANNEL_ID);

        // then
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();
    }

    @Test
    void profile_json이_잘못된_형식일_경우_해당_메시지는_제외하고_반환한다() throws Exception {
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
        List<ChatMessage> convert = chzzkMessageConverter.convert(rootNode, TARGET_CHANNEL_ID);

        // then
        assertThat(convert).isEmpty(); // 파싱 실패 시 null 반환 후 filter로 걸러짐
    }
}
