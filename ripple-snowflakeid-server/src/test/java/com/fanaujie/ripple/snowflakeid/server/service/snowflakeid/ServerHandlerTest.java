package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdRequest;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerHandlerTest {

    @Mock private ChannelHandlerContext ctx;

    @Mock private SnowflakeIdGenerator mockGenerator;

    private ServerHandler serverHandler;

    @BeforeEach
    void setUp() {
        serverHandler = new ServerHandler(mockGenerator);
    }

    @Test
    void testChannelRead0_ReturnsResponseWithCorrectRequestId() throws Exception {
        String requestId = "test-request-123";
        long generatedId = 12345678L;

        GenerateIdRequest request =
                GenerateIdRequest.newBuilder().setRequestId(requestId).build();

        when(mockGenerator.nextId()).thenReturn(generatedId);

        serverHandler.channelRead0(ctx, request);

        ArgumentCaptor<GenerateIdResponse> responseCaptor =
                ArgumentCaptor.forClass(GenerateIdResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        GenerateIdResponse response = responseCaptor.getValue();
        assertEquals(requestId, response.getRequestId());
        assertEquals(generatedId, response.getId());
    }

    @Test
    void testChannelRead0_CallsGeneratorNextId() throws Exception {
        GenerateIdRequest request =
                GenerateIdRequest.newBuilder().setRequestId("test-123").build();

        when(mockGenerator.nextId()).thenReturn(1L);

        serverHandler.channelRead0(ctx, request);

        verify(mockGenerator, times(1)).nextId();
    }

    @Test
    void testChannelRead0_WritesAndFlushesResponse() throws Exception {
        GenerateIdRequest request =
                GenerateIdRequest.newBuilder().setRequestId("test-456").build();

        when(mockGenerator.nextId()).thenReturn(999L);

        serverHandler.channelRead0(ctx, request);

        verify(ctx, times(1)).writeAndFlush(any(GenerateIdResponse.class));
    }

    @Test
    void testHandlerWithRealGenerator() throws Exception {
        SnowflakeIdGenerator realGenerator = new SnowflakeIdGenerator(1);
        ServerHandler handler = new ServerHandler(realGenerator);

        String requestId = "real-test-789";
        GenerateIdRequest request =
                GenerateIdRequest.newBuilder().setRequestId(requestId).build();

        handler.channelRead0(ctx, request);

        ArgumentCaptor<GenerateIdResponse> responseCaptor =
                ArgumentCaptor.forClass(GenerateIdResponse.class);
        verify(ctx).writeAndFlush(responseCaptor.capture());

        GenerateIdResponse response = responseCaptor.getValue();
        assertEquals(requestId, response.getRequestId());
        assertTrue(response.getId() > 0, "Generated ID should be positive");
    }

    @Test
    void testMultipleRequests() throws Exception {
        when(mockGenerator.nextId()).thenReturn(100L, 200L, 300L);

        GenerateIdRequest request1 =
                GenerateIdRequest.newBuilder().setRequestId("req-1").build();
        GenerateIdRequest request2 =
                GenerateIdRequest.newBuilder().setRequestId("req-2").build();
        GenerateIdRequest request3 =
                GenerateIdRequest.newBuilder().setRequestId("req-3").build();

        serverHandler.channelRead0(ctx, request1);
        serverHandler.channelRead0(ctx, request2);
        serverHandler.channelRead0(ctx, request3);

        ArgumentCaptor<GenerateIdResponse> responseCaptor =
                ArgumentCaptor.forClass(GenerateIdResponse.class);
        verify(ctx, times(3)).writeAndFlush(responseCaptor.capture());

        var responses = responseCaptor.getAllValues();
        assertEquals("req-1", responses.get(0).getRequestId());
        assertEquals(100L, responses.get(0).getId());
        assertEquals("req-2", responses.get(1).getRequestId());
        assertEquals(200L, responses.get(1).getId());
        assertEquals("req-3", responses.get(2).getRequestId());
        assertEquals(300L, responses.get(2).getId());
    }
}
