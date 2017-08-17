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

import java.text.ParseException;
import java.util.Map;

import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.header.ContactHeader;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.HASipDialog;
import org.mobicents.ha.javax.sip.HASipDialogFactory;
import org.restcomm.cache.MobicentsCache;
import org.restcomm.cluster.cache.ClusteredCacheData;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.AbstractHASipDialog;
import gov.nist.javax.sip.stack.SIPDialog;

/**
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public class SIPDialogCacheData extends ClusteredCacheData<String, Map<String,Object>> {
	private static final String APPDATA = "APPDATA";
	private ClusteredSipStack clusteredSipStack;
	private MobicentsCache mobicentsCache;
	private static StackLogger logger = CommonLogger.getLogger(SIPDialogCacheData.class);
	public SIPDialogCacheData(String dialogID, MobicentsCache mobicentsCache, ClusteredSipStack clusteredSipStack) {
		super(dialogID, mobicentsCache);
		this.clusteredSipStack = clusteredSipStack;
		this.mobicentsCache = mobicentsCache;
	}
	
	public SIPDialog getSIPDialog() throws SipCacheException {
		HASipDialog haSipDialog = null;
		//final Cache jbossCache = getMobicentsCache().getJBossCache();
		//Configuration config = jbossCache.getConfiguration();
		final boolean isBuddyReplicationEnabled = mobicentsCache.isBuddyReplicationEnabled();
		TransactionManager transactionManager = mobicentsCache.getTxManager();
		boolean doTx = false;
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }
			// Issue 1517 : http://code.google.com/p/restcomm/issues/detail?id=1517
			// Adding code to handle Buddy replication to force data gravitation
			if(isBuddyReplicationEnabled) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("forcing data gravitation since buddy replication is enabled");
				}
				mobicentsCache.setForceDataGravitation(true);
			}

			Map<String,Object> dialogData = getValue();
			if(dialogData != null) {
				try {
					final Object dialogAppData = dialogData.remove(APPDATA);
					haSipDialog = createDialog(getKey(), dialogData, dialogAppData);
				} catch (Exception e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + getKey() + " from the Cache", e);
				} 
			}			
		} catch (Exception ex) {
			try {
				logger.logError("Problem taking dialog from cache. We will try to rollback the transction is any.",
						ex);
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				logger.logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					logger.logWarning("endBatch(): rolling back transaction with exception: "
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
		return (SIPDialog) haSipDialog;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#updateDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void updateSIPDialog(SIPDialog sipDialog) throws SipCacheException {
		final String dialogId = sipDialog.getDialogId();
		//final Cache jbossCache = getMobicentsCache().getJBossCache();
		//Configuration config = jbossCache.getConfiguration();
		final boolean isBuddyReplicationEnabled = mobicentsCache.isBuddyReplicationEnabled();
		TransactionManager transactionManager = mobicentsCache.getTxManager();
		boolean doTx = false;
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }
			// Issue 1517 : http://code.google.com/p/restcomm/issues/detail?id=1517
			// Adding code to handle Buddy replication to force data gravitation   
			if(isBuddyReplicationEnabled) {     
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("forcing data gravitation since buddy replication is enabled");
				}
				mobicentsCache.setForceDataGravitation(true);
			}

			final Map<String, Object> dialogData = getValue();
			if(dialogData != null) {
				try {
					final HASipDialog haSipDialog = (HASipDialog) sipDialog;
					final Object dialogAppData = dialogData.remove(APPDATA);
					updateDialog(haSipDialog, dialogData, dialogAppData);
				//} catch (CacheException e) {
				} catch (Exception e) {
					throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from the Cache", e);
				}
			}
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				logger.logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					logger.logWarning("endBatch(): rolling back transaction with exception: "
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
	
	public HASipDialog createDialog(String dialogId, Map<String, Object> dialogMetaData, Object dialogAppData) throws SipCacheException {
		HASipDialog haSipDialog = null; 
		if(dialogMetaData != null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("sipStack " + this + " dialog " + dialogId + " is present in the distributed cache, recreating it locally");
			}
			final String lastResponseStringified = (String) dialogMetaData.get(AbstractHASipDialog.LAST_RESPONSE);
			try {
				final SIPResponse lastResponse = (SIPResponse) SipFactory.getInstance().createMessageFactory().createResponse(lastResponseStringified);
				haSipDialog = HASipDialogFactory.createHASipDialog(clusteredSipStack.getReplicationStrategy(), (SipProviderImpl)clusteredSipStack.getSipProviders().next(), lastResponse);
				haSipDialog.setDialogId(dialogId);
				updateDialogMetaData(dialogMetaData, dialogAppData, haSipDialog, true);
				// setLastResponse won't be called on recreation since version will be null on recreation			
				haSipDialog.setLastResponse(lastResponse);				
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("HA SIP Dialog " + dialogId + " localTag  = " + haSipDialog.getLocalTag());
					logger.logDebug("HA SIP Dialog " + dialogId + " remoteTag  = " + haSipDialog.getRemoteTag());
					logger.logDebug("HA SIP Dialog " + dialogId + " localParty = " + haSipDialog.getLocalParty());
					logger.logDebug("HA SIP Dialog " + dialogId + " remoteParty  = " + haSipDialog.getRemoteParty());
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
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is older " + currentVersion + " than the one in the cache " + cacheVersion + " updating it");
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
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("HA SIP Dialog " + haSipDialog + " with dialogId " + haSipDialog.getDialogIdToReplicate() + " is not older " + currentVersion + " than the one in the cache " + cacheVersion + ", not updating it");
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
	private void updateDialogMetaData(Map<String, Object> dialogMetaData, Object dialogAppData, HASipDialog haSipDialog, boolean recreation) throws ParseException,
			PeerUnavailableException {
		haSipDialog.setMetaDataToReplicate(dialogMetaData, recreation);
		haSipDialog.setApplicationDataToReplicate(dialogAppData);
		final String contactStringified = (String) dialogMetaData.get(AbstractHASipDialog.CONTACT_HEADER);
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("contactStringified " + contactStringified);
		}
		if(contactStringified != null) {
			Address contactAddress = SipFactory.getInstance().createAddressFactory().createAddress(contactStringified);
			ContactHeader contactHeader = SipFactory.getInstance().createHeaderFactory().createContactHeader(contactAddress);
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("contactHeader " + contactHeader);
				logger.logDebug("contactURI " + contactHeader.getAddress().getURI());
			}
			haSipDialog.setContactHeader(contactHeader);
		}
	}
	
	public void putSIPDialog(SIPDialog dialog) throws SipCacheException {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logStackTrace();
		}
		final HASipDialog haSipDialog = (HASipDialog) dialog;
		final String dialogId = haSipDialog.getDialogIdToReplicate();
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("put HA SIP Dialog " + dialog + " with dialog " + dialogId);
		}
		//final Cache jbossCache = getMobicentsCache().getJBossCache();
		TransactionManager transactionManager = mobicentsCache.getTxManager();
		boolean doTx = false;
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }
			
			/*final FqnWrapper fqnWrapper = FqnWrapper.fromElementsWrapper(dialogId);
			final Object childNode = addChildNode(fqnWrapper);*/
			if (!exists()) {
			    Map<String, Object> dialogData = haSipDialog.getMetaDataToReplicate();
			    final Object dialogAppData = haSipDialog.getApplicationDataToReplicate();
                if (dialogAppData != null) {
                    dialogData.put(APPDATA, dialogAppData);
                }
                putValue(dialogData);
			}
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				logger.logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					logger.logWarning("endBatch(): rolling back transaction with exception: "
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

	public boolean removeSIPDialog() {
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("remove HA SIP Dialog " + getKey());
		}
		boolean succeeded = false;
		//final Cache jbossCache = getMobicentsCache().getJBossCache();
		TransactionManager transactionManager = mobicentsCache.getTxManager();
		boolean doTx = false;
		try {
			if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				logger.logDebug("transaction manager :" + transactionManager);
			}
			if(transactionManager != null && transactionManager.getTransaction() == null) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
					logger.logDebug("transaction manager begin transaction");
				}
				transactionManager.begin();				
				doTx = true;				
	        }
			succeeded = remove() != null;
		} catch (Exception ex) {
			try {
				if(transactionManager != null) {
					// Let's set it no matter what.
					transactionManager.setRollbackOnly();
				}
			} catch (Exception exn) {
				logger.logError("Problem rolling back session mgmt transaction",
						exn);
			}			
		} finally {
			if (doTx) {
				try {
					if (transactionManager.getTransaction().getStatus() != Status.STATUS_MARKED_ROLLBACK) {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("transaction manager committing transaction");
						}
						transactionManager.commit();
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							logger.logDebug("endBatch(): rolling back batch");
						}
						transactionManager.rollback();
					}
				} catch (RollbackException re) {
					// Do nothing here since cache may rollback automatically.
					logger.logWarning("endBatch(): rolling back transaction with exception: "
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

	public void evictSIPDialog() {
	    evict();
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("HA SIP Dialog " + getKey() + " evicted");
		}
	}
}
