package com.tars.spotai.service;

import com.tars.spotai.dto.BlogPublishedMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费探店笔记发布事件，异步写入粉丝关注流。
 */
@Component
@ConditionalOnProperty(prefix = "spotai.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.mq.blog-published-topic}",
        consumerGroup = "spotai-blog-published-consumer"
)
public class BlogPublishedConsumer implements RocketMQListener<BlogPublishedMessage> {
    private final FeedService feedService;

    public BlogPublishedConsumer(FeedService feedService) {
        this.feedService = feedService;
    }

    @Override
    public void onMessage(BlogPublishedMessage message) {
        feedService.pushBlogToFollowers(message.getAuthorId(), message.getBlogId());
    }
}
