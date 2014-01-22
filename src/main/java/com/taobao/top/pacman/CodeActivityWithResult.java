package com.taobao.top.pacman;

import com.taobao.top.pacman.runtime.BookmarkManager;

public abstract class CodeActivityWithResult extends ActivityWithResult {
	protected abstract Object execute(CodeActivityContext context);

	@Override
	protected final void internalExecute(
			ActivityInstance instance, 
			ActivityExecutor executor, 
			BookmarkManager bookmarkManager) {
		CodeActivityContext context = executor.CodeActivityContextPool.acquire();
		try {
			context.initialize(instance, executor);
			this.getResult().set(context, this.execute(context));
		} finally {
			context.dispose();
			executor.CodeActivityContextPool.release(context);
		}
	}

	@Override
	protected final void internalCacheMetadataExceptResult() {
		super.internalCacheMetadataExceptResult();
	}
}
