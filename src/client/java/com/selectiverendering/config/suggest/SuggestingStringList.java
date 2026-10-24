package com.selectiverendering.config.suggest;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.options.ConfigStringList;

/** 带注册名补全的字符串名单 */
public final class SuggestingStringList extends ConfigStringList {
	private final SuggestKind kind;

	public SuggestingStringList(String name, SuggestKind kind) {
		super(name, ImmutableList.of());

		this.kind = kind;
	}

	public SuggestKind kind() {
		return this.kind;
	}

	@Override
	public SuggestingStringList apply(String translationPrefix) {
		super.apply(translationPrefix);
		return this;
	}
}
