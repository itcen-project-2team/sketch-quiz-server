package com.itcen.whiteboardserver.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TransactionTestInnerService {


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> publicMethod() {
        Map<String, Object> txInfo = new HashMap<>();

        // 3. public 메소드 트랜잭션 정보 캡처 및 로깅
        txInfo.put("inner_public_txName", TransactionSynchronizationManager.getCurrentTransactionName());
        log.info("=== InnerPublicMethod 트랜잭션 정보 ===");
        log.info("InnerPublicMethod 트랜잭션 이름: {}", txInfo.get("inner_public_txName"));

        return txInfo;
    }

}