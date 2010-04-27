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

	static AddressFactory addressFactory = null;
	static HeaderFactory headerFactory = null;		
	boolean isCreated = false;
	private AtomicLong version = new AtomicLong(0);
	
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
		String transport = getLastResponse().getTopmostViaHeader().getTransport();
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
		firstTransactionPort = getSipProvider().getListeningPoint(getLastResponse().getTopmostViaHeader().getTransport()).getPort();
		ackProcessed = true;
//		ackSeen = true;
	}	
	
	@SuppressWarnings("unchecked")
	public Map<String,Object> getMetaDataToReplicate() {
		Map<String,Object> dialogMetaData = new HashMap<String,Object>();
		dialogMetaData.put(VERSION, Long.valueOf(version.incrementAndGet()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : version " + version);
		}
		dialogMetaData.put(LAST_RESPONSE, getLastResponse().toString());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : lastResponse " + getLastResponse());
		}
		dialogMetaData.put(IS_REINVITE, isReInvite());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isReInvite " + isReInvite());
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
			getStack().getStackLogger().logDebug(getDialogId() + " : routes " + routes);
		}
		dialogMetaData.put(TERMINATE_ON_BYE, Boolean.valueOf(isTerminatedOnBye()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : terminateOnBye " + isTerminatedOnBye());
		}
		if(getRemoteTarget() != null) {
			dialogMetaData.put(REMOTE_TARGET, getRemoteTarget().toString());
		} else {
			dialogMetaData.put(REMOTE_TARGET, null);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : remoteTarget " + getRemoteTarget());
		}		
		if(getEventHeader() != null) {
			dialogMetaData.put(EVENT_HEADER, getEventHeader().toString());
		} else {
			dialogMetaData.put(EVENT_HEADER, null);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : evenHeader " + getEventHeader());
		}
		dialogMetaData.put(B2BUA, Boolean.valueOf(isBackToBackUserAgent()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isB2BUA " + isBackToBackUserAgent());
		}
		dialogMetaData.put(IS_SERVER, Boolean.valueOf(isServer()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isServer " + isServer());
		}
		dialogMetaData.put(FIRST_TX_SECURE, Boolean.valueOf(firstTransactionSecure));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTxSecure " + firstTransactionSecure);
		}					
		dialogMetaData.put(FIRST_TX_ID, firstTransactionId);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTransactionId " + firstTransactionId);
		}
		dialogMetaData.put(FIRST_TX_METHOD, firstTransactionMethod);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTransactionMethod " + firstTransactionMethod);
		}
		dialogMetaData.put(REMOTE_TAG, getRemoteTag());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : remoteTag " + getRemoteTag());
		}
		dialogMetaData.put(LOCAL_TAG, getLocalTag());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : localTag " + getLocalTag());
		}
		dialogMetaData.put(REMOTE_CSEQ, Long.valueOf(getRemoteSeqNumber()));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : remoteCSeq " + getRemoteSeqNumber());
		}
		if(contactHeader != null) {
			dialogMetaData.put(CONTACT_HEADER, contactHeader.toString());
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : contactHeader " + contactHeader);
		}
		return dialogMetaData;
	}

	public Object getApplicationDataToReplicate() {
		return getApplicationData();
	}
	
	public void setMetaDataToReplicate(Map<String, Object> metaData) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setState(DialogState._CONFIRMED);		
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : lastResponse " + getLastResponse());
		}
		version = new AtomicLong((Long)metaData.get(VERSION));
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : version " + version);
		}
		final Boolean isB2BUA = (Boolean) metaData.get(B2BUA);
		if(isB2BUA == Boolean.TRUE) {
			setBackToBackUserAgent();
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isB2BUA " + isB2BUA);
		}
		final Boolean isReinvite = (Boolean) metaData.get(IS_REINVITE);
		if(isReinvite != null) {
			setReInviteFlag(isReinvite);
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isReInvite " + isReinvite);
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
			getStack().getStackLogger().logDebug(getDialogId() + " : evenHeader " + eventHeaderStringified);
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
			getStack().getStackLogger().logDebug(getDialogId() + " : remoteTarget " + remoteTargetStringified);
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
			getStack().getStackLogger().logDebug(getDialogId() + " : terminateOnBye " + terminateOnBye);
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
			getStack().getStackLogger().logDebug(getDialogId() + " : routes " + routes);
		}
		final Boolean isServer = (Boolean) metaData.get(IS_SERVER);
		if(isServer != null) {
			firstTransactionSeen = true;
			firstTransactionIsServerTransaction = isServer.booleanValue();
		}
		setServerTransactionFlag(isServer.booleanValue());
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : isServer " + isServer.booleanValue());
		}
		final Boolean firstTxSecure = (Boolean) metaData.get(FIRST_TX_SECURE);
		if(firstTxSecure != null) {
			firstTransactionSecure = firstTxSecure;
		}
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTxSecure " + firstTxSecure);
		}
		firstTransactionId = (String) metaData.get(FIRST_TX_ID);		
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTransactionId " + firstTransactionId);
		}
		firstTransactionMethod = (String) metaData.get(FIRST_TX_METHOD);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : firstTransactionMethod " + firstTransactionMethod);
		}
		String remoteTag = (String) metaData.get(REMOTE_TAG);
		setRemoteTag(remoteTag);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : remoteTag " + getRemoteTag());
		}
		String localTag = (String) metaData.get(LOCAL_TAG);
		setLocalTag(localTag);
		if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug(getDialogId() + " : localTag " + getLocalTag());
		}
		Long remoteCSeq = (Long) metaData.get(REMOTE_CSEQ);
		if(remoteCSeq != null) {
			setRemoteSequenceNumber(remoteCSeq.longValue());
			if (getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStack().getStackLogger().logDebug(getDialogId() + " : remoteCSeq " + getRemoteSeqNumber());
			}
		}		
	}
	
	public void setApplicationDataToReplicate(Object appData) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setApplicationData(appData);
	}
	
	@Override
	public void setState(int state) {
		DialogState oldState = getState();
		long previousVersion = version.get();
		super.setState(state);
		DialogState newState = getState();
		// we replicate only if the state has really changed
		// the fact of setting the last response upon recreation will trigger setState to be called and so the replication
		// so we make sure to replicate only if the dialog has been created
		// also  we replicate only if the version is the same, otherwise it means setState already triggered a replication by putting the dialog in the stack and therefore in the cache
		if(!newState.equals(oldState) && previousVersion == version.get()){
			replicateState();
		}
	}

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
		boolean lastResponseChanged = false;
		// version can be null on dialog recreation
		if(version != null) {
			long previousVersion = version.get(); 
			if(sipResponse != null && getLastResponse() != null && sipResponse.getStatusCode() >= 200 && !sipResponse.equals(this.getLastResponse())) {
				lastResponseChanged = true;
			}		
			super.setLastResponse(transaction, sipResponse);
			// we replicate only if the version is the same, otherwise it means lastResponse already triggered a replication by putting the dialog in the stack and therefore in the cache		
			if(lastResponseChanged && previousVersion == version.get()) {
				replicateState();
			}
		} else {
			super.setLastResponse(transaction, sipResponse);
		}
	}

	public void setLastResponse(SIPResponse lastResponse) {
		// the call to super is very important otherwise it triggers replication on dialog recreation
		super.setLastResponse(null, lastResponse);
		// local sequence number is not reset, so we need to do it
		this.localSequenceNumber = lastResponse.getCSeq().getSeqNumber();
		if(getStack().getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStack().getStackLogger().logDebug("update HA SIP Dialog " + this + " and dialogId " + getDialogId() + " with lastResponse " + lastResponse);				
		}
        this.originalLocalSequenceNumber = localSequenceNumber;
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
		CallID cid = (CallID) this.getCallId();
		StringBuilder retval = new StringBuilder(cid.getCallId());
        retval.append(Separators.COLON);
        retval.append(myTag);
        retval.append(Separators.COLON);
        retval.append(hisTag);        
        return retval.toString().toLowerCase();
	}
}
