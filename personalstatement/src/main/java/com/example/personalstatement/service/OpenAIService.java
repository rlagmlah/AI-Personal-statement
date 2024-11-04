package com.example.personalstatement.service;

import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class OpenAIService {
    private final AssistantApi APIClient;
    private final Data.Assistant assistant;
    private final Data.Thread thread;

    //openAI 의 API 키와 Assistant API 키를 받음
    public OpenAIService(@Value("${spring.ai.openai.api-key}") String auth,
                               @Value("${spring.ai.openai.assistant-api-key}") String apiKey) {
        if (auth == null || auth.isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key must be set.");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Assistant API key must be set.");
        }
        this.APIClient = new AssistantApi(auth); //API 클라이언트 생성
        this.assistant = this.APIClient.retrieveAssistant(apiKey); //어시스트를 받아옴
        this.thread = this.APIClient.createThread(new Data.ThreadRequest()); //스레드 생성. 스레드는 바로 삭제되기 때문에 ID 를 저장해둬야 한다.
        System.out.println("thread id : " + this.thread.id());
    }

    //메세지를 보내고 응답을 받는 코드
    private Data.DataList<Data.Message> RunAndGetResponse(Data.Thread thread, String messages) {
        this.APIClient.createMessage( new Data.MessageRequest( //스레드에 메세지를 보내기
                        Data.Role.user, messages),
                thread.id()
        );
        Data.Run run = this.APIClient.createRun( //API 실행 (엔터치기)
                thread.id(),
                new Data.RunRequest(this.assistant.id())
        );
        //응답이 완료될 때 까지 대기하기
        System.out.println("now Running... id : " + run.id());
        while (this.APIClient.retrieveRun(thread.id(), run.id()).status() != Data.Run.Status.completed) {
            try {
                java.lang.Thread.sleep(500);
            } catch (InterruptedException e) {}
        }
        System.out.println("run Complete! id : " + run.id());
        //메세지 목록을 리턴 (스레드의 모든 메세지를 리턴함)
        return this.APIClient.listMessages(new Data.ListRequest(), thread.id());
    }

    //메세지에서 마지막 메세지만 String 으로 추출하는 코드
    private String getAssistantResponse(Data.DataList<Data.Message> messages) {
        // API 의 응답만 필터링
        List<Data.Message> assistantMessages = messages.data().stream()
                .filter(msg -> msg.role() == Data.Role.assistant).toList();

        // 글자만 뽑아내기
        List<String> resault = new ArrayList<>();
        for (Data.Message message  : assistantMessages) {
            for (Data.Content content : message.content()) {
                if (content.type() == Data.Content.Type.text) {
                    resault.add(content.text().value());
                }
            }
        }
        //마지막 응답만 추출해서 String 으로 바꾸고 리턴
        return String.join("\n ", resault.get(0));
    }

    //위에 것을 한번에 하는 코드
    public String SendMassageAndGetResponse(String message) {
        Data.DataList<Data.Message> messages = RunAndGetResponse(this.thread, message);
        return getAssistantResponse(messages);
    }

    // Intro(소개) 생성 메서드
    public String generateIntro (HttpSession session, String intro, String tone) {
        String realname = (String) session.getAttribute("realname");
        String prompt = String.format(
                "# 자기 소개\n"
                + "## 이름\n %s"
                + "## 소개\n %s"
                + "다음과 같은 어조로 수정해 주세요. %s", realname, intro, tone
        );
        return SendMassageAndGetResponse(prompt);
    }

    // Qualifications(자격증) 생성 메서드
    //자격증은 특별한 수정이 필요 없는 단순 정보인 경우가 많아서 프롬포트를 약간 수정
    public String generateQualifications (String qualifications, String tone) {
        return SendMassageAndGetResponse( "# 보유 자격증\n" + qualifications + "\n제공된 양식을 파괴하지 않고, 어조, 형태, 문구 등에 대한 피드백만을 제공해 주세요.");
    }

    // Company 생성 매서드
    public String generateMotivationStatement (String motivationStatement, String tone) {
        String prompt = String.format(
                "# 지원 동기\n %s"
                + "다음과 같은 어조로 수정해 주세요. %s", motivationStatement, tone
        );
        return SendMassageAndGetResponse(prompt);
    }

    public String generateExperience (String experience, String tone) {
        String prompt = String.format(
                "# 경력 사항\n %s"
                + "다음과 같은 어조로 수정해 주세요. %s", experience, tone
        );
        return SendMassageAndGetResponse(prompt);
    }

    //완료 시에는 이미 Thread 에 모든 데이터가 있기 떄문에 작성을 완료하라는 플래그만 세워주면 된다
    public String generateFinalPersonStatement(HttpSession session) {
        return SendMassageAndGetResponse("_complete_writing_personal_statement_");
    }
}
