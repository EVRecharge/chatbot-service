package com.evcharging.chatbotservice.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import com.evcharging.chatbotservice.dto.ChatMessageResponse;
import com.evcharging.chatbotservice.entity.ChatMessage;
import com.evcharging.chatbotservice.repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Answers general customer-support questions for EVRecharge India drivers and charging-station
 * providers, via the Claude API. Conversation history is persisted per (entityType, entityId) so
 * context carries across messages, bounded to the last {@code app.chatbot.max-history} turns to
 * keep each request's token cost predictable.
 */
@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private static final String SYSTEM_PROMPT = String.join("\n",
            "You are the support assistant for EVRecharge India, a marketplace app connecting EV",
            "drivers with charging-station providers across India.",
            "",
            "What the app does:",
            "- Drivers: search for nearby charging stations on a map, compare rates and charger",
            "  types, book a charging slot, save a payment method (card or UPI), and are charged",
            "  automatically once the provider confirms their booking. Prices are in Indian rupees.",
            "- Providers: register their charging station (location, rate per hour, charger type,",
            "  available slots), confirm or decline incoming booking requests, and link a payout",
            "  bank account to receive their share of each booking automatically.",
            "- Both roles manage their profile, view booking history, and manage saved payment",
            "  methods / payout accounts from their account pages.",
            "",
            "How to answer:",
            "- Be concise, friendly, and practical -- this is a support chat, not an essay.",
            "- Answer general \"how does X work\" / \"how do I do X\" questions about the app directly.",
            "- You do NOT have access to any specific user's real account data (their actual",
            "  bookings, payments, balances, or personal details) -- never invent specifics. If",
            "  asked about a specific booking or transaction, explain how to find it in the app",
            "  (My Bookings / Payment Methods / Payout Account) and suggest contacting human",
            "  support for anything you can't resolve generally.",
            "- If asked something unrelated to EV charging or this app, answer briefly if it's easy",
            "  and harmless, otherwise politely steer back to what you can help with here.",
            "- Never ask for or repeat back full card numbers, CVVs, passwords, or OTPs.");

    @Autowired
    private AnthropicClient anthropicClient;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Value("${app.anthropic.model}")
    private String model;

    @Value("${app.anthropic.max-tokens}")
    private long maxTokens;

    @Value("${app.chatbot.max-history}")
    private int maxHistory;

    public List<ChatMessageResponse> getHistory(String entityType, Long entityId) {
        return chatMessageRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(m -> new ChatMessageResponse(m.getRole(), m.getContent(), m.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageResponse sendMessage(String entityType, Long entityId, String userText) {
        ChatMessage userMessage = new ChatMessage();
        userMessage.setEntityType(entityType);
        userMessage.setEntityId(entityId);
        userMessage.setRole("user");
        userMessage.setContent(userText);
        chatMessageRepository.save(userMessage);

        List<ChatMessage> recentHistory = chatMessageRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, PageRequest.of(0, maxHistory));
        Collections.reverse(recentHistory);

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(SYSTEM_PROMPT)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build());

        for (ChatMessage turn : recentHistory) {
            if ("assistant".equals(turn.getRole())) {
                builder.addAssistantMessage(turn.getContent());
            } else {
                builder.addUserMessage(turn.getContent());
            }
        }

        String replyText;
        try {
            Message response = anthropicClient.messages().create(builder.build());
            replyText = extractText(response);
        } catch (RateLimitException e) {
            log.warn("Claude API rate limited: {}", e.getMessage());
            replyText = "I'm getting a lot of questions right now -- please try again in a moment.";
        } catch (AnthropicServiceException e) {
            log.error("Claude API error: {}", e.getMessage());
            replyText = "Sorry, I couldn't process that just now. Please try again shortly.";
        }

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setEntityType(entityType);
        assistantMessage.setEntityId(entityId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(replyText);
        chatMessageRepository.save(assistantMessage);

        return new ChatMessageResponse(assistantMessage.getRole(), assistantMessage.getContent(), assistantMessage.getCreatedAt());
    }

    @Transactional
    public void clearHistory(String entityType, Long entityId) {
        chatMessageRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
    }

    private String extractText(Message response) {
        StringBuilder sb = new StringBuilder();
        List<ContentBlock> blocks = response.content();
        for (ContentBlock block : blocks) {
            block.text().ifPresent(t -> sb.append(t.text()));
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? "I'm not sure how to answer that -- could you rephrase?" : text;
    }
}
