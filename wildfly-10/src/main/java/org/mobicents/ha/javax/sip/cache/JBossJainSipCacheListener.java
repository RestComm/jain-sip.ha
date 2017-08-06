/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;

import org.infinispan.tree.Fqn;
import org.infinispan.tree.impl.NodeKey;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStarted;
import org.infinispan.notifications.cachemanagerlistener.annotation.CacheStopped;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;

import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStoppedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;

import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;

import org.mobicents.ha.javax.sip.ClusteredSipStack;

/**
 * Listener on the cache to be notified and update the local stack accordingly
 * 
 * @author jean.deruelle@gmail.com
 *
 */
@Listener
public class JBossJainSipCacheListener {
	private static StackLogger clusteredlogger = CommonLogger.getLogger(JBossJainSipCacheListener.class);

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
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredlogger.logInfo(
					"Mobicents Cache started, status: " + cacheStartedEvent.getCacheManager().getCache().getStatus() +
					", Mode: " + cacheStartedEvent.getCacheManager().getCache().getCacheConfiguration().clustering().cacheModeString());
		}
	}

	@CacheStopped
	public void cacheStopped(CacheStoppedEvent cacheStoppedEvent) {
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredlogger.logInfo(
					"Mobicents Cache stopped, status: " + cacheStoppedEvent.getCacheManager().getCache().getStatus() +
					", Mode: " + cacheStoppedEvent.getCacheManager().getCache().getCacheConfiguration().clustering().cacheModeString());
		}
	}
	
	@CacheEntryCreated
	public void nodeCreated(CacheEntryCreatedEvent nodeCreatedEvent) {
		if(nodeCreatedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = ((NodeKey)nodeCreatedEvent.getKey()).getFqn();
		if (!nodeCreatedEvent.isOriginLocal() && clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("sipStack " + clusteredSipStack + 
					" Node created : " + fqn);
		}
	}

	@CacheEntryModified
	public void nodeModified(CacheEntryModifiedEvent nodeModifiedEvent) {
		if(nodeModifiedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = ((NodeKey)nodeModifiedEvent.getKey()).getFqn();
		if (!nodeModifiedEvent.isOriginLocal() && clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("sipStack " + clusteredSipStack + 
					" Node modified : " + fqn + " " + nodeModifiedEvent.getValue());
		}
		
	}

	@CacheEntryRemoved
	public void nodeRemoved(CacheEntryRemovedEvent nodeRemovedEvent) {
		if(nodeRemovedEvent.isOriginLocal()) {
			return ;
		}
		final Fqn fqn = ((NodeKey)nodeRemovedEvent.getKey()).getFqn();
		if (!nodeRemovedEvent.isOriginLocal()) {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + clusteredSipStack + 
						" Node removed : " + fqn);
			}
			clusteredSipStack.remoteDialogRemoval(fqn.getLastElementAsString());
		}		
	}

	@ViewChanged
	public void viewChange(ViewChangedEvent viewChangedEvent) {
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("sipStack " + clusteredSipStack + 
					" View changed : " + viewChangedEvent.getViewId());
		}
	}

}
