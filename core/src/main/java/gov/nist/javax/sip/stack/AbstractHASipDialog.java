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

import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sip.DialogState;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.header.EventHeader;

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
	public static final String SERVER_TRANSACTION_FLAG = "stf";
	public static final String REMOTE_TARGET = "rt";
	public static final String TERMINATE_ON_BYE = "tob";
	public static final String ROUTE_LIST = "rl";
	public static final String IS_REINVITE = "ir";
	public static final String LAST_RESPONSE = "lr";

	public AbstractHASipDialog(SIPTransaction transaction) {
		super(transaction);
	}
	
	public AbstractHASipDialog(SIPClientTransaction transaction, SIPResponse sipResponse) {
		super(transaction, sipResponse);
	}
	
    public AbstractHASipDialog(SipProviderImpl sipProvider, SIPResponse sipResponse) {
		super(sipProvider, sipResponse);
	}	

    /**
	 * Updates the local dialog transient attributes that were not serialized during the replication 
	 * @param sipStackImpl the sip Stack Impl that reloaded this dialog from the distributed cache
	 */
	public void initAfterLoad(ClusteredSipStack sipStackImpl) {
		setSipProvider((SipProviderImpl) sipStackImpl.getSipProviders().next());
		setStack((SIPTransactionStack)sipStackImpl);
		setAssigned();
		ackProcessed = true;
		ackSeen = true;
	}	
	
	public Map<String, Object> getMetaDataToReplicate() {
		Map<String, Object> dialogMetaData = new HashMap<String, Object>();
		dialogMetaData.put(LAST_RESPONSE, getLastResponse());
		dialogMetaData.put(IS_REINVITE, isReInvite());
		List<SIPHeader> routeList = new ArrayList<SIPHeader>();
		Iterator<SIPHeader> it = getRouteSet();
		while (it.hasNext()) {
			SIPHeader sipHeader = (SIPHeader) it.next();
			routeList.add(sipHeader);
		}
		dialogMetaData.put(ROUTE_LIST, routeList.toArray(new SIPHeader[routeList.size()]));
		dialogMetaData.put(TERMINATE_ON_BYE, isTerminatedOnBye());
		dialogMetaData.put(REMOTE_TARGET, getRemoteTarget());
		dialogMetaData.put(SERVER_TRANSACTION_FLAG, isServer());
		dialogMetaData.put(EVENT_HEADER, getEventHeader());
		dialogMetaData.put(B2BUA, isBackToBackUserAgent());
		return dialogMetaData;
	}

	public Object getApplicationDataToReplicate() {
		return getApplicationData();
	}
	
	public void setMetaDataToReplicate(Map<String, Object> metaData) {
		setState(DialogState._CONFIRMED);		
		Boolean isB2BUA = (Boolean) metaData.get(B2BUA);
		if(isB2BUA != null) {
			setBackToBackUserAgent(isB2BUA);
		}
		Boolean isReinvite = (Boolean) metaData.get(IS_REINVITE);
		if(isReinvite != null) {
			setReInviteFlag(isReinvite);
		}
		EventHeader eventHeader = (EventHeader) metaData.get(EVENT_HEADER);
		if(eventHeader != null) {
			setEventHeader(eventHeader);
		}	
		Boolean serverTransactionFlag = (Boolean) metaData.get(SERVER_TRANSACTION_FLAG);
		if(serverTransactionFlag != null) {
			setServerTransactionFlag(serverTransactionFlag);
		}
		Address remoteTarget = (Address) metaData.get(REMOTE_TARGET);
		if(remoteTarget != null) {
			Contact contact = new Contact();
	        contact.setAddress(remoteTarget);
			setRemoteTarget(contact);
		}
		Boolean terminateOnBye = (Boolean) metaData.get(TERMINATE_ON_BYE);
		if(terminateOnBye != null) {
			try {
				terminateOnBye(terminateOnBye);
			} catch (SipException e) {
				// exception is never thrown
			}
		}
		SIPHeader[] routes = (SIPHeader[]) metaData.get(ROUTE_LIST);
		if(routes != null) {
			RouteList routeList = new RouteList();
			for (SIPHeader sipHeader : routes) {
				routeList.add((Route)sipHeader);
			}
			setRouteList(routeList);
		}
	}
	
	public void setApplicationDataToReplicate(Object appData) {
		setApplicationData(appData);
	}
	
	@Override
	public void setState(int state) {
		DialogState oldState = getState();
		super.setState(state);
		DialogState newState = getState();
		// we replicate only if the state has really changed
		if(!newState.equals(oldState)){
			replicateState();
		}
	}

	protected abstract void replicateState();
}
