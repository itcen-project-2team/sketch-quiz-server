package com.itcen.whiteboardserver.room;

import com.itcen.whiteboardserver.game.dto.response.RoomInfoResponse;
import com.itcen.whiteboardserver.game.service.RoomService;
import com.itcen.whiteboardserver.member.enums.MemberRole;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
public class RestRoomControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RoomService roomService() {
            return mock(RoomService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Test
    @DisplayName("방 생성 테스트")
    @WithMockUser
    public void testCreateRoom() throws Exception {
        // given
        String testEmail = "test@example.com";
        RoomInfoResponse mockResponse = new RoomInfoResponse(new ArrayList<>(), 12345L);

        // CustomPrincipal 모의 객체 생성
        CustomPrincipal mockPrincipal = new CustomPrincipal(
                1L, testEmail, "테스트유저", "password", Set.of(MemberRole.MEMBER), null);

        // 서비스 동작 모의 설정
        when(roomService.createRoom(anyString())).thenReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/room")
                        .with(SecurityMockMvcRequestPostProcessors.user(mockPrincipal))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomCode").value(12345L));
    }
}