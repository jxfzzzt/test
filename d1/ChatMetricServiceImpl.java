package com.mindverse.odems.service;

import com.mindverse.odems.api.ChatMetricService;
import com.mindverse.odems.api.dto.content.ChatContent;
import com.mindverse.odems.api.dto.metric.*;
import com.mindverse.odems.dao.po.ChatContentDO;
import com.mindverse.odems.dao.po.ChatMetricDO;
import com.mindverse.odems.dao.po.ChatSessionDO;
import com.mindverse.odems.dao.mapper.ChatContentMapper;
import com.mindverse.odems.dao.mapper.ChatMetricMapper;
import com.mindverse.odems.dao.mapper.ChatSessionMapper;
import com.mindverse.odems.utils.MathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话的评价指标服务
 */

@Service
@Slf4j
public class ChatMetricServiceImpl implements ChatMetricService {

    @Autowired
    ChatContentMapper chatContentMapper;

    @Autowired
    ChatMetricMapper chatMetricMapper;

    @Autowired
    ChatSessionMapper chatSessionMapper;


    // 获得单个指标评价的数学统计量
    private ChatStatistic getChatStatistic(List<BigDecimal> data) {
        ChatStatistic chatStatistic = new ChatStatistic();
        chatStatistic.setRange(MathUtil.Range(data));
        chatStatistic.setMax(MathUtil.Max(data));
        chatStatistic.setVar(MathUtil.Variance(data));
        return chatStatistic;
    }

    // 获得metric全部的评分对应的数学统计量，并进行封装返回
     private SessionChatStatistic getSessionChatStatistic(List<ChatMetric> chatMetricList) {
        SessionChatStatistic sessionChatStatistic = new SessionChatStatistic();

        List<BigDecimal> fluency = new ArrayList<>();
        List<BigDecimal> context = new ArrayList<>();
        List<BigDecimal> diversity = new ArrayList<>();

        for(ChatMetric chatMetric : chatMetricList) {
            fluency.add(chatMetric.getFluency());
            context.add(chatMetric.getContext());
            diversity.add(chatMetric.getDiversity());
        }

        // 计算每个指标对应的数学统计量
        ChatStatistic fluencyStatistic = getChatStatistic(fluency);
        ChatStatistic contextStatistic = getChatStatistic(context);
        ChatStatistic diversityStatistic = getChatStatistic(diversity);

        // 对每一个指标的数学统计量进行封装
        sessionChatStatistic.setContextStatistic(contextStatistic);
        sessionChatStatistic.setFluencyStatistic(fluencyStatistic);
        sessionChatStatistic.setDiversityStatistic(diversityStatistic);

        return sessionChatStatistic;
    }

    /**
     * 通过sessionId获得改session内的对话内容，以及评分，评分的数学统计量
     * @param sessionId
     * @return SessionChatDialogue
     */
    @Override
    public SessionChatDialogue getSessionChatDialogue(String sessionId) {
       SessionChatDialogue sessionChatDialogue = new SessionChatDialogue();
       sessionChatDialogue.setSessionId(sessionId);

        ChatSessionDO chatSessionDO = chatSessionMapper.getChatSessionBySessionId(sessionId);

        // 如果查询的session信息为null，则直接返回
        if(chatSessionDO == null) return sessionChatDialogue;
        sessionChatDialogue.setMindId(chatSessionDO.getMindId());
        sessionChatDialogue.setUserId(chatSessionDO.getUserId());

        List<ChatContentDO> chatContentDOList = chatContentMapper.getChatContentBySessionId(sessionId);

        // 如果对话内容为空，也直接返回
        if(chatContentDOList == null || chatContentDOList.isEmpty()) return null;

        List<ChatContent> chatContentList = new ArrayList<>();
        List<ChatMetric> chatMetricList = new ArrayList<>();

        // 提取对话信息内容与产品对话对应的分数
        for(ChatContentDO chatContentDO : chatContentDOList) {

            // 如果是source是MIND，则还需要获得对应的分数
            if (chatContentDO.getSource().equals("MIND")) {
                ChatContentMetric chatContentMetric = new ChatContentMetric();
                chatContentMetric.setSource(chatContentDO.getSource());
                chatContentMetric.setContent(chatContentDO.getContent());

                ChatMetricDO chatMetricDO = chatMetricMapper.getChatMetricByContentId(chatContentDO.getId());

                if(chatMetricDO == null) {
                    // 说明产品的这句话没有评价,则只需要放入产品的对话内容，不需要set对于的评分
                    chatContentList.add(chatContentMetric);
                } else {
                    ChatMetric chatMetric = new ChatMetric(chatMetricDO);
                    chatContentMetric.setChatMetric(chatMetric);
                    chatContentList.add(chatContentMetric);
                    chatMetricList.add(chatMetric);
                }
            } else {
                // 如果source是User，则直接返回对话的内容即可，不需要获得对应的评分
                ChatContent chatContent = new ChatContent();
                chatContent.setContent(chatContentDO.getContent());
                chatContent.setSource(chatContentDO.getSource());
                chatContentList.add(chatContent);
            }
        }

        sessionChatDialogue.setChatContentList(chatContentList);
        sessionChatDialogue.setChatMetricList(chatMetricList);

        sessionChatDialogue.setSessionChatStatistic(getSessionChatStatistic(chatMetricList));

        return sessionChatDialogue;
    }
}
