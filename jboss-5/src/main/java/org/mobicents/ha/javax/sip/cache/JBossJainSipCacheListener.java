/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.mobicents.ha.javax.sip.cache;

import gov.nist.core.StackLogger;

import org.jboss.cache.Fqn;
import org.jboss.cache.notifications.annotation.CacheStarted;
import org.jboss.cache.notifications.annotation.CacheStopped;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.NodeRemoved;
import org.jboss.cache.notifications.annotation.ViewChanged;
import org.jboss.cache.notifications.event.CacheStartedEvent;
import org.jboss.cache.notifications.event.CacheStoppedEvent;
import org.jboss.cache.notifications.event.NodeCreatedEvent;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.notifications.event.NodeRemovedEvent;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.SipStackImpl;

/**
 * Listener on the cache to be notified and update the local stack accordingly
 * 
 * @author jean.deruelle@gmail.com
 *
 */
@org.jboss.cache.notifications.annotation.CacheListener
public class JBossJainSipCacheListener {

	private ClusteredSipStack clusteredSipStack;

	/**
	 * @param clusteredSipStack 
	 * 
	 */
	public JBossJainSipCacheListener(ClusteredSipStack clusteredSipStack) {
		this.clusteredSipStack = clusteredSipStack;
	}

	@CacheStarted
	public void cacheStarted(CacheStartedEvent cacheStartedEvent) {
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredSipStack.getStackLogger().logInfo(
					"Mobicents Cache started, status: " + cacheStartedEvent.getCache().getCacheStatus() + 
					", Mode: " + cacheStartedEvent.getCache().getConfiguration().getCacheModeString());
		}
	}

	@CacheStopped
	public void cacheStopped(CacheStoppedEvent cacheStoppedEvent) {
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredSipStack.getStackLogger().logInfo(
					"Mobicents Cache stopped, status: " + cacheStoppedEvent.getCache().getCacheStatus() + 
					", Mode: " + cacheStoppedEvent.getCache().getConfiguration().getCacheModeString());
		}
	}
	
	@NodeCreated
	public void nodeCreated(NodeCreatedEvent nodeCreatedEvent) {		
		if(nodeCreatedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = nodeCreatedEvent.getFqn();
		if (!nodeCreatedEvent.isOriginLocal() && clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("sipStack " + clusteredSipStack + 
					" Node created : " + fqn);
		}
	}

	@NodeModified
	public void nodeModified(NodeModifiedEvent nodeModifiedEvent) {
		if(nodeModifiedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = nodeModifiedEvent.getFqn();
		if (!nodeModifiedEvent.isOriginLocal() && clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("sipStack " + clusteredSipStack + 
					" Node modified : " + fqn);
		}
		
	}

	@NodeRemoved
	public void nodeRemoved(NodeRemovedEvent nodeRemovedEvent) {
		if(nodeRemovedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = nodeRemovedEvent.getFqn();
		if (!nodeRemovedEvent.isOriginLocal() && clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("sipStack " + clusteredSipStack + 
					" Node removed : " + fqn);
		}		
	}

	@ViewChanged
	public void viewChange(org.jboss.cache.notifications.event.ViewChangedEvent viewChangedEvent) {
		if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("sipStack " + clusteredSipStack + 
					" View changed : " + viewChangedEvent.getNewView().getVid());
		}
	}

}
