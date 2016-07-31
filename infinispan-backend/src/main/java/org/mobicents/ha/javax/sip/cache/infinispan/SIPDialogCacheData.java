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

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.HASipDialogFactory;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

import org.infinispan.Cache;

/**
 * This class modifies the original SIPDialogCacheData ( @see org.mobicents.ha.javax.sip.cache.hz.SIPDialogCacheData ) 
 * to provide an implementation backed by Infinispan Cache
 * 
 * @author <A HREF="mailto:posfai.gergely@ext.alerant.hu">Gergely Posfai</A>
 * @author <A HREF="mailto:kokuti.andras@ext.alerant.hu">Andras Kokuti</A>
 *
 */

public class SIPDialogCacheData {

	private ClusteredSipStack stack;
	private StackLogger clusteredlogger;
	private Cache<String, Object> dialogs;
	private Cache<String, Object> appDataMap;
	
	public SIPDialogCacheData(ClusteredSipStack s, 
			Cache<String, Object> dialogCache,
			Cache<String, Object> dialogAppCache) {
		stack = s;
		clusteredlogger = s.getStackLogger();
		setDialogs(dialogCache);
		setAppDataMap(dialogAppCache);
	}
	
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logTrace("getDialog("+ dialogId +")");
		
		Object metaData = getDialogs().get(dialogId);
		Object appData = getAppDataMap().get(dialogId);
		if (metaData != null) {
			return (SIPDialog) createDialog(dialogId, (Map<String, Object>) metaData, appData);
			
		} else {
			return null;
		}
	}
	
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
			clusteredlogger.logDebug("putDialog(" + dialog.getDialogId() + ")");
		}
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		
		Object dialogMetaData = haSipDialog.getMetaDataToReplicate(); 
		if (dialogMetaData != null) {
			if (getDialogs().containsKey(dialog.getDialogId())) {
				Map<String, Object> cachedMetaData = (Map<String, Object>)getDialogs().get(dialog.getDialogId());
				Long currentVersion = (Long)((Map<String, Object>)dialogMetaData).get(AbstractHASipDialog.VERSION);
				Long cacheVersion = (Long)((Map<String, Object>)cachedMetaData).get(AbstractHASipDialog.VERSION);
				if ( cacheVersion.longValue() < currentVersion.longValue()) {
					for(Entry<String, Object> e : ((Map<String, Object>)dialogMetaData).entrySet()) {
						cachedMetaData.put(e.getKey(), e.getValue());
					}
					getDialogs().replace(dialog.getDialogId(), cachedMetaData);
				}
				
			} else {
				getDialogs().put(dialog.getDialogId(), dialogMetaData);
			}
		}
		
		Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
		if (dialogAppData != null) {
			if (getAppDataMap().containsKey(dialog.getDialogId()))
				getAppDataMap().replace(dialog.getDialogId(), dialogAppData);
			else 
				getAppDataMap().put(dialog.getDialogId(), dialogAppData);
		}
	}
	
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("updateDialog(" + dialog.getDialogId() + ")");
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		final Object dialogMetaData = getDialogs().get(dialog.getDialogId());
		final Object dialogAppData = getAppDataMap().get(dialog.getDialogId()); 
	    
		updateDialog(haSipDialog, (Map<String, Object>)dialogMetaData, dialogAppData);
	}
	
	public void removeDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("removeDialog(" + dialogId + ")");
		
		getDialogs().remove(dialogId);
	}
	
	public void evictDialog(String dialogId) {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("evictDialog(" + dialogId + ")");
		
		getDialogs().remove(dialogId);
	}
	
	private HASipDialog createDialog(String dialogId, Map<String, Object> dialogMetaData, 
			Object dialogAppData) throws SipCacheException {
		HASipDialog haSipDialog = null; 
		if(dialogMetaData != null) {
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("sipStack " + this + " dialog " + dialogId + " is present in the distributed cache, recreating it locally");
			}
			
			final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);
			try {
				final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
				haSipDialog = HASipDialogFactory.createHASipDialog(stack.getReplicationStrategy(), (SipProviderImpl)stack.getSipProviders().next(), lastResponse);
				haSipDialog.setDialogId(dialogId);
				updateDialogMetaData(dialogMetaData, dialogAppData, haSipDialog, true);
				// setLastResponse won't be called on recreation since version will be null on recreation			
				haSipDialog.setLastResponse(lastResponse);				
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " localTag  = " + haSipDialog.getLocalTag());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " remoteTag  = " + haSipDialog.getRemoteTag());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " localParty = " + haSipDialog.getLocalParty());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " remoteParty  = " + haSipDialog.getRemoteParty());
					clusteredlogger.logDebug("HA SIP Dialog " + dialogId + " state  = " + ((SIPDialog)haSipDialog).getState());
				}
				
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			}
		}
		
		return haSipDialog;
	}
	
	public void updateDialog(HASipDialog haSipDialog, Map<String, Object> dialogMetaData, 
			Object dialogAppData) throws SipCacheException {
		if(dialogMetaData != null) {			
			final long currentVersion = haSipDialog.getVersion();
			final Long cacheVersion = ((Long)dialogMetaData.get(AbstractHASipDialog.VERSION)); 
			if(cacheVersion != null && currentVersion < cacheVersion.longValue()) {
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is older " + currentVersion + " than the one in the cache " + cacheVersion + " updating it");
				}
				try {
					final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);				
					final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
					haSipDialog.setLastResponse(lastResponse);
					updateDialogMetaData(dialogMetaData, dialogAppData, haSipDialog, false);
				
				}  catch (PeerUnavailableException e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + haSipDialog.getDialogIdToReplicate() + " from the TreeCache", e);
				} catch (ParseException e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + haSipDialog.getDialogIdToReplicate() + " from the TreeCache", e);
				}
			} else {
				if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredlogger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is not older " + currentVersion + " than the one in the cache " + cacheVersion + ", not updating it");
				}
			}
		}
	}
	
	
	/**
	 * Update the haSipDialog passed in param with the dialogMetaData and app meta data
	 * @param dialogMetaData
	 * @param dialogAppData
	 * @param haSipDialog
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 */
	private void updateDialogMetaData(Map<String, Object> dialogMetaData, Object dialogAppData, HASipDialog haSipDialog, boolean recreation) 
			throws ParseException, PeerUnavailableException {
		haSipDialog.setMetaDataToReplicate(dialogMetaData, recreation);
		haSipDialog.setApplicationDataToReplicate(dialogAppData);
		final String contactStringified = (String) dialogMetaData.get(AbstractHASipDialog.CONTACT_HEADER);
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("contactStringified " + contactStringified);
		}
		
		if(contactStringified != null) {
			Address contactAddress = SipFactory.getInstance().createAddressFactory().createAddress(contactStringified);
			ContactHeader contactHeader = SipFactory.getInstance().createHeaderFactory().createContactHeader(contactAddress);
			if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredlogger.logDebug("contactHeader " + contactHeader);
				clusteredlogger.logDebug("contactURI " + contactHeader.getAddress().getURI());
			}
			haSipDialog.setContactHeader(contactHeader);
		}
	}

	/**
	 * @return the dialogs
	 */
	public Cache<String, Object> getDialogs() {
		return dialogs;
	}

	/**
	 * @param dialogs the dialogs to set
	 */
	public void setDialogs(Cache<String, Object> dialogs) {
		this.dialogs = dialogs;
	}

	/**
	 * @return the appDataMap
	 */
	public Cache<String, Object> getAppDataMap() {
		return appDataMap;
	}

	/**
	 * @param appDataMap the appDataMap to set
	 */
	public void setAppDataMap(Cache<String, Object> appDataMap) {
		this.appDataMap = appDataMap;
	}
}
