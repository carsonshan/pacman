package com.taobao.top.pacman.statements;

import java.util.Map;

import org.junit.Ignore;

import com.taobao.top.pacman.Activity;
import com.taobao.top.pacman.InArgument;

//FIXME fix while test
@Ignore
public class WhileTest extends StatementTestBase {
	@Override
	protected Activity createActivity() {
		While while1 = new While();
		while1.Body = new WriteLine("");
		while1.Condition = new InArgument();
		return null;
	}

	@Override
	protected Map<String, Object> createInputs() {
		return null;
	}
}
