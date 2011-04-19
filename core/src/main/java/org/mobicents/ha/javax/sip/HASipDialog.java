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

package org.mobicents.ha.javax.sip;

import gov.nist.javax.sip.message.SIPResponse;

import java.util.Map;

import javax.sip.address.Address;
import javax.sip.header.ContactHeader;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public interface HASipDialog {
	void initAfterLoad(ClusteredSipStack clusteredSipStack);

	String getDialogIdToReplicate();
	void setDialogId(String dialogId);
	
	String getMergeId();
	
	Map<String, Object> getMetaDataToReplicate();
	Object getApplicationDataToReplicate();

	void setMetaDataToReplicate(Map<String, Object> dialogMetaData, boolean recreation);
	void setApplicationDataToReplicate(Object dialogAppData);

	void setContactHeader(ContactHeader contactHeader);

	long getVersion();
	
	void setLastResponse(SIPResponse lastResponse);

	boolean isServer();

	String getLocalTag();
	void setLocalTagInternal(String localTag);

	Address getLocalParty();
	void setLocalPartyInternal(Address localParty);

	String getRemoteTag();
	void setRemoteTagInternal(String remoteTag);
	
	Address getRemoteParty();
	void setRemotePartyInternal(Address remoteParty);
}
