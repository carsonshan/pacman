package pacman.runtime;

import pacman.ActivityExecutor;
import pacman.ActivityInstance;
import pacman.ActivityInstance.ActivityInstanceState;

public abstract class ActivityExecutionWorkItem extends WorkItem {
	private boolean skipAbort;

	protected ActivityExecutionWorkItem() {
	}

	protected ActivityExecutionWorkItem(ActivityInstance activityInstance) {
		super(activityInstance);
	}

	@Override
	public boolean isValid() {
		return this.getActivityInstance().getState() == ActivityInstanceState.Executing;
	}

	@Override
	protected void clear() {
		super.clear();
		this.skipAbort = false;
	}

	protected void setExceptionToPropagateWithoutAbort(Exception exception) {
		this.setExceptionToPropagate(exception);
		this.skipAbort = true;
	}

	@Override
	public void postProcess(ActivityExecutor executor) {
		// NOTE 4.1 check exception, abort activityInstance if exception not handled in faultCallback
		if (this.getExceptionToPropagate() != null && !this.skipAbort) {
			// NOTE 4.1.1 abort activityInstance and its tree ,set faulted
			executor.abortActivityInstance(this.getActivityInstance(), this.getExceptionToPropagate());
			return;
		}

		// NOTE 4.2 update activityInstance state and check weather activity completed
		if (!this.getActivityInstance().updateState(executor))
			return;

		// NOTE 4.3 complete activityInstance
		Exception exception = executor.completeActivityInstance(this.getActivityInstance());
		if (exception != null)
			this.setExceptionToPropagate(exception);
	}

}
