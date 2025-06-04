package com.itcen.whiteboardserver.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
public class TransactionTestServiceTest {

    @Autowired
    private TransactionTestService service;

    @Test
    void testTransactionPropagation() {
        // When: 서비스 메소드 실행
        Map<String, Object> txInfo = service.publicMethod();
        Map<String, Object> txInfo2 = service.publicMethod2();

        // Then: 트랜잭션 정보 검증

        // 트랜잭션 이름 검증 (동일한 트랜잭션)
        String publicTxName = (String) txInfo.get("public_txName");
        String privateTxName = (String) txInfo.get("private_txName");
        assertEquals(publicTxName, privateTxName,
                "public과 private 메소드의 트랜잭션 이름 동일해야 함");

        // 트랜잭션 이름 검증 (다른 트랙잭션)
        String public2TxName = (String) txInfo2.get("public2_txName");
        assertNotEquals(publicTxName, public2TxName,
                "public과 public2 메소드의 트랜잭션 이름 동일하지 않아야 함");
    }

}