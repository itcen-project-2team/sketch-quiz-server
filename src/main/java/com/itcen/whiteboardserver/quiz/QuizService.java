package com.itcen.whiteboardserver.quiz;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class QuizService {
    private Map<QuizCategory, List<String>> keywords = new EnumMap<>(QuizCategory.class);

    @PostConstruct
    public void init() throws IOException {
        for (QuizCategory category : QuizCategory.values()) {
            keywords.put(category, new ArrayList<>());
        }

        try (InputStream is = getClass().getResourceAsStream("/data/keywords.csv");
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

            while (true) {
                String line = br.readLine();

                if (line == null) {
                    break;
                }

                String[] parts = line.split(",");
                String keyword = parts[0];
                QuizCategory category = QuizCategory.from(parts[1]);

                keywords.get(category).add(keyword);
            }
        }
    }

    public List<String> getQuizWords(int participantCnt) {
        for (List<String> quizWords : keywords.values()) {
            Collections.shuffle(quizWords);
        }

        List<String> randomKeywords = new ArrayList<>();
        for (List<String> quizWords : keywords.values()) {
            randomKeywords.addAll(quizWords.subList(0, participantCnt));
        }

        Collections.shuffle(randomKeywords);
        return randomKeywords;
    }

    public QuizCategory getQuizType(String quizWord) {
        for (QuizCategory category : QuizCategory.values()) {
            if (keywords.get(category).contains(quizWord)) {
                return category;
            }
        }

        throw new RuntimeException("카테고리에 해당하는 단어가 아닙니다.");
    }
}
