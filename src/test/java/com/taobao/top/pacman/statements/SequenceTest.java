package com.taobao.top.pacman.statements;

import java.util.Map;

import com.taobao.top.pacman.Activity;

public class SequenceTest extends StatementTestBase {
	protected Activity createActivity() {
		Sequence sequence = new Sequence();
		sequence.getActivities().add(new WriteLine());
		sequence.getActivities().add(new WriteLine());
		return sequence;
	}

	@Override
	protected Map<String, Object> createInputs() {
		return null;
	}
}