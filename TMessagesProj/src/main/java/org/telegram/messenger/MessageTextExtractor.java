/*
 * Copyright (C) 2024 Nnngram
 * 消息文本提取器
 * 统一提取所有消息类型的文本内容
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

//wd 消息文本提取器
//wd 统一提取所有消息类型的文本内容，用于AI广告过滤分析
public class MessageTextExtractor {

    private static volatile MessageTextExtractor instance;

    //wd 私有构造函数
    private MessageTextExtractor() {
    }

    //wd 获取单例实例
    public static MessageTextExtractor getInstance() {
        if (instance == null) {
            synchronized (MessageTextExtractor.class) {
                if (instance == null) {
                    instance = new MessageTextExtractor();
                }
            }
        }
        return instance;
    }

    //wd 提取消息的所有文本内容
    //wd 合并所有可能的文本字段，用于广告过滤分析
    @NonNull
    public String extractAllText(@NonNull MessageObject messageObject) {
        List<String> textParts = new ArrayList<>();

        //wd 1. 提取主要消息文本
        String messageText = extractMessageText(messageObject);
        if (!TextUtils.isEmpty(messageText)) {
            textParts.add(messageText);
        }

        //wd 2. 提取媒体标题
        String caption = extractCaption(messageObject);
        if (!TextUtils.isEmpty(caption)) {
            textParts.add(caption);
        }

        //wd 3. 提取文件信息
        String fileInfo = extractFileInfo(messageObject);
        if (!TextUtils.isEmpty(fileInfo)) {
            textParts.add(fileInfo);
        }

        //wd 4. 提取链接预览信息
        String webpageInfo = extractWebpageInfo(messageObject);
        if (!TextUtils.isEmpty(webpageInfo)) {
            textParts.add(webpageInfo);
        }

        //wd 5. 提取投票信息
        String pollInfo = extractPollInfo(messageObject);
        if (!TextUtils.isEmpty(pollInfo)) {
            textParts.add(pollInfo);
        }

        //wd 6. 提取按钮文本
        String buttonText = extractButtonText(messageObject);
        if (!TextUtils.isEmpty(buttonText)) {
            textParts.add(buttonText);
        }

        //wd 7. 提取游戏信息
        String gameInfo = extractGameInfo(messageObject);
        if (!TextUtils.isEmpty(gameInfo)) {
            textParts.add(gameInfo);
        }

        //wd 8. 提取位置信息
        String venueInfo = extractVenueInfo(messageObject);
        if (!TextUtils.isEmpty(venueInfo)) {
            textParts.add(venueInfo);
        }

        //wd 合并所有文本，用空格分隔
        return String.join(" ", textParts);
    }

    //wd 提取主要消息文本
    private String extractMessageText(@NonNull MessageObject messageObject) {
        if (!TextUtils.isEmpty(messageObject.messageText)) {
            return messageObject.messageText.toString();
        }
        if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.message)) {
            return messageObject.messageOwner.message;
        }
        return "";
    }

    //wd 提取媒体标题
    private String extractCaption(@NonNull MessageObject messageObject) {
        if (!TextUtils.isEmpty(messageObject.caption)) {
            return messageObject.caption.toString();
        }
        if (messageObject.messageOwner != null && messageObject.messageOwner.media != null) {
            TLRPC.MessageMedia media = messageObject.messageOwner.media;
            if (!TextUtils.isEmpty(media.captionLegacy)) {
                return media.captionLegacy;
            }
        }
        return "";
    }

    //wd 提取文件信息
    private String extractFileInfo(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.media == null) {
            return "";
        }

        TLRPC.MessageMedia media = messageObject.messageOwner.media;
        StringBuilder fileInfo = new StringBuilder();

        //wd 文档文件名
        if (media.document != null) {
            if (!TextUtils.isEmpty(media.document.file_name)) {
                fileInfo.append(media.document.file_name).append(" ");
            }
            //wd 文档mime类型
            if (!TextUtils.isEmpty(media.document.mime_type)) {
                fileInfo.append(media.document.mime_type).append(" ");
            }
        }

        //wd 音频标题和表演者 (从document.attributes中获取)
        if (media.document != null && media.document.attributes != null) {
            for (TLRPC.DocumentAttribute attribute : media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    if (!TextUtils.isEmpty(attribute.title)) {
                        fileInfo.append(attribute.title).append(" ");
                    }
                    if (!TextUtils.isEmpty(attribute.performer)) {
                        fileInfo.append(attribute.performer).append(" ");
                    }
                }
            }
        }

        return fileInfo.toString().trim();
    }

    //wd 提取链接预览信息
    private String extractWebpageInfo(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.media == null) {
            return "";
        }

        TLRPC.MessageMedia media = messageObject.messageOwner.media;
        if (media.webpage == null) {
            return "";
        }

        TLRPC.WebPage webpage = media.webpage;
        StringBuilder webpageInfo = new StringBuilder();

        //wd 网页标题
        if (!TextUtils.isEmpty(webpage.title)) {
            webpageInfo.append(webpage.title).append(" ");
        }

        //wd 网页描述
        if (!TextUtils.isEmpty(webpage.description)) {
            webpageInfo.append(webpage.description).append(" ");
        }

        //wd 网站名称
        if (!TextUtils.isEmpty(webpage.site_name)) {
            webpageInfo.append(webpage.site_name).append(" ");
        }

        //wd 作者
        if (!TextUtils.isEmpty(webpage.author)) {
            webpageInfo.append(webpage.author).append(" ");
        }

        return webpageInfo.toString().trim();
    }

    //wd 提取投票信息
    private String extractPollInfo(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.media == null) {
            return "";
        }

        TLRPC.MessageMedia media = messageObject.messageOwner.media;
        if (!(media instanceof TLRPC.TL_messageMediaPoll)) {
            return "";
        }

        TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) media;
        if (mediaPoll.poll == null) {
            return "";
        }

        TLRPC.Poll poll = mediaPoll.poll;
        StringBuilder pollInfo = new StringBuilder();

        //wd 投票问题 (TL_textWithEntities类型)
        if (poll.question != null && !TextUtils.isEmpty(poll.question.text)) {
            pollInfo.append(poll.question.text).append(" ");
        }

        //wd 投票选项
        if (poll.answers != null) {
            for (TLRPC.PollAnswer answer : poll.answers) {
                if (answer.text != null && !TextUtils.isEmpty(answer.text.text)) {
                    pollInfo.append(answer.text.text).append(" ");
                }
            }
        }

        return pollInfo.toString().trim();
    }

    //wd 提取按钮文本
    private String extractButtonText(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.reply_markup == null) {
            return "";
        }

        TLRPC.ReplyMarkup markup = messageObject.messageOwner.reply_markup;
        if (markup.rows == null) {
            return "";
        }

        StringBuilder buttonText = new StringBuilder();

        for (TLRPC.TL_keyboardButtonRow row : markup.rows) {
            if (row.buttons == null) continue;

            for (TLRPC.KeyboardButton button : row.buttons) {
                if (!TextUtils.isEmpty(button.text)) {
                    buttonText.append(button.text).append(" ");
                }

                //wd URL按钮的URL也提取（可能包含广告链接）
                if (button instanceof TLRPC.TL_keyboardButtonUrl) {
                    TLRPC.TL_keyboardButtonUrl urlButton = (TLRPC.TL_keyboardButtonUrl) button;
                    if (!TextUtils.isEmpty(urlButton.url)) {
                        buttonText.append(urlButton.url).append(" ");
                    }
                }
            }
        }

        return buttonText.toString().trim();
    }

    //wd 提取游戏信息
    private String extractGameInfo(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.media == null) {
            return "";
        }

        TLRPC.MessageMedia media = messageObject.messageOwner.media;
        if (media.game == null) {
            return "";
        }

        TLRPC.TL_game game = media.game;
        StringBuilder gameInfo = new StringBuilder();

        //wd 游戏标题
        if (!TextUtils.isEmpty(game.title)) {
            gameInfo.append(game.title).append(" ");
        }

        //wd 游戏描述
        if (!TextUtils.isEmpty(game.description)) {
            gameInfo.append(game.description).append(" ");
        }

        return gameInfo.toString().trim();
    }

    //wd 提取位置/场所信息
    private String extractVenueInfo(@NonNull MessageObject messageObject) {
        if (messageObject.messageOwner == null || messageObject.messageOwner.media == null) {
            return "";
        }

        TLRPC.MessageMedia media = messageObject.messageOwner.media;
        StringBuilder venueInfo = new StringBuilder();

        //wd 场所信息 (直接使用MessageMedia基类的字段)
        if (!TextUtils.isEmpty(media.title)) {
            venueInfo.append(media.title).append(" ");
        }
        if (!TextUtils.isEmpty(media.address)) {
            venueInfo.append(media.address).append(" ");
        }
        if (!TextUtils.isEmpty(media.provider)) {
            venueInfo.append(media.provider).append(" ");
        }

        return venueInfo.toString().trim();
    }

    //wd 获取消息摘要（用于日志和调试）
    @NonNull
    public String getMessageSummary(@NonNull MessageObject messageObject) {
        String allText = extractAllText(messageObject);
        if (TextUtils.isEmpty(allText)) {
            return "[Empty Message]";
        }

        //wd 截断显示
        String trimmed = allText.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }
        return trimmed.substring(0, 100) + "...";
    }

    //wd 检查消息是否包含文本内容
    public boolean hasTextContent(@NonNull MessageObject messageObject) {
        return !TextUtils.isEmpty(extractAllText(messageObject));
    }
}