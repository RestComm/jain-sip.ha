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
package gov.nist.javax.sip.stack;

import gov.nist.core.Separators;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.EventParser;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.sip.DialogState;
import javax.sip.PeerUnavailableException;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.HeaderFactory;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public abstract class AbstractHASipDialog extends SIPDialog implements HASipDialog {
	
	private static final long serialVersionUID = 1L;	
	public static final String B2BUA = "b2b";
	public static final String EVENT_HEADER = "eh";	
	public static final String REMOTE_TARGET = "rt";
	public static final String TERMINATE_ON_BYE = "tob";
	public static final String ROUTE_LIST = "rl";
	public static final String IS_REINVITE = "ir";
	public static final String LAST_RESPONSE = "lr";
	public static final String IS_SERVER = "is";
	public static final String FIRST_TX_METHOD = "ftm";
	public static final String FIRST_TX_ID = "ftid";
	public static final String FIRST_TX_PORT = "ftp";
	public static final String FIRST_TX_SECURE = "fts";
	public static final String CONTACT_HEADER = "ch";
	public static final String LOCAL_TAG = "ltag";
	public static final String REMOTE_TAG = "rtag";
	public static final String VERSION = "v";
	public static final String REMOTE_CSEQ = "rc";
	public static final String LOCAL_CSEQ = "lc";
	public static final String DIALOG_METHOD = "dm";

	static AddressFactory addressFactory = null;
	static HeaderFactory headerFactory = null;		
	boolean isCreated = false;
	private AtomicLong version = new AtomicLong(0);
	private String lastResponseStringified = null;
	
	static {		
		try {
			headerFactory = SipFactory.getInstance().createHeaderFactory();
			addressFactory = SipFactory.getInstance().createAddressFactory();
		} catch (PeerUnavailableException e) {}
	}
	
	public AbstractHASipDialog(SIPTransaction transaction) {
		super(transaction);		
		isCreated = true;
	}
	
	public AbstractHASipDialog(SIPClientTransaction transaction, SIPResponse sipResponse) {
		super(transaction, sipResponse);
		isCreated = true;
	}
	
    public AbstractHASipDialog(SipProviderImpl sipProvider, SIPResponse sipResponse) {
		super(sipProvider, sipResponse);
		isCreated = true;
	}	

	/**
	 * Updates the local dialog transient attributes that were not serialized during the replication 
	 * @param sipStackImpl the sip Stack Impl that reloaded this dialog from the distributed cache
	 */
	public void initAfterLoad(ClusteredSipStack sipStackImpl) {
		String transport = getLastResponseTopMostVia().getTransport();
		Iterator<SipProviderImpl> providers = sipStackImpl.getSipProviders();
		boolean providerNotFound = true;
		while(providers.hasNext()) {
			SipProviderImpl providerImpl = providers.next();
			if(providerImpl.getListeningPoint(transport) != null) {
				setSipProvider(providerImpl);
				providerNotFound = false;
			}
		}
		if(providerNotFound) {
			throw new RuntimeException("No providers found for transport=" 
					+ transport + " on this node. Make sure connectors are configured for this transport");
		}
		setStack((SIPTransactionStack)sipStackImpl);
		setAssigned();
		firstTransactionPort = getSipProvider().getListeningPoint(getLastResponseTopMostVia().getTransport()).getPort();
		ackProcessed = true;
//		ackSeen = true;
	}			

	@SuppressWarnings("unchecked")
	public Map<String,Object> getMetaDataToReplicate() {
		Map<String,Object> dialogMetaData = new HashMap<String,Object>();
		dialogMetaData.put(VERSION, Long.valueOf(version.incrementAndGet()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : version " + version);
		}
		dialogMetaData.put(DIALOG_METHOD, getMethod());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : dialog method " + getMethod());
		}
		dialogMetaData.put(LAST_RESPONSE, getLastResponseStringified());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : lastResponse " + getLastResponseStringified());
		}
		dialogMetaData.put(IS_REINVITE, isReInvite());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isReInvite " + isReInvite());
		}
		final List<SIPHeader> routeList = new ArrayList<SIPHeader>();
		final Iterator<SIPHeader> it = getRouteSet();
		while (it.hasNext()) {
			SIPHeader sipHeader = (SIPHeader) it.next();
			routeList.add(sipHeader);
		}
		final String[] routes = new String[routeList.size()];
		int i = 0;
		for (SIPHeader sipHeader : routeList) {
			routes[i++] = sipHeader.getHeaderValue().toString();
		}
		dialogMetaData.put(ROUTE_LIST, routes);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : routes " + routes);
		}
		dialogMetaData.put(TERMINATE_ON_BYE, Boolean.valueOf(isTerminatedOnBye()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : terminateOnBye " + isTerminatedOnBye());
		}
		if(getRemoteTarget() != null) {
			dialogMetaData.put(REMOTE_TARGET, getRemoteTarget().toString());
		} else {
			dialogMetaData.put(REMOTE_TARGET, null);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteTarget " + getRemoteTarget());
		}		
		if(getEventHeader() != null) {
			dialogMetaData.put(EVENT_HEADER, getEventHeader().toString());
		} else {
			dialogMetaData.put(EVENT_HEADER, null);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : evenHeader " + getEventHeader());
		}
		dialogMetaData.put(B2BUA, Boolean.valueOf(isBackToBackUserAgent()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isB2BUA " + isBackToBackUserAgent());
		}
		dialogMetaData.put(IS_SERVER, Boolean.valueOf(isServer()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isServer " + isServer());
		}
		dialogMetaData.put(FIRST_TX_SECURE, Boolean.valueOf(firstTransactionSecure));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTxSecure " + firstTransactionSecure);
		}					
		dialogMetaData.put(FIRST_TX_ID, firstTransactionId);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTransactionId " + firstTransactionId);
		}
		dialogMetaData.put(FIRST_TX_METHOD, firstTransactionMethod);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTransactionMethod " + firstTransactionMethod);
		}
		dialogMetaData.put(REMOTE_TAG, getRemoteTag());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteTag " + getRemoteTag());
		}
		dialogMetaData.put(LOCAL_TAG, getLocalTag());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : localTag " + getLocalTag());
		}
		dialogMetaData.put(REMOTE_CSEQ, Long.valueOf(getRemoteSeqNumber()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteCSeq " + getRemoteSeqNumber());
		}
		dialogMetaData.put(LOCAL_CSEQ, Long.valueOf(getLocalSeqNumber()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : localCSeq " + getLocalSeqNumber());
		}
		if(contactHeader != null) {
			dialogMetaData.put(CONTACT_HEADER, contactHeader.toString());
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : contactHeader " + contactHeader);
		}
		return dialogMetaData;
	}

	public Object getApplicationDataToReplicate() {
		return getApplicationData();
	}
	
	public void setMetaDataToReplicate(Map<String, Object> metaData) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setState(DialogState._CONFIRMED);		
		lastResponseStringified = (String) metaData.get(LAST_RESPONSE);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : lastResponse " + lastResponseStringified);
		}
		method = (String) metaData.get(DIALOG_METHOD);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : dialog method " + method);
		}
		version = new AtomicLong((Long)metaData.get(VERSION));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : version " + version);
		}
		final Boolean isB2BUA = (Boolean) metaData.get(B2BUA);
		if(isB2BUA == Boolean.TRUE) {
			setBackToBackUserAgent();
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isB2BUA " + isB2BUA);
		}
		final Boolean isReinvite = (Boolean) metaData.get(IS_REINVITE);
		if(isReinvite != null) {
			setReInviteFlag(isReinvite);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isReInvite " + isReinvite);
		}
		final String eventHeaderStringified = (String) metaData.get(EVENT_HEADER);
		if(eventHeaderStringified != null) {
			try {
				setEventHeader((EventHeader)new EventParser(eventHeaderStringified).parse());
			} catch (ParseException e) {
				getStack().getStackLogger().logError("Unexpected exception while parsing a deserialized eventHeader", e);
			}
		}	
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : evenHeader " + eventHeaderStringified);
		}		
		final String remoteTargetStringified = (String) metaData.get(REMOTE_TARGET);
		if(remoteTargetStringified != null) {
			Contact contact = new Contact();
			try {
				contact.setAddress(addressFactory.createAddress(remoteTargetStringified));
				setRemoteTarget(contact);
			} catch (ParseException e) {
				getStack().getStackLogger().logError("Unexpected exception while parsing a deserialized remoteTarget address", e);
			}	       
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteTarget " + remoteTargetStringified);
		}
		final Boolean terminateOnBye = (Boolean) metaData.get(TERMINATE_ON_BYE);
		if(terminateOnBye != null) {
			try {
				terminateOnBye(terminateOnBye);
			} catch (SipException e) {
				// exception is never thrown
			}
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : terminateOnBye " + terminateOnBye);
		}
		final String[] routes = (String[]) metaData.get(ROUTE_LIST);
		if(routes != null) {			
			final RouteList routeList = new RouteList();			
			for (String route : routes) {
				try {
					routeList.add((Route)headerFactory.createRouteHeader(addressFactory.createAddress(route)));
				} catch (ParseException e) {
					getStack().getStackLogger().logError("Unexpected exception while parsing a deserialized route address", e);
				}				
			}
			setRouteList(routeList);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : routes " + routes);
		}
		final Boolean isServer = (Boolean) metaData.get(IS_SERVER);
		if(isServer != null) {
			firstTransactionSeen = true;
			firstTransactionIsServerTransaction = isServer.booleanValue();
			setServerTransactionFlag(isServer.booleanValue());
		}		
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : isServer " + isServer.booleanValue());
		}
		final Boolean firstTxSecure = (Boolean) metaData.get(FIRST_TX_SECURE);
		if(firstTxSecure != null) {
			firstTransactionSecure = firstTxSecure;
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTxSecure " + firstTxSecure);
		}
		firstTransactionId = (String) metaData.get(FIRST_TX_ID);		
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTransactionId " + firstTransactionId);
		}
		firstTransactionMethod = (String) metaData.get(FIRST_TX_METHOD);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : firstTransactionMethod " + firstTransactionMethod);
		}
		String remoteTag = (String) metaData.get(REMOTE_TAG);
		setRemoteTagInternal(remoteTag);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteTag " + getRemoteTag());
		}
		String localTag = (String) metaData.get(LOCAL_TAG);
		setLocalTagInternal(localTag);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : localTag " + getLocalTag());
		}
		Long remoteCSeq = (Long) metaData.get(REMOTE_CSEQ);
		if(remoteCSeq != null) {
			setRemoteSequenceNumber(remoteCSeq.longValue());
			if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : remoteCSeq " + getRemoteSeqNumber());
			}
		}
		Long localCSeq = (Long) metaData.get(LOCAL_CSEQ);
		if(localCSeq != null) {
			localSequenceNumber = localCSeq.longValue();
			if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStack().getStackLogger().logDebug(getDialogIdToReplicate() + " : localCSeq " + getLocalSeqNumber());
			}
		}			
	}
	
	public void setApplicationDataToReplicate(Object appData) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setApplicationData(appData);
	}
	
	// not needed anymore the setLastResponse on final response will take care of it automatically
//	@Override
//	public void setState(int state) {
//		DialogState oldState = getState();
//		long previousVersion = -1;
//		if(version != null) {
//			previousVersion= version.get();
//		}
//		super.setState(state);
//		DialogState newState = getState();
//		// we replicate only if the state has really changed
//		// the fact of setting the last response upon recreation will trigger setState to be called and so the replication
//		// so we make sure to replicate only if the dialog has been created
//		// also  we replicate only if the version is the same, otherwise it means setState already triggered a replication by putting the dialog in the stack and therefore in the cache
//		if(!newState.equals(oldState) & newState == DialogState.CONFIRMED && previousVersion != -1 && previousVersion == version.get()){
//			replicateState();
//		}
//	}

	protected abstract void replicateState();

	public void setLocalTagInternal(String localTag) {
		super.myTag = localTag;
	}
	
	public void setRemoteTagInternal(String remoteTag) {
		super.hisTag = remoteTag;
	}

	public void setLocalPartyInternal(Address localParty) {
		super.localParty = localParty;
	}

	public void setRemotePartyInternal(Address remoteParty) {
		super.remoteParty = remoteParty;
	}

	public void setContactHeader(ContactHeader contactHeader) {
		this.contactHeader = (Contact) contactHeader;
	}
		
	@Override
	public void setLastResponse(SIPTransaction transaction,
			SIPResponse sipResponse) {		
		// version can be null on dialog recreation
		if(version != null) {
			boolean lastResponseChanged = false;
			long previousVersion = version.get(); 			
			// for 2xx w/o 1xx
			if(sipResponse != null && getLastResponseStringified() == null && sipResponse.getStatusCode() >= 200) {
				lastResponseChanged = true;
			}
			String responseStringified = sipResponse.toString(); 
			if(sipResponse != null && getLastResponseStringified() != null && sipResponse.getStatusCode() >= 200 && !responseStringified.equals(this.getLastResponseStringified())) {
				lastResponseChanged = true;
			}		
			super.setLastResponse(transaction, sipResponse);
			lastResponseStringified = responseStringified;
			// we replicate only if the version is the same, otherwise it means lastResponse already triggered a replication by putting the dialog in the stack and therefore in the cache		
			if(lastResponseChanged && previousVersion == version.get()) {
				replicateState();
			}
		}
		// causes REINVITE after NOTIFY on failover to fail with NPE since method attribute is not yet initialized
//		else {
//			super.setLastResponse(transaction, sipResponse);
//		}
	}

	public void setLastResponse(SIPResponse lastResponse) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setLastResponse(null, lastResponse);
	}	

	/**
	 * @return the isRemoteTagSet
	 */
	public boolean isRemoteTagSet() {
		return hisTag != null && hisTag.trim().length() > 0 ? true : false;
	}
	
	/**
	 * @return the isRemoteTagSet
	 */
	public boolean isLocalTagSet() {
		return myTag != null && myTag.trim().length() > 0 ? true : false;
	}
	
	/**
	 * @return the version
	 */
	public long getVersion() {
		return version.get();
	}		

	public String getDialogIdToReplicate() {
		return getDialogId();
		// No need for this anymore since we replicate only when the last response is a final one
//		String cid = this.getCallId().getCallId();
//		StringBuilder retval = new StringBuilder(cid);
//        retval.append(Separators.COLON);
//        retval.append(myTag);
//        retval.append(Separators.COLON);
//        retval.append(hisTag);        
//        return retval.toString().toLowerCase();
	}
	
	public String getLastResponseStringified() {
		return lastResponseStringified;
	}		
}
