package com.selectiverendering.render.hiding;

import java.lang.reflect.Method;

/** 通过 Iris API 判断当前是否正在使用光影包，没装 Iris 时恒为 false。 */
public final class ShaderPackState {
	private static Method inUse;
	private static Object api;
	private static boolean resolved;

	private ShaderPackState() {
	}

	public static boolean active() {
		if (!resolved) {
			resolve();
		}

		if (inUse == null) {
			return false;
		}

		try {
			return (boolean) inUse.invoke(api);
		}
		catch (ReflectiveOperationException | RuntimeException exception) {
			return false;
		}
	}

	private static synchronized void resolve() {
		if (resolved) {
			return;
		}

		try {
			Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			api = type.getMethod("getInstance").invoke(null);
			inUse = type.getMethod("isShaderPackInUse");
		}
		catch (ReflectiveOperationException | LinkageError exception) {
			inUse = null;
		}

		resolved = true;
	}
}
