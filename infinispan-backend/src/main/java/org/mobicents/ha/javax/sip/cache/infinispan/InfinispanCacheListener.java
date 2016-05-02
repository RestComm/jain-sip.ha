package org.mobicents.ha.javax.sip.cache.infinispan;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.mobicents.ha.javax.sip.ClusteredSipStack;


import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

@Listener
public class InfinispanCacheListener {

	private static StackLogger clusteredlogger = CommonLogger.getLogger(InfinispanCacheListener.class);

	private ClusteredSipStack clusteredSipStack;
	
	
	public InfinispanCacheListener(ClusteredSipStack clusteredSipStack) {
		this.clusteredSipStack = clusteredSipStack;
	}
	
	@CacheEntryRemoved
	public void cacheEntryRemovedHandler(CacheEntryRemovedEvent<?, ?> event){
		
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("sipStack " + clusteredSipStack + 
					" entry removed : " + event.getKey() + " - " + event.getValue());
		}		
		
	}
	
}
