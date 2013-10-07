/*
 * TeleStax, Open Source Cloud Communications.
 * Copyright 2011-2013 and individual contributors by the @authors tag. 
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
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.ConfirmedReplicationSipDialog;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.header.ContactHeader;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.HASipDialogFactory;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

/**
 * Implementation of the SipCache interface, backed by a Hazelcast Cache 3.0.X
 * The configuration of Hazelcast Cache can be set throught the following Mobicents SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.HAZELCAST_CACHE_CONFIG_PATH</b>
 * 
 * @author icivico@gmail.com
 *
 */
public class HazelcastCache implements SipCache {
	
	public static final String HAZELCAST_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.HAZELCAST_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/cache-configuration.xml"; 
	private static StackLogger clusteredlogger = CommonLogger.getLogger(HazelcastCache.class);
	
	private Properties configProperties = null;
	protected HazelcastInstance hz;
	private ClusteredSipStack stack;
	private IMap<String, Object> dialogs;
	private IMap<String, Object> appDataMap;
	
	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logTrace("getDialog("+ dialogId +")");
		
		Object metaData = dialogs.get(dialogId);
		Object appData = appDataMap.get(dialogId);
		if (metaData != null) {
			SIPDialog d = (SIPDialog) createDialog(dialogId, (Map<String, Object>) metaData, appData);
			return d;
			
		} else {
			return null;
		}
	}
	
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("putDialog(" + dialog.getDialogId() + ")");
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		
		Object dialogMetaData = haSipDialog.getMetaDataToReplicate(); 
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((Map<String, Object>)dialogMetaData).get(AbstractHASipDialog.REMOTE_TARGET));
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((ConfirmedReplicationSipDialog)dialog).getRemoteTarget());
		}
		// fix error with remote contact
		//((Map<String, Object>)dialogMetaData).put(AbstractHASipDialog.REMOTE_TARGET, ((ConfirmedReplicationSipDialog)dialog).getRemoteTarget().toString());
		if (dialogs.containsKey(dialog.getDialogId()))
			dialogs.replace(dialog.getDialogId(), dialogMetaData);
		else
			dialogs.put(dialog.getDialogId(), dialogMetaData);
		
		Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
		if (dialogAppData != null) {
			if (appDataMap.containsKey(dialog.getDialogId()))
				appDataMap.replace(dialog.getDialogId(), dialogAppData);
			else 
				appDataMap.put(dialog.getDialogId(), dialogAppData);
		}
	}
	
	public void updateDialog(SIPDialog dialog) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("updateDialog(" + dialog.getDialogId() + ")");
		
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		
		Object dialogMetadata = haSipDialog.getMetaDataToReplicate();
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((Map<String, Object>)dialogMetadata).get(AbstractHASipDialog.REMOTE_TARGET));
			clusteredlogger.logDebug("HA SIP Dialog " + dialog.getDialogId() + " remoteParty  = " + ((ConfirmedReplicationSipDialog)dialog).getRemoteTarget());
		}
		// fix an error with remote contact
		//((Map<String, Object>)o).put(AbstractHASipDialog.REMOTE_TARGET, haSipDialog.getRemoteTarget().toString());
		if (dialogs.containsKey(dialog.getDialogId()))
			dialogs.replace(dialog.getDialogId(), dialogMetadata);
		else
			dialogs.put(dialog.getDialogId(), dialogMetadata);
		
		Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
		if (dialogAppData != null) {
			if (appDataMap.containsKey(dialog.getDialogId()))
				appDataMap.replace(dialog.getDialogId(), dialogAppData);
			else 
				appDataMap.put(dialog.getDialogId(), dialogAppData);
		}
	}
	
	public void removeDialog(String dialogId) throws SipCacheException {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("removeDialog(" + dialogId + ")");
		
		dialogs.remove(dialogId);
	}
	
	public void evictDialog(String dialogId) {
		if(clusteredlogger.isLoggingEnabled(StackLogger.TRACE_TRACE))
			clusteredlogger.logDebug("evictDialog(" + dialogId + ")");
		
		dialogs.remove(dialogId);
	}
	
	public SIPClientTransaction getClientTransaction(String arg0) throws SipCacheException {
		return null;
	}

	public SIPServerTransaction getServerTransaction(String arg0) throws SipCacheException {
		return null;
	}
	
	public void putClientTransaction(SIPClientTransaction arg0) throws SipCacheException {
	
	}

	public void putServerTransaction(SIPServerTransaction arg0) throws SipCacheException {
		
	}
	
	public void removeClientTransaction(String arg0) throws SipCacheException {
	
	}

	public void removeServerTransaction(String arg0) throws SipCacheException {
	
	}
	
	public void init() throws SipCacheException {
		Config cfg = null;
		
		String pojoConfigurationPath = configProperties.getProperty(HAZELCAST_CACHE_CONFIG_PATH);
		if (pojoConfigurationPath != null) {
			if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
				clusteredlogger.logInfo(
						"Mobicents JAIN SIP Hazelcast Cache Configuration path is : " + pojoConfigurationPath);
			}
			try {
				cfg = new XmlConfigBuilder(pojoConfigurationPath).build();
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
		} else {
			cfg = new ClasspathXmlConfig(DEFAULT_FILE_CONFIG_PATH);
			
		}
		cfg.setInstanceName("jain-sip-ha");
        hz = Hazelcast.newHazelcastInstance(cfg);
		dialogs = hz.getMap("cache.dialogs");
		appDataMap = hz.getMap("cache.appdata");
	}
	
	public void start() throws SipCacheException {
		
	}

	public void stop() throws SipCacheException {
		
	}
	
	public void setConfigurationProperties(Properties configurationProperties) {
		this.configProperties = configurationProperties;
	}

	public boolean inLocalMode() {
		return false;
	}
	
	public void setClusteredSipStack(ClusteredSipStack clusteredStack) {
		stack = clusteredStack;
	}

	public HASipDialog createDialog(String dialogId, Map<String, Object> dialogMetaData, Object dialogAppData) throws SipCacheException {
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
				}
				
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
			}
		}
		
		return haSipDialog;
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
}
