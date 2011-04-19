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

import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Properties;

import org.mobicents.ha.javax.sip.ClusteredSipStack;

/**
 * Interface defining the contract between the cache instance and the Sip Stack
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public interface SipCache {

	public static final String SIP_DEFAULT_CACHE_CLASS_NAME = "org.mobicents.ha.javax.sip.cache.ManagedMobicentsSipCache";
	
	public static final String DIALOG_PARENT_FQN_ELEMENT = "Dialogs";
	public static final String SERVER_TX_PARENT_FQN_ELEMENT = "ServerTransactions";
	public static final String CLIENT_TX_PARENT_FQN_ELEMENT = "ClientTransactions";

	/**
	 * Set the Clustered Sip Stack that created this sip cache instance 
	 * @param clusteredSipStack the Clustered Sip Stack that created this sip cache instance
	 */
	void setClusteredSipStack(ClusteredSipStack clusteredSipStack);
	/**
	 * Set the configuration Properties that have been passed to the sip stack upon its creation 
	 * @param configurationProperties the configuration Properties that have been passed to the sip stack upon its creation 
	 */
	void setConfigurationProperties(Properties configurationProperties);

	/**
	 * Initializes the cache
	 * @throws Exception if anything goes wrong
	 */
	void init() throws SipCacheException;
	
	/**
	 * Start the cache
	 * @throws Exception if anything goes wrong
	 */
	void start() throws SipCacheException;
	
	/**
	 * Stop the cache
	 * @throws Exception if anything goes wrong
	 */
	void stop() throws SipCacheException;
	
	/**
	 * Retrieve the dialog with the passed dialogId from the cache
	 * @param dialogId id of the dialog to retrieve from the cache 
	 * @return the dialog with the passed dialogId from the cache, null if not found
	 */
	SIPDialog getDialog(String dialogId) throws SipCacheException;
	/**
	 * Store the dialog into the cache
	 * @param dialog the dialog to store
	 */
	void putDialog(SIPDialog dialog) throws SipCacheException;
	/**
	 * Update the dialog from the cache
	 * @param dialog the dialog to update
	 */
	void updateDialog(SIPDialog sipDialog) throws SipCacheException;
	/**
	 * Remove the dialog from the cache
	 * @param dialogId the id of the dialog to remove
	 */
	void removeDialog(String dialogId) throws SipCacheException;

	/**
	 * Evict the dialog from the cache memory
	 * @param dialogId the id of the dialog to evict
	 */
	void evictDialog(String dialogId);
	
	/**
	 * Retrieve the server transaction with the passed transactionId from the cache
	 * @param transactionId id of the transaction to retrieve from the cache 
	 * @return the transaction with the passed transactionId from the cache, null if not found
	 */
	SIPServerTransaction getServerTransaction(String transactionId) throws SipCacheException;	
	/**
	 * Store the server transaction into the cache
	 * @param serverTransaction the transaction to store
	 */
	void putServerTransaction(SIPServerTransaction serverTransaction) throws SipCacheException;
	/**
	 * Remove the transaction from the cache
	 * @param serverTransaction the id of the transaction to remove
	 */
	void removeServerTransaction(String transactionId) throws SipCacheException;
	
	/**
	 * Retrieve the client transaction with the passed transactionId from the cache
	 * @param transactionId id of the transaction to retrieve from the cache 
	 * @return the transaction with the passed transactionId from the cache, null if not found
	 */
	SIPClientTransaction getClientTransaction(String transactionId) throws SipCacheException;	
	/**
	 * Store the client transaction into the cache
	 * @param clientTransaction the transaction to store
	 */
	void putClientTransaction(SIPClientTransaction clientTransaction) throws SipCacheException;
	/**
	 * Remove the transaction from the cache
	 * @param transactionId the id of the transaction to remove
	 */
	void removeClientTransaction(String transactionId) throws SipCacheException;
	
	/**
	 * Indicates if the cache is running in local or clustered mode.
	 * @return
	 */
	boolean inLocalMode();
}
