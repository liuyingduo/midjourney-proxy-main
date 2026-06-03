package com.github.novicezk.midjourney.wss.handle;

import cn.hutool.core.text.CharSequenceUtil;
import com.github.novicezk.midjourney.Constants;
import com.github.novicezk.midjourney.enums.MessageType;
import com.github.novicezk.midjourney.enums.TaskAction;
import com.github.novicezk.midjourney.enums.TaskStatus;
import com.github.novicezk.midjourney.loadbalancer.DiscordInstance;
import com.github.novicezk.midjourney.support.Task;
import com.github.novicezk.midjourney.support.TaskCondition;
import com.github.novicezk.midjourney.util.ContentParseData;
import com.github.novicezk.midjourney.util.ConvertUtils;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ComponentSuccessHandler extends MessageHandler {

	@Override
	public int order() {
		return 110;
	}

	@Override
	public void handle(DiscordInstance instance, MessageType messageType, DataObject message) {
		if (!MessageType.CREATE.equals(messageType) && !MessageType.UPDATE.equals(messageType)) {
			return;
		}
		if (!hasImage(message)) {
			return;
		}
		Task task = findComponentTask(instance, messageType, message);
		if (task == null) {
			return;
		}
		message.put(Constants.MJ_MESSAGE_HANDLED, true);
		ContentParseData parseData = ConvertUtils.parseContent(getMessageContent(message));
		if (parseData != null) {
			task.setProperty(Constants.TASK_PROPERTY_FINAL_PROMPT, parseData.getPrompt());
		}
		String imageUrl = getImageUrl(message);
		task.setImageUrl(imageUrl);
		task.setProperty(Constants.TASK_PROPERTY_MESSAGE_HASH, this.discordHelper.getMessageHash(imageUrl));
		finishTask(task, message);
		task.awake();
	}

	private Task findComponentTask(DiscordInstance instance, MessageType messageType, DataObject message) {
		Task task = findComponentTaskByNonce(instance, message);
		if (task != null) {
			return task;
		}
		if (MessageType.UPDATE.equals(messageType)) {
			return findComponentTaskByProgressMessageId(instance, message.getString("id"));
		}
		String referenceMessageId = getReferenceMessageId(message);
		if (CharSequenceUtil.isBlank(referenceMessageId)) {
			referenceMessageId = getReferencedMessageId(message);
		}
		if (CharSequenceUtil.isBlank(referenceMessageId)) {
			return null;
		}
		task = findComponentTaskByProgressMessageId(instance, referenceMessageId);
		return task == null ? findComponentTaskByReferencedMessageId(instance, referenceMessageId) : task;
	}

	private Task findComponentTaskByNonce(DiscordInstance instance, DataObject message) {
		Task task = instance.getRunningTaskByNonce(getMessageNonce(message));
		return isRunningComponentTask(task) ? task : null;
	}

	private Task findComponentTaskByProgressMessageId(DiscordInstance instance, String messageId) {
		TaskCondition condition = componentTaskCondition().setProgressMessageId(messageId);
		return instance.findRunningTask(condition).findFirst().orElse(null);
	}

	private Task findComponentTaskByReferencedMessageId(DiscordInstance instance, String messageId) {
		TaskCondition condition = componentTaskCondition();
		return instance.findRunningTask(condition)
				.filter(task -> messageId.equals(task.getProperty(Constants.TASK_PROPERTY_REFERENCED_MESSAGE_ID, String.class)))
				.findFirst().orElse(null);
	}

	private TaskCondition componentTaskCondition() {
		return new TaskCondition().setActionSet(Set.of(TaskAction.INPUT))
				.setStatusSet(Set.of(TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED));
	}

	private boolean isRunningComponentTask(Task task) {
		return task != null && TaskAction.INPUT.equals(task.getAction())
				&& Set.of(TaskStatus.IN_PROGRESS, TaskStatus.SUBMITTED).contains(task.getStatus());
	}

	private String getReferencedMessageId(DataObject message) {
		DataObject referencedMessage = message.optObject("referenced_message").orElse(DataObject.empty());
		return referencedMessage.getString("id", "");
	}
}
