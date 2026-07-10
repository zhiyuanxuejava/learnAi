package org.zhiyuan.demo01.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.zhiyuan.demo01.service.ChatClientFactory;
import org.zhiyuan.demo01.tools.DateTimeTools;
import org.zhiyuan.demo01.tools.mcp.LoggingMcpToolCallbackProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
public class MyController {

    private final ChatClientFactory chatClientFactory;

    private final DateTimeTools dateTimeTools;

    private final boolean thinkingVisible;

    private final LoggingMcpToolCallbackProvider loggingMcpToolCallbackProvider;

    public MyController(ChatClientFactory chatClientFactory,
                        DateTimeTools dateTimeTools,
                        @Value("${app.ai.thinking.visible:false}") boolean thinkingVisible,
                        LoggingMcpToolCallbackProvider loggingMcpToolCallbackProvider) {
        this.chatClientFactory = chatClientFactory;
        this.dateTimeTools = dateTimeTools;
        this.thinkingVisible = thinkingVisible;
        this.loggingMcpToolCallbackProvider = loggingMcpToolCallbackProvider;
    }

    /**
     * 普通问答：一次性返回
     */
    @GetMapping(value = "/ai", produces = "text/plain;charset=UTF-8")
    public String getAnswer(@RequestParam("question") String question,
                            @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * 流式问答：SSE，直接页面展示
     * text/html
     */
    @GetMapping(value = "/ai/stream01", produces = "text/html;charset=UTF-8")
    public Flux<String> getAnswerStream01(@RequestParam("question") String question,
                                          @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient.prompt()
                .user(question)
                .stream()
                .content();
    }


    /**
     * 流式问答：SSE  返回data: input。 text/event-stream
     */
    @GetMapping(value = "/ai/stream02", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream02(@RequestParam("question") String question,
                                                           @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient.prompt()
                .user(question)
                .stream()
                .chatResponse()
                .transform(this::toSseWithThinking);
    }


    /**
     * 流式问答：SSE
     * 使用模板 ： 一套代码对应所有场景 ，并且设置系统提示词
     */
    @GetMapping(value = "/ai/stream03", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream03(@RequestParam("question") String question,
                                                           @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        // 提示词模板
        PromptTemplate promptTemplate = new PromptTemplate("介绍一下{question}");
        Prompt prompt = promptTemplate.create(Map.of("question", question));
        return chatClient
                .prompt(prompt) // 使用模板
                .system("你是一个问答助手") //系统提示词 如果这里指定了系统提示词，就会覆盖AiConfiguration 配置的默认系统提示词
                .stream()  //流式输出
                .chatResponse()
                .transform(this::toSseWithThinking);
    }

    /**
     * 设置用户提示词和系统提示词，通过链式写法简写
     */
    @GetMapping(value = "/ai/stream04", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream04(@RequestParam("question") String question,
                                                           @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient
                .prompt()
                .system("你是一个专业的书评助手") //系统提示词 注意，此处设置的系统提示词，会覆盖AiConfiguration 设置的默认系统提示词
                .user(u -> u.text("请给我推荐三本关于{question}的书籍，并且给我列出特点以及对比").param("question", question))
                .stream()  //流式输出
                .chatResponse()
                .transform(this::toSseWithThinking);
    }

    /**
     * 对用户问题 加修饰
     */
    @GetMapping(value = "/ai/stream06", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream06(@RequestParam("question") String question,
                                                           @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient
                .prompt()
//                .system("") //系统提示词
                .user(u -> u.text("回答{question}，并且总结").param("question", question))
                .stream()  //流式输出
                .chatResponse()
                .transform(this::toSseWithThinking);
    }

    private Flux<ServerSentEvent<String>> toSseWithThinking(Flux<ChatResponse> responseFlux) {
        AtomicBoolean thinkingOpened = new AtomicBoolean(false);
        AtomicBoolean thinkingClosed = new AtomicBoolean(false);
        AtomicReference<String> thinkingSeen = new AtomicReference<>("");
        AtomicBoolean contentThinkingOpened = new AtomicBoolean(false);
        AtomicReference<String> contentPending = new AtomicReference<>("");

        return responseFlux
                .concatMap(response -> {
                    Generation generation = response.getResult();
                    if (generation == null || generation.getOutput() == null) {
                        return Flux.<String>empty();
                    }

                    AssistantMessage output = generation.getOutput();
                    List<String> chunks = new ArrayList<>();

                    String thinking = thinkingVisible ? thinkingDelta(extractThinking(generation, output), thinkingSeen) : "";
                    if (hasValue(thinking) && (thinkingOpened.get() || hasText(thinking))) {
                        if (thinkingOpened.compareAndSet(false, true)) {
                            chunks.add("<think>");
                        }
                        chunks.add(thinking);
                    }

                    String content = output.getText();
                    if (!thinkingVisible) {
                        content = removeThinkContent(content, contentThinkingOpened, contentPending, false);
                    }
                    if (hasValue(content)) {
                        if (thinkingOpened.get() && thinkingClosed.compareAndSet(false, true)) {
                            chunks.add("</think>\n\n");
                        }
                        chunks.add(content);
                    }

                    return Flux.fromIterable(chunks);
                })
                .concatWith(Mono.defer(() -> {
                    if (!thinkingVisible) {
                        String remainingContent = removeThinkContent("", contentThinkingOpened, contentPending, true);
                        if (hasValue(remainingContent)) {
                            return Mono.just(remainingContent);
                        }
                    }
                    if (thinkingOpened.get() && thinkingClosed.compareAndSet(false, true)) {
                        return Mono.just("</think>");
                    }
                    return Mono.empty();
                }))
                .map(content -> ServerSentEvent.builder(encodeSseContent(content)).build())
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.builder(encodeSseContent("模型调用失败：" + e.getMessage())).build()
                ));
    }

    private String encodeSseContent(String content) {
        return URLEncoder.encode(content, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String extractThinking(Generation generation, AssistantMessage output) {
        Object thinking = generation.getMetadata().get("thinking");
        if (thinking == null) {
            thinking = output.getMetadata().get("thinking");
        }
        return thinking == null ? "" : thinking.toString();
    }

    private String removeThinkContent(String chunk,
                                      AtomicBoolean inThinking,
                                      AtomicReference<String> pendingRef,
                                      boolean flush) {
        String content = pendingRef.getAndSet("") + (chunk == null ? "" : chunk);
        if (content.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < content.length()) {
            if (inThinking.get()) {
                int closeIndex = indexOfIgnoreCase(content, "</think>", index);
                if (closeIndex < 0) {
                    if (!flush) {
                        pendingRef.set(possibleTagTail(content, "</think>"));
                    }
                    return result.toString();
                }
                inThinking.set(false);
                index = closeIndex + "</think>".length();
                continue;
            }

            int openIndex = indexOfIgnoreCase(content, "<think>", index);
            if (openIndex < 0) {
                String remaining = content.substring(index);
                if (!flush) {
                    String pending = possibleTagTail(remaining, "<think>");
                    int outputLength = remaining.length() - pending.length();
                    result.append(remaining, 0, outputLength);
                    pendingRef.set(pending);
                } else {
                    result.append(remaining);
                }
                return result.toString();
            }

            result.append(content, index, openIndex);
            inThinking.set(true);
            index = openIndex + "<think>".length();
        }

        return result.toString();
    }

    private int indexOfIgnoreCase(String value, String search, int fromIndex) {
        return value.toLowerCase().indexOf(search.toLowerCase(), fromIndex);
    }

    private String possibleTagTail(String value, String tag) {
        String lowerValue = value.toLowerCase();
        String lowerTag = tag.toLowerCase();
        int maxLength = Math.min(value.length(), tag.length() - 1);
        for (int length = maxLength; length > 0; length--) {
            String tail = lowerValue.substring(lowerValue.length() - length);
            if (lowerTag.startsWith(tail)) {
                return value.substring(value.length() - length);
            }
        }
        return "";
    }

    private String thinkingDelta(String latest, AtomicReference<String> seenRef) {
        if (!hasValue(latest)) {
            return "";
        }

        String seen = seenRef.get();
        if (latest.startsWith(seen)) {
            seenRef.set(latest);
            return latest.substring(seen.length());
        }

        seenRef.set(seen + latest);
        return latest;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }




    /**
     *
     * 结构化输出 得到主题和书名
     * 使用java 16+的 Record 特性，编译器会自动生成构造器，equals/hashCode/toString等方法。
     *
     * @param topic
     * @param books
     */
    public record TopicBook(
            String topic,
            List<String> books
    ) {
    }


    /**
     * 结构化输出 ，返回自定义的结构化数据，返回对象
     */
    @GetMapping(value = "/ai/stream07", produces = "text/event-stream;charset=UTF-8")
    public String getAnswerStream07(@RequestParam("question") String question,
                                  @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        TopicBook topicBook = chatClient
                .prompt()
                .system("你是一个专业的书评助手") //系统提示词 注意，此处设置的系统提示词，会覆盖AiConfiguration 设置的默认系统提示词
                .user(u -> u.text("请给我推荐三本关于{question}的书籍，并且给我列出特点以及对比").param("question", question))
                //诸塞输出
                .call()
                //结构化输出
                .entity(TopicBook.class);
        System.out.println(topicBook);

        return topicBook.toString();
    }


    /**
     * 结构化输出，得到某本书评价和评分
     * @param reviewerName
     * @param rating
     * @param comment
     */
    public record BookReview(
            String reviewerName, //评价人
            String rating, //评分
            String comment //评价
    ) {
    }


    /**
     * 结构化输出，得到某本书的评价和评分
     * 返回数组
     */
    @GetMapping(value = "/ai/stream08", produces = "text/event-stream;charset=UTF-8")
    public String getAnswerStream08(@RequestParam("question") String question,
                                  @RequestParam(defaultValue = "ollama") String provider) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        List<BookReview> bookReviews = chatClient.prompt() //创建prompt对象，用于构建聊天请求
                .user(u -> u.text("请给{question}书籍三条评价信息").param("question", question))
                //诸塞输出
                .call()
                //结构化输出
                .entity(new ParameterizedTypeReference<List<BookReview>>() {
                });
        System.out.println(bookReviews);

        return "ok";
    }


    /**
     * 带记忆的会话，设置记忆的Advisor，如果内部设置了记忆Advisor，再请求的时候，必须填写ConversationId
     */
    @GetMapping(value = "/ai/stream09",  produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream09(@RequestParam("question") String question,
                                                         @RequestParam(defaultValue = "ollama") String provider,
                                                         @RequestParam(value = "convId", defaultValue = "1") String convId) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .stream()
                .chatResponse()
                .transform(this::toSseWithThinking);
    }

    /**
     * 带记忆的会话 + 工具调用 + MCP
     */
    @GetMapping(value = "/ai/stream11",  produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> getAnswerStream11(@RequestParam("question") String question,
                                                         @RequestParam(defaultValue = "ollama") String provider,
                                                         @RequestParam(value = "convId", defaultValue = "1") String convId) {
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        return chatClient.prompt()
                .user(question)
                .tools(dateTimeTools)  //添加本地时间工具
                .tools(loggingMcpToolCallbackProvider)  //添加带日志能力的 MCP 工具回调
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .stream()
                .chatResponse()
                .transform(this::toSseWithThinking);
    }

    /**
     * 测试多模态能力：文字 + 上传图片 + Tool + MCP + Memory + SSE
     */
    @PostMapping(value = "/ai/stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> getAnswerStream(@RequestParam("question") String question,
                                                         @RequestParam(defaultValue = "ollama") String provider,
                                                         @RequestParam(value = "convId", defaultValue = "1") String convId,
                                                         @RequestPart(value = "image", required = false) MultipartFile image) {
        //创建ChatClient对象
        ChatClient chatClient = chatClientFactory.getChatClient(provider);
        //创建prompt对象，用于构建聊天请求
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();

       //动态构造 发给用户的信息
        if (image != null && !image.isEmpty()) {
            //如果图片不为空，将图片结合问题添加到user中
            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException(
                        "不支持的文件类型：" + contentType
                );
            }
            MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
            requestSpec = requestSpec.user(u -> u
                    .text(question) //用户问题
                    .media(mimeType, image.getResource())  //上传的图片资源
            );
        } else {
            requestSpec = requestSpec.user(question);
        }

        return requestSpec
                .tools(dateTimeTools) //添加本地时间工具
                .tools(loggingMcpToolCallbackProvider)//添加带日志能力的 MCP 工具回调
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId)) //设置会话id，添加记忆功能
                .stream()
                .chatResponse()
                .transform(this::toSseWithThinking);
    }
}
