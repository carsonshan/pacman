package pacman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pacman.runtime.*;

public class ActivityInstance {
	private int id;
	private Activity activity;
	private ActivityInstance parent;
	private List<ActivityInstance> children;

	private CompletionBookmark completionBookmark;
	private FaultBookmark faultBookmark;
	private LocationEnvironment environment;
	private boolean noSymbols;

	// instance state
	private ActivityInstanceState state;
	// execution state
	private SubState subState;

	private boolean isCancellationRequested;
	private boolean isInitializationIncomplete;
	private boolean isPerformingDefaultCancelation;

	private int busyCount;

	public ActivityInstance(Activity activity) {
		this.activity = activity;
		this.state = ActivityInstanceState.Executing;
		this.subState = SubState.Created;
	}

	protected boolean initialize(
			ActivityInstance parent,
			int id,
			LocationEnvironment parentEnvironment,
			ActivityExecutor executor) {
		this.parent = parent;
		this.id = id;

		if (this.parent != null) {
			if (parentEnvironment == null)
				parentEnvironment = this.parent.getEnvironment();
		}

		int symbolCount = this.getActivity().getSymbolCount();

		if (symbolCount > 0) {
			this.environment = new LocationEnvironment(this.getActivity(), parentEnvironment, symbolCount);
			this.subState = SubState.ResolvingArguments;
			return true;
		}

		if (parentEnvironment == null) {
			this.environment = new LocationEnvironment(this.getActivity());
			return false;
		}

		this.noSymbols = true;
		this.environment = parentEnvironment;
		return false;
	}

	protected int getId() {
		return this.id;
	}

	protected void setCompletionBookmark(CompletionBookmark completionBookmark) {
		this.completionBookmark = completionBookmark;
	}

	protected CompletionBookmark getCompletionBookmark() {
		return this.completionBookmark;
	}

	protected void setFaultBookmakr(FaultBookmark faultBookmark) {
		this.faultBookmark = faultBookmark;
	}

	protected FaultBookmark getFaultBookmark() {
		return this.faultBookmark;
	}

	protected LocationEnvironment getEnvironment() {
		return this.environment;
	}

	protected boolean isEnvironmentOwner() {
		return !this.noSymbols;
	}

	protected Iterable<ActivityInstance> getChildren() {
		return this.children;
	}

	protected void addChild(ActivityInstance child) {
		if (this.children == null)
			this.children = new ArrayList<ActivityInstance>();
		this.children.add(child);
	}

	private void removeChild(ActivityInstance child) {
		this.children.remove(child);
	}

	public ActivityInstanceState getState() {
		return this.state;
	}

	public void markCanceled() {
		this.subState = SubState.Canceling;
	}

	protected void markExecuted() {
		this.subState = SubState.Executing;
	}

	public boolean isCancellationRequested() {
		return this.isCancellationRequested;
	}

	protected void setCancellationRequested() {
		Helper.assertFalse(this.isCancellationRequested);
		this.isCancellationRequested = true;
	}

	protected boolean isCompleted() {
		return this.getState() != ActivityInstanceState.Executing;
	}

	public ActivityInstance getParent() {
		return this.parent;
	}

	public Activity getActivity() {
		return this.activity;
	}

	public void markAsComplete() {
		if (this.parent != null)
			this.parent.removeChild(this);
	}

	protected void baseCancel(NativeActivityContext context) {
		Helper.assertTrue(this.isCancellationRequested());
		this.isPerformingDefaultCancelation = true;
		this.cancelChildren(context);
	}

	protected void cancelChildren(NativeActivityContext context) {
		if (this.children != null && this.children.size() > 0)
			for (ActivityInstance child : this.children)
				context.cancelChild(child);
	}

	public boolean isPerformingDefaultCancelation() {
		return this.isPerformingDefaultCancelation;
	}

	public boolean hasNotExecuted() {
		return this.subState != SubState.Executing;
	}

	public void setInitializationIncomplete() {
		this.isInitializationIncomplete = true;
	}

	public void setInitialized() {
		Helper.assertNotEquals(SubState.Initialized, this.subState);
		this.subState = SubState.Initialized;
	}

	public void finalize(boolean fault) {
		if (fault) {
			this.tryCancelParent();
			this.state = ActivityInstanceState.Faulted;
		}

		// TODO can trace more type of trace record here

		if (Trace.isEnabled())
			Trace.traceActivityCompleted(this);
	}

	public void setCanceled() {
		Helper.assertFalse(this.isCompleted());
		this.tryCancelParent();
		this.state = ActivityInstanceState.Canceled;
	}

	public void setClosed() {
		Helper.assertFalse(this.isCompleted());
		this.state = ActivityInstanceState.Closed;
	}

	public void execute(ActivityExecutor executor, BookmarkManager bookmarkManager) throws Exception {
		Helper.assertFalse(this.isInitializationIncomplete, "init incomplete");
		this.markExecuted();
		// NOTE 3.2 internal execute activity
		this.getActivity().internalExecute(this, executor, bookmarkManager);
	}

	public void cancel(ActivityExecutor executor, BookmarkManager bookmarkManager) {
		this.getActivity().internalCancel(this, executor, bookmarkManager);
	}

	// only abort itself tree, not to it's parent
	protected void abort(ActivityExecutor executor,
			BookmarkManager bookmarkManager,
			Exception reason,
			boolean isTerminate) {
		ActivityInstance root = this;
		ActivityInstance current = this;

		// depth first and avoid recursion
		while (current.hasChildren())
			current = current.children.get(0);

		while (true) {
			// maybe arugment resovle error or branch not executed
			if (current.hasNotExecuted())
				current.getActivity().internalAbort(current, executor, reason);

			// transaction can be rollback here
			// ...

			// maybe root
			executor.handleRootCompletion(current);
			// must remove self from parent
			current.markAsComplete();
			current.state = ActivityInstanceState.Faulted;
			current.finalize(false);

			if (current == root)
				break;
			current = current.getParent();
			while (current.hasChildren())
				current = current.children.get(0);

		}
	}

	public boolean resolveArguments(ActivityExecutor executor,
			Map<String, Object> argumentValues,
			Location resultLocation,
			int startIndex) {
		boolean sync = true;

		List<RuntimeArgument> runtimeArguments = this.getActivity().getRuntimeArguments();
		int argumentCount = runtimeArguments.size();

		if (argumentCount == 0)
			return sync;

		ActivityContext resolutionContext = new ActivityContext(this, executor);

		for (int i = startIndex; i < argumentCount; i++) {
			RuntimeArgument argument = runtimeArguments.get(i);

			Object value = null;
			if (argumentValues != null)
				value = argumentValues.get(argument.getName());

			// try fast-path
			if (!argument.tryPopuateValue(this.getEnvironment(), this, resolutionContext, value, resultLocation)) {
				sync = false;
				int next = i + 1;
				// if have one more argument, should resume argument resolution after current expression scheduled
				if (next < runtimeArguments.size()) {
					ResolveNextArgumentWorkItem workItem = executor.ResolveNextArgumentWorkItemPool.acquire();
					// NOTE looks confused that still pass resultLocation,
					// but only used when resultArgument and the activtiy must be activityWithResult
					workItem.initialize(this, next, argumentValues, resultLocation);
					executor.scheduleItem(workItem);
				}
				// schedule argument expression
				executor.scheduleExpression(
						argument.getBoundArgument().getExpression(),
						this,
						resolutionContext.getEnvironment(),
						// FIXME should not direct use real Location, use Referencelocation
						this.getEnvironment().getLocation(argument.getId()));
				// must break, different from variables
				break;
			}
		}

		if (sync && startIndex == 0)
			this.subState = SubState.ResolvingVariables;

		return sync;
	}

	public boolean resolveVariables(ActivityExecutor executor) {
		this.subState = SubState.ResolvingVariables;
		boolean sync = true;

		List<Variable> implementationVariables = this.getActivity().getImplementationVariables();
		List<Variable> runtimevaVariables = this.getActivity().getRuntimeVariables();
		ActivityContext context = new ActivityContext(this, executor);

		for (int i = 0; i < implementationVariables.size(); i++) {
			Variable variable = implementationVariables.get(i);

			if (!variable.tryPopulateLocation(executor, context)) {
				Helper.assertNotNull(variable.getDefault());
				executor.scheduleExpression(
						variable.getDefault(),
						this,
						this.getEnvironment(),
						this.getEnvironment().getLocation(variable.getId()));
				sync = false;
			}
		}

		for (int i = 0; i < runtimevaVariables.size(); i++) {
			Variable variable = runtimevaVariables.get(i);

			// try fast-path
			if (!variable.tryPopulateLocation(executor, context)) {
				Helper.assertNotNull(variable.getDefault());
				executor.scheduleExpression(
						variable.getDefault(),
						this,
						this.getEnvironment(),
						// FIXME should not direct use real location, use Referencelocation
						this.getEnvironment().getLocation(variable.getId()));
				sync = false;
			}
		}
		return sync;
	}

	public boolean updateState(ActivityExecutor executor) {
		boolean activityCompleted = false;

		if (this.hasNotExecuted()) {
			if (this.isCancellationRequested()) {
				if (this.hasChildren()) {
					for (ActivityInstance child : this.getChildren()) {
						Helper.assertTrue(
								child.getState() == ActivityInstanceState.Executing,
								"should only have children if they're still executing");
						executor.cancelActivity(child);
					}
				} else {
					this.setCanceled();
					activityCompleted = true;
				}
			} else if (!this.hasPendingWork()) {
				boolean scheduleBody = false;
				if (this.subState == SubState.ResolvingArguments) {
					// NOTE 4.2.1 finish async resolution of arguments now and continue variables resolution
					// outArgument in async resolution will render to target location here
					this.getEnvironment().collapseTemporaryResolutionLocations();
					this.subState = SubState.ResolvingVariables;
					scheduleBody = this.resolveVariables(executor);
				} else if (this.subState == SubState.ResolvingVariables)
					scheduleBody = true;

				if (scheduleBody)
					executor.scheduleBody(this, false, null, null);
			}

			Helper.assertTrue(
					this.hasPendingWork() || activityCompleted,
					"should have scheduled work pending if we're not complete");
		} else if (!this.hasPendingWork()) {
			activityCompleted = true;
			if (this.subState == SubState.Canceling)
				this.setCanceled();
			else
				this.setClosed();
			// transaction maybe check here
		} else if (this.isPerformingDefaultCancelation()) {
			// TODO impl bookmark cleanup while cancel
			// if (this.onlyHasOutstandingBookmarks()) {
			// executor.getBookmarkManager().removeAll(this);
			// RemoveAllBookmarks(executor.RawBookmarkScopeManager, executor.RawBookmarkManager);
			// this.markCanceled();
			// Helper.assertFalse(this.hasPendingWork(), "Shouldn't have pending work here.");
			// this.setCanceled();
			// activityCompleted = true;
			// }
		}

		return activityCompleted;
	}

	public void incrementBusyCount() {
		this.busyCount++;
	}

	public void decrementBusyCount() {
		Helper.assertTrue(this.busyCount > 0);
		this.busyCount--;
	}

	private void tryCancelParent() {
		if (this.getParent() != null && this.getParent().isPerformingDefaultCancelation()) {
			this.getParent().markCanceled();
		}
	}

	protected static ActivityInstance createCanceledActivityInstance(Activity activity) {
		ActivityInstance instance = new ActivityInstance(activity);
		instance.state = ActivityInstanceState.Canceled;
		return instance;
	}

	private boolean hasPendingWork() {
		return this.hasChildren() || this.busyCount > 0;
	}

	private boolean hasChildren() {
		return this.children != null && this.children.size() > 0;
	}

	@Override
	public String toString() {
		return String.format("instance#%s|%s|%s",
				this.getId(),
				this.getActivity().getClass().getSimpleName(),
				this.getActivity().getDisplayName());
	}

	public enum ActivityInstanceState {
		Executing,
		Closed,
		Canceling,
		Canceled,
		Faulted
	}

	enum SubState {
		Executing,
		Created,
		ResolvingArguments,
		ResolvingVariables,
		Initialized,
		Canceling
	}
}
