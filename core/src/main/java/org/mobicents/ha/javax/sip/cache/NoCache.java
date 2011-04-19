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

import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Properties;

import org.mobicents.ha.javax.sip.ClusteredSipStack;

/**
 * Empty cache class that does nothing except returning true for inLocalMode that we can use the mobicents jain sip stack
 * in default mode wihtout the need of jboss cache
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class NoCache implements SipCache {

	public SIPDialog getDialog(String dialogId) throws SipCacheException {
		return null;
	}

	public boolean inLocalMode() {
		return true;
	}

	public void init() throws SipCacheException {
		
	}

	public void putDialog(SIPDialog dialog) throws SipCacheException {
		
	}

	public void updateDialog(SIPDialog sipDialog) throws SipCacheException {
		
	}
	
	public void removeDialog(String dialogId) throws SipCacheException {
		
	}

	public void setClusteredSipStack(ClusteredSipStack clusteredSipStack) {
		
	}

	public void setConfigurationProperties(Properties configurationProperties) {
		
	}

	public void start() throws SipCacheException {
		
	}

	public void stop() throws SipCacheException {
		
	}

	public void evictDialog(String dialogId) {
		
	}

	public SIPServerTransaction getServerTransaction(String transactionId) {
		return null;
	}

	public void putServerTransaction(SIPServerTransaction serverTransaction) {
		
	}

	public void removeServerTransaction(String transactionId) {
		
	}

	public SIPClientTransaction getClientTransaction(String transactionId)
			throws SipCacheException {
		// TODO Auto-generated method stub
		return null;
	}

	public void putClientTransaction(SIPClientTransaction clientTransaction)
			throws SipCacheException {
		// TODO Auto-generated method stub
		
	}

	public void removeClientTransaction(String transactionId)
			throws SipCacheException {
		// TODO Auto-generated method stub
		
	}

	
}
