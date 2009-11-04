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

import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.SIPDialog;

import java.text.ParseException;
import java.util.Map;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;

import org.jboss.cache.CacheException;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.mobicents.cache.CacheData;
import org.mobicents.cache.MobicentsCache;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialogFactory;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SIPDialogCacheData extends CacheData {
	private static final String APPDATA = "APPDATA";
	private static final String METADATA = "METADATA";
	private ClusteredSipStack clusteredSipStack;
	
	public SIPDialogCacheData(Fqn nodeFqn, MobicentsCache mobicentsCache, ClusteredSipStack clusteredSipStack) {
		super(nodeFqn, mobicentsCache);
		this.clusteredSipStack = clusteredSipStack;
	}
	
	public SIPDialog getSIPDialog(String dialogId) throws SipCacheException {
		final Node<String,Map<String, Object>> childNode = getNode().getChild(Fqn.fromElements(dialogId));
		AbstractHASipDialog haSipDialog = null;
		if(childNode != null) {
			try {
				Map<String, Object> dialogMetaData = (Map<String, Object>) childNode.get(METADATA);				
				 
				if(dialogMetaData != null) {
					final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);
					final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
					haSipDialog = HASipDialogFactory.createHASipDialog(clusteredSipStack.getReplicationStrategy(), (SipProviderImpl)clusteredSipStack.getSipProviders().next(), lastResponse);
					haSipDialog.setDialogId(dialogId);
					haSipDialog.setMetaDataToReplicate(dialogMetaData);
					Object dialogAppData = childNode.get(APPDATA);
					haSipDialog.setApplicationDataToReplicate(dialogAppData);				
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
		final AbstractHASipDialog haSipDialog = (AbstractHASipDialog) dialog;
		final Map<String, Object> dialogMetaData = haSipDialog.getMetaDataToReplicate();
		if(dialogMetaData != null) {
			getNode().addChild(Fqn.fromElements(dialog.getDialogId())).put(METADATA, dialogMetaData);
		}
		final Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
		if(dialogAppData != null) {
			getNode().addChild(Fqn.fromElements(dialog.getDialogId())).put(APPDATA, dialogAppData);
		}		
	}

	public boolean removeSIPDialog(String dialogId) {
		return getNode().removeChild(Fqn.fromElements(dialogId));
	}
}
