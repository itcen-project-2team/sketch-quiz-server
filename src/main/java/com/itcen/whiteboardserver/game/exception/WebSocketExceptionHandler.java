package com.itcen.whiteboardserver.game.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.security.Principal;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class WebSocketExceptionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    // 유효성 검사 예외 처리
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationException(MethodArgumentNotValidException ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 유효성 검사 예외 발생");
            return;
        }

        BindingResult result = ex.getBindingResult();
        String errorMessage = result.getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((message1, message2) -> message1 + ", " + message2)
                .orElse("유효성 검사 오류가 발생했습니다.");
        
        log.debug("유효성 검사 예외 발생: {}, 사용자: {}", errorMessage, principal.getName());
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("유효성 검사 오류", errorMessage)
        );
    }

    // RuntimeException 예외 처리
    @MessageExceptionHandler(RuntimeException.class)
    public void handleRuntimeException(RuntimeException ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 RuntimeException 발생: {}", ex.getMessage());
            return;
        }

        String errorMessage = ex.getMessage();
        log.error("RuntimeException 발생: {}, 사용자: {}", errorMessage, principal.getName(), ex);
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("실행 오류", errorMessage != null ? errorMessage : "알 수 없는 오류가 발생했습니다.")
        );
    }

    // 기타 모든 예외 처리
    @MessageExceptionHandler(Exception.class)
    public void handleException(Exception ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 예외 발생: {}", ex.getMessage());
            return;
        }
        
        log.error("예외 발생: {}, 사용자: {}", ex.getMessage(), principal.getName(), ex);
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("서버 오류", "서버에서 오류가 발생했습니다.")
        );
    }
    
    // 사용자 정의 예외 처리 메서드들
    @MessageExceptionHandler(RoomNotFoundException.class)
    public void handleRoomNotFoundException(RoomNotFoundException ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 RoomNotFoundException 발생: {}", ex.getMessage());
            return;
        }
        
        log.debug("RoomNotFoundException 발생: {}, 사용자: {}", ex.getMessage(), principal.getName());
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("방 오류", ex.getMessage())
        );
    }

    @MessageExceptionHandler(MemberNotFoundException.class)
    public void handleMemberNotFoundException(MemberNotFoundException ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 MemberNotFoundException 발생: {}", ex.getMessage());
            return;
        }
        
        log.debug("MemberNotFoundException 발생: {}, 사용자: {}", ex.getMessage(), principal.getName());
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("회원 오류", ex.getMessage())
        );
    }

    @MessageExceptionHandler(RoomJoinException.class)
    public void handleRoomJoinException(RoomJoinException ex, SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null) {
            log.warn("Principal이 없는 상태에서 RoomJoinException 발생: {}", ex.getMessage());
            return;
        }
        
        log.debug("RoomJoinException 발생: {}, 사용자: {}", ex.getMessage(), principal.getName());
        
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/errors",
                new ErrorResponse("방 참여 오류", ex.getMessage())
        );
    }
}