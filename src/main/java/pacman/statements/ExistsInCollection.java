package pacman.statements;

import pacman.ActivityMetadata;
import pacman.CodeActivityContext;
import pacman.CodeActivityWithResult;
import pacman.InArgument;
import pacman.RuntimeArgument;
import pacman.RuntimeArgument.ArgumentDirection;

public final class ExistsInCollection extends CodeActivityWithResult {
	public InArgument Collection;
	public InArgument Item;
	
	@Override
	protected void cacheMetadata(ActivityMetadata metadata) throws Exception {
		metadata.bindAndAddArgument(this.Collection, new RuntimeArgument("Collection", java.util.Collection.class, ArgumentDirection.In));
		metadata.bindAndAddArgument(this.Item, new RuntimeArgument("Item", Object.class, ArgumentDirection.In));
	}
	
	@SuppressWarnings({ "rawtypes" })
	@Override
	protected Object execute(CodeActivityContext context) throws Exception {
		return ((java.util.Collection) this.Collection.get(context)).contains(this.Item.get(context));
	}
	
}
