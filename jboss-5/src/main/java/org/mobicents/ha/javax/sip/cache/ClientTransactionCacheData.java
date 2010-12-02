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
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MessageProcessor;
import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPTransactionStack;

import java.io.IOException;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Map;
import java.util.Map.Entry;

import javax.sip.PeerUnavailableException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.config.Configuration;
import org.mobicents.cache.CacheData;
import org.mobicents.cache.MobicentsCache;
import org.mobicents.ha.javax.sip.ClusteredSipStack;

/**
 * @author jean.deruelle@gmail.com
 */
public class ClientTransactionCacheData extends CacheData {
	private static final String APPDATA = "APPDATA";
	private ClusteredSipStack clusteredSipStack;	
	
	public ClientTransactionCacheData(Fqn nodeFqn, MobicentsCache mobicentsCache, ClusteredSipStack clusteredSipStack) {
		super(nodeFqn, mobicentsCache);
		this.clusteredSipStack = clusteredSipStack;
	}
	
	public SIPClientTransaction getServerTransaction(String txId) throws SipCacheException {
		SIPClientTransaction haSipClientTransaction = null;
		final Cache jbossCache = getMobicentsCache().getJBossCache();
		Configuration config = jbossCache.getConfiguration();
		final boolean isBuddyReplicationEnabled = config.getBuddyReplicationConfig() != null && config.getBuddyReplicationConfig().isEnabled();
		TransactionManager transactionManager = config.getRuntimeConfig().getTransactionManager();		
		boolean doTx = false;
		try {
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }
			// Issue 1517 : http://code.google.com/p/mobicents/issues/detail?id=1517
			// Adding code to handle Buddy replication to force data gravitation   
			if(isBuddyReplicationEnabled) {     
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("forcing data gravitation since buddy replication is enabled");
				}
				jbossCache.getInvocationContext().getOptionOverrides().setForceDataGravitation(true);
			}
			// not sure why but whatever we do the transactionId is always set to lower case in the cache
            final Node<String,Object> childNode = getNode().getChild(txId.toLowerCase());
			if(childNode != null) {
				try {
					final Map<String, Object> transactionMetaData = childNode.getData();		
					final Object dialogAppData = childNode.get(APPDATA);
						
					haSipClientTransaction = createClientTransaction(txId, transactionMetaData, dialogAppData);
				} catch (CacheException e) {
					throw new SipCacheException("A problem occured while retrieving the following client transaction " + txId + " from the Cache", e);
				} 
			} else {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("no child node found for transactionId " + txId);
				}				
			}
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				clusteredSipStack.getStackLogger().logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					clusteredSipStack.getStackLogger().logWarning("endBatch(): rolling back transaction with exception: "
									+ re);
				} catch (RuntimeException re) {
					throw re;
				} catch (Exception e) {
					throw new RuntimeException(
							"endTransaction(): Caught Exception ending batch: ",
							e);
				}
			}
		}
		return haSipClientTransaction;
	}
	
	public MobicentsHASIPClientTransaction createClientTransaction(String txId, Map<String, Object> transactionMetaData, Object transactionAppData) throws SipCacheException {
		MobicentsHASIPClientTransaction haClientTransaction = null; 
		if(transactionMetaData != null) {
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("sipStack " + this + " client transaction " + txId + " is present in the distributed cache, recreating it locally");
			}
			String channelTransport = (String) transactionMetaData.get(MobicentsHASIPClientTransaction.TRANSPORT);
			if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug(txId + " : transport " + channelTransport);
			}
			InetAddress channelIp = (InetAddress) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_IP);
			if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug(txId + " : channel peer Ip address " + channelIp);
			}
			Integer channelPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.PEER_PORT);
			if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug(txId + " : channel peer port " + channelPort);
			}
			Integer myPort = (Integer) transactionMetaData.get(MobicentsHASIPClientTransaction.MY_PORT);
			if (clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug(txId + " : my port " + myPort);
			}
			MessageChannel messageChannel = null;
			MessageProcessor[] messageProcessors = clusteredSipStack.getStackMessageProcessors();
			for (MessageProcessor messageProcessor : messageProcessors) {
				if(messageProcessor.getTransport().equalsIgnoreCase(channelTransport)) {
					try {
						messageChannel = messageProcessor.createMessageChannel(channelIp, channelPort);
					} catch (IOException e) {
						clusteredSipStack.getStackLogger().logError("couldn't recreate the message channel on ip address " 
								+ channelIp + " and port " + channelPort, e);
					}
					break;
				}
			}
			
			haClientTransaction = new MobicentsHASIPClientTransaction((SIPTransactionStack) clusteredSipStack, messageChannel);
			haClientTransaction.setBranch(txId);
			try {
				updateClientTransactionMetaData(transactionMetaData, transactionAppData, haClientTransaction, true);						
			} catch (PeerUnavailableException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			} catch (ParseException e) {
				throw new SipCacheException("A problem occured while retrieving the following transaction " + txId + " from the Cache", e);
			}
		} else {
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("sipStack " + this + " client transaction " + txId + " not found in the distributed cache");
			}
		}
		
		return haClientTransaction;
	}

	/**
	 * Update the haSipDialog passed in param with the dialogMetaData and app meta data
	 * @param transactionMetaData
	 * @param transactionAppData
	 * @param haClientTransaction
	 * @throws ParseException
	 * @throws PeerUnavailableException
	 */
	private void updateClientTransactionMetaData(Map<String, Object> transactionMetaData, Object transactionAppData, MobicentsHASIPClientTransaction haClientTransaction, boolean recreation) throws ParseException,
			PeerUnavailableException {
		haClientTransaction.setMetaDataToReplicate(transactionMetaData, recreation);
		haClientTransaction.setApplicationDataToReplicate(transactionAppData);		
	}
	
	public void putClientTransaction(SIPClientTransaction clientTransaction) throws SipCacheException {
		if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logStackTrace();
		}
		final MobicentsHASIPClientTransaction haClientTransaction = (MobicentsHASIPClientTransaction) clientTransaction;
		final String transactionId = haClientTransaction.getTransactionId();
		if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("put HA SIP Client Transaction " + clientTransaction + " with id " + transactionId);
		}
		final Cache jbossCache = getMobicentsCache().getJBossCache();
		TransactionManager transactionManager = jbossCache.getConfiguration().getRuntimeConfig().getTransactionManager();		
		boolean doTx = false;
		try {
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }					
			// not sure why but whatever we do the transactionId is always set to lower case in the cache
			final Node childNode = getNode().addChild(Fqn.fromElements(transactionId.toLowerCase()));
			for (Entry<String, Object> metaData : haClientTransaction.getMetaDataToReplicate().entrySet()) {
				childNode.put(metaData.getKey(), metaData.getValue());
			}
			final Object transactionAppData = haClientTransaction.getApplicationDataToReplicate();
			if(transactionAppData != null) {
				childNode.put(APPDATA, transactionAppData);
			}
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				clusteredSipStack.getStackLogger().logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					clusteredSipStack.getStackLogger().logWarning("endBatch(): rolling back transaction with exception: "
									+ re);
				} catch (RuntimeException re) {
					throw re;
				} catch (Exception e) {
					throw new RuntimeException(
							"endTransaction(): Caught Exception ending batch: ",
							e);
				}
			}
		}
	}

	public boolean removeClientTransaction(String transactionId) {
		if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			clusteredSipStack.getStackLogger().logDebug("remove HA SIP Client Transaction " + transactionId);
		}
		boolean succeeded = false;
		final Cache jbossCache = getMobicentsCache().getJBossCache();
		TransactionManager transactionManager = jbossCache.getConfiguration().getRuntimeConfig().getTransactionManager();		
		boolean doTx = false;
		try {
			if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				clusteredSipStack.getStackLogger().logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					clusteredSipStack.getStackLogger().logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }			
			// not sure why but whatever we do the transactionId is always set to lower case in the cache
			succeeded = getNode().removeChild(transactionId.toLowerCase());
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				clusteredSipStack.getStackLogger().logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(clusteredSipStack.getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							clusteredSipStack.getStackLogger().logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					clusteredSipStack.getStackLogger().logWarning("endBatch(): rolling back transaction with exception: "
									+ re);
				} catch (RuntimeException re) {
					throw re;
				} catch (Exception e) {
					throw new RuntimeException(
							"endTransaction(): Caught Exception ending batch: ",
							e);
				}
			}
		}
		return succeeded;
	}
}
