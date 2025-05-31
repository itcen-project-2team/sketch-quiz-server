package com.itcen.whiteboardserver.game.controller;

import com.itcen.whiteboardserver.game.dto.request.RoomJoinRequest;
import com.itcen.whiteboardserver.game.dto.response.RoomResponse;
import com.itcen.whiteboardserver.game.service.RoomService;
import com.itcen.whiteboardserver.security.principal.CustomPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // REST API 엔드포인트 - 방 생성
    @ResponseBody
    @PostMapping("/api/room")
    public ResponseEntity<RoomResponse> createRoom(@AuthenticationPrincipal CustomPrincipal principal) {
        return ResponseEntity.ok(roomService.createRoom(principal.getEmail()));
    }

    // WebSocket 엔드포인트 - 방 참여
    @MessageMapping("/room/join")
    public void joinRoom(@Valid RoomJoinRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String memberEmail = headerAccessor.getUser().getName();
        roomService.joinRoom(request, memberEmail);
    }

    // WebSocket 엔드포인트 - 방 떠나기
    @MessageMapping("/room/leave")
    public void leaveRoom(SimpMessageHeaderAccessor headerAccessor) {
        String memberEmail = headerAccessor.getUser().getName();
        roomService.leaveRoom(memberEmail);
    }

}