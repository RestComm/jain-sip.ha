/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.ha.javax.sip.cache.infinispan;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.mobicents.ha.javax.sip.ClusteredSipStack;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

/**
 * Cache event listener which can be registered to any Infinispan cache 
 * in order to receive CacheEntryEvents. Now it is only used for get 
 * events when dialog is no longer available in the sip cache, thus it
 * also has to be removed from the remote sip stack as well.
 * 
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */

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
		
		clusteredSipStack.remoteDialogRemoval((String)event.getKey());
	}
	
}
