/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.internal.requests;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.AbstractWebhookClient;
import net.dv8tion.jda.internal.entities.ReceivedMessage;
import net.dv8tion.jda.internal.entities.channel.concrete.WebhookChannel;
import net.dv8tion.jda.internal.requests.restaction.AuditableRestActionImpl;
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageCreateActionImpl;
import net.dv8tion.jda.internal.requests.restaction.WebhookMessageEditActionImpl;
import net.dv8tion.jda.internal.utils.Checks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.function.Function;

public class IncomingWebhookClient extends AbstractWebhookClient<Message>
{
    public IncomingWebhookClient(long webhookId, String webhookToken, JDA api)
    {
        super(webhookId, webhookToken, api);
    }

    private String threadId;
    private IncomingWebhookClient(long webhookId, String webhookToken, JDA api, String threadId)
    {
        this(webhookId, webhookToken, api);
        this.threadId = threadId;
    }

    public IncomingWebhookClient withThreadId(String threadId)
    {
        return new IncomingWebhookClient(id, token, api, threadId);
    }

    @Override
    public WebhookMessageCreateActionImpl<Message> sendRequest()
    {
        Route.CompiledRoute route = Route.Webhooks.EXECUTE_WEBHOOK.compile(Long.toUnsignedString(id), token);
        route = route.withQueryParams("wait", "true");
        route = route.withQueryParams("thread_id", threadId);
        WebhookMessageCreateActionImpl<Message> action = new WebhookMessageCreateActionImpl<>(api, route, builder());
        action.run();
        return action;
    }

    @Override
    public WebhookMessageEditActionImpl<Message> editRequest(@Nonnull String messageId)
    {
        if (!"@original".equals(messageId))
            Checks.isSnowflake(messageId);
        Route.CompiledRoute route = Route.Webhooks.EXECUTE_WEBHOOK_EDIT.compile(Long.toUnsignedString(id), token, messageId);
        route = route.withQueryParams("wait", "true");
        route = route.withQueryParams("thread_id", threadId);
        WebhookMessageEditActionImpl<Message> action = new WebhookMessageEditActionImpl<>(api, route, builder());
        action.run();
        return action;
    }

    @Nonnull
    @Override
    public RestAction<Message> retrieveMessageById(@Nonnull String messageId)
    {
        if (!"@original".equals(messageId))
            Checks.isSnowflake(messageId);
        Route.CompiledRoute route = Route.Interactions.GET_MESSAGE.compile(Long.toUnsignedString(id), token, messageId);
        route = route.withQueryParams("thread_id", threadId);
        return new RestActionImpl<>(api, route, (response, request) -> builder().apply(response.getObject()));
    }

    @NotNull
    @Override
    public RestAction<Void> deleteMessageById(@NotNull String messageId)
    {
        if (!"@original".equals(messageId))
            Checks.isSnowflake(messageId);
        Route.CompiledRoute route = Route.Webhooks.EXECUTE_WEBHOOK_DELETE.compile(Long.toUnsignedString(id), token, messageId);
        route = route.withQueryParams("thread_id", threadId);
        return new AuditableRestActionImpl<>(api, route);
    }

    private Function<DataObject, Message> builder()
    {
        return (data) -> {
            JDAImpl jda = (JDAImpl) api;
            long channelId = data.getUnsignedLong("channel_id");
            MessageChannel channel = api.getChannelById(MessageChannel.class, channelId);
            if (channel == null)
                channel = new WebhookChannel(this, channelId);
            ReceivedMessage message = jda.getEntityBuilder().createMessageWithChannel(data, channel, false);
            message.withHook(this);
            return message;
        };
    }

}
