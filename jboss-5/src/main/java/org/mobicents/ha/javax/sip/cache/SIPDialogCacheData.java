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
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.SIPDialog;

import java.text.ParseException;
import java.util.Map;
import java.util.Map.Entry;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.header.ContactHeader;

import org.jboss.cache.CacheException;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.mobicents.cache.CacheData;
import org.mobicents.cache.MobicentsCache;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialogFactory;

/**
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public class SIPDialogCacheData extends CacheData {
	private static final String APPDATA = "APPDATA";
	private ClusteredSipStack clusteredSipStack;
	
	public SIPDialogCacheData(Fqn nodeFqn, MobicentsCache mobicentsCache, ClusteredSipStack clusteredSipStack) {
		super(nodeFqn, mobicentsCache);
		this.clusteredSipStack = clusteredSipStack;
	}
	
	public SIPDialog getSIPDialog(String dialogId) throws SipCacheException {
		final Node<String,Object> childNode = getNode().getChild(dialogId);
		AbstractHASipDialog haSipDialog = null;
		if(childNode != null) {
			try {
				final Map<String, Object> dialogMetaData = childNode.getData();				
				if(dialogMetaData != null) {
					if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
						clusteredSipStack.getStackLogger().logDebug("sipStack " + this + " dialog " + dialogId + " is present in the distributed cache, recreating it locally");
					}
					final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);
					final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
					haSipDialog = HASipDialogFactory.createHASipDialog(clusteredSipStack.getReplicationStrategy(), (SipProviderImpl)clusteredSipStack.getSipProviders().next(), lastResponse);
					haSipDialog.setDialogId(dialogId);
					haSipDialog.setMetaDataToReplicate(dialogMetaData);
					Object dialogAppData = childNode.get(APPDATA);
					haSipDialog.setApplicationDataToReplicate(dialogAppData);
					final String contactStringified = (String) dialogMetaData.get(AbstractHASipDialog.CONTACT_HEADER);
					if(contactStringified != null) {
						Address contactAddress = SipFactory.getInstance().createAddressFactory().createAddress(contactStringified);
						ContactHeader contactHeader = SipFactory.getInstance().createHeaderFactory().createContactHeader(contactAddress);
						haSipDialog.setContactHeader(contactHeader);
					}
					if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
						clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + dialogId + " is Server ? " + haSipDialog.isServer() );
					}
					if(haSipDialog.isServer()) {
						String remoteTag = haSipDialog.getLocalTag();
						Address remoteParty = haSipDialog.getLocalParty();
						String localTag = haSipDialog.getRemoteTag();
						Address localParty = haSipDialog.getRemoteParty();
						haSipDialog.setLocalTagInternal(localTag);
						haSipDialog.setLocalPartyInternal(localParty);
						haSipDialog.setRemoteTagInternal(remoteTag);
						haSipDialog.setRemotePartyInternal(remoteParty);
					}					
					if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
						clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + dialogId + " localTag  = " + haSipDialog.getLocalTag());
						clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + dialogId + " remoteTag  = " + haSipDialog.getRemoteTag());
						clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + dialogId + " localParty = " + haSipDialog.getLocalParty());
						clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + dialogId + " remoteParty  = " + haSipDialog.getRemoteParty());
					}
				}				
				return haSipDialog;
			} catch (CacheException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the TreeCache", e);
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the TreeCache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the TreeCache", e);
			}
		}
		return haSipDialog;
	}
	
	public void putSIPDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logStackTrace();
		}
		final AbstractHASipDialog haDialog = (AbstractHASipDialog) dialog;
		if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("HA SIP Dialog " + haDialog.getDialogId() + " is Server ? " + haDialog.isServer() );
		}
		final Node childNode = getNode().addChild(Fqn.fromElements(dialog.getDialogId()));
		for (Entry<String, Object> metaData : haDialog.getMetaDataToReplicate().entrySet()) {
			childNode.put(metaData.getKey(), metaData.getValue());
		}
		final Object dialogAppData = haDialog.getApplicationDataToReplicate();
		if(dialogAppData != null) {
			childNode.put(APPDATA, dialogAppData);
		}		
	}

	public boolean removeSIPDialog(String dialogId) {
		return getNode().removeChild(dialogId);
	}
}
