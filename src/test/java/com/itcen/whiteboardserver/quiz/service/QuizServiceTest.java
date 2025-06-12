package com.itcen.whiteboardserver.quiz.service;


import com.itcen.whiteboardserver.quiz.QuizCategory;
import com.itcen.whiteboardserver.quiz.QuizService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class QuizServiceTest {
    static QuizService quizService = new QuizService();

    @BeforeAll
    static void init() {
        try {
            quizService.init();
        } catch (IOException e) {
            Assertions.fail("fail read data.csv file");
        }
    }

    @Test
    void 랜덤_문제_테스트() {
        int participantCnt = 3;
        List<String> keywords = quizService.getQuizWords(participantCnt);

        int itcenCnt = 0;
        int techCnt = 0;
        int commonCnt = 0;

        for (String keyword : keywords) {
            QuizCategory category = quizService.getQuizType(keyword);

            switch (category) {
                case TECH -> techCnt++;
                case ITCEN -> itcenCnt++;
                case COMMON -> commonCnt++;
                default -> throw new RuntimeException("카테고리 처리가 다 되어있지 앖습니다.");
            }
        }

        Assertions.assertEquals(itcenCnt, participantCnt);
        Assertions.assertEquals(techCnt, participantCnt);
        Assertions.assertEquals(commonCnt, participantCnt);
    }
}
