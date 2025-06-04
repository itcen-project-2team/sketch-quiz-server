package com.itcen.whiteboardserver.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionTestService {

    private final TransactionTestInnerService innerService;

    @Transactional
    public Map<String, Object> publicMethod() {
        Map<String, Object> txInfo = new HashMap<>();

        // 1. public 메소드 트랜잭션 정보 캡처 및 로깅
        txInfo.put("public_txName", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("=== publicMethod 트랜잭션 정보 ===");
        log.info("publicMethod 트랜잭션 이름: {}", txInfo.get("public_txName"));

        // 2. private 메소드 호출 및 정보 병합
        txInfo.putAll(privateMethod());

        // 3. inner 클래스의 public 메소드 호출 및 정보 병합
        txInfo.putAll(innerService.publicMethod());

        return txInfo;
    }

    private Map<String, Object> privateMethod() {
        Map<String, Object> txInfo = new HashMap<>();

        // 3. private 메소드 트랜잭션 정보 캡처 및 로깅
        txInfo.put("private_txName", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("=== privateMethod 트랜잭션 정보 ===");
        log.info("privateMethod 트랜잭션 이름: {}", txInfo.get("private_txName"));

        return txInfo;
    }

    @Transactional
    public Map<String, Object> publicMethod2() {
        Map<String, Object> txInfo = new HashMap<>();

        // 1. public 메소드 트랜잭션 정보 캡처 및 로깅
        txInfo.put("public2_txName", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("=== public2Method 트랜잭션 정보 ===");
        log.info("public2Method 트랜잭션 이름: {}", txInfo.get("public2_txName"));


        return txInfo;
    }
}