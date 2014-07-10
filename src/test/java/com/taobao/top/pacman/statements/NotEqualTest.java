package com.taobao.top.pacman.statements;

import static org.junit.Assert.*;

import java.util.Map;

import com.taobao.top.pacman.Activity;
import com.taobao.top.pacman.InArgument;
import com.taobao.top.pacman.testsuite.StatementTestBase;

public class NotEqualTest extends StatementTestBase {
	
	@Override
	protected Activity createActivity() {
		NotEqual notEqual = new NotEqual();
		notEqual.Left = new InArgument("hi");
		notEqual.Right = new InArgument("hi");
		return notEqual;
	}
	
	@Override
	protected Map<String, Object> createInputs() {
		return null;
	}
	
	@Override
	protected void assertOutputs(Map<String, Object> outputs) {
		assertFalse((Boolean) outputs.get("Result"));
	}
}
