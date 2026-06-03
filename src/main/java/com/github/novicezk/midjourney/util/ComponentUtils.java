package com.github.novicezk.midjourney.util;

import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class ComponentUtils {
	private static final String CUSTOM_ID_KEY = "custom_id";

	public static boolean containsCustomId(Object value, String customId) {
		if (value instanceof Map<?, ?> map) {
			Object currentCustomId = map.get(CUSTOM_ID_KEY);
			if (customId.equals(currentCustomId)) {
				return true;
			}
			for (Object child : map.values()) {
				if (containsCustomId(child, customId)) {
					return true;
				}
			}
			return false;
		}
		if (value instanceof Iterable<?> iterable) {
			for (Object child : iterable) {
				if (containsCustomId(child, customId)) {
					return true;
				}
			}
		}
		return false;
	}
}
