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

import gov.nist.core.Separators;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.MobicentsHASIPClientTransaction;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;
import gov.nist.javax.sip.stack.SIPTransaction;

import java.util.Properties;
import java.util.StringTokenizer;

import javax.sip.DialogState;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.ProviderDoesNotExistException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.message.Request;

import org.mobicents.ext.javax.sip.SipProviderFactory;
import org.mobicents.ext.javax.sip.SipStackExtension;
import org.mobicents.ext.javax.sip.TransactionFactory;
import org.mobicents.ha.javax.sip.cache.SipCache;
import org.mobicents.ha.javax.sip.cache.SipCacheException;
import org.mobicents.ha.javax.sip.cache.SipCacheFactory;

/**
 * This class extends the regular NIST SIP Stack Implementation to cache Dialogs in a replicated cache 
 * and make use of Fault Tolerant Timers so that the NIST SIP Stack can be distributed in a cluster 
 * and calls can be failed over 
 * 
 * This class will instantiate an instance of a class implementing the org.mobicents.ha.javax.sip.cache.SipCache interface to be able to set/get dialogs and txs into/from it.
 * The cache class name is retrieved through the Properties given to the Sip Stack upon creation under the following property name : org.mobicents.ha.javax.sip.CACHE_CLASS_NAME
 * 
 * It will override all the calls to get/remove/put Dialogs and Txs so that we can fetch/remove/put them from/into the Cache 
 * and populate the local datastructure of the NIST SIP Stack
 * 
 * @author jean.deruelle@gmail.com
 * @author martins
 *
 */
public abstract class ClusteredSipStackImpl extends gov.nist.javax.sip.SipStackImpl implements ClusteredSipStack, SipStackExtension {	
	
	protected SipCache sipCache = null;
	protected LoadBalancerHeartBeatingService loadBalancerHeartBeatingService = null;
	protected ReplicationStrategy replicationStrategy = ReplicationStrategy.ConfirmedDialog;
	protected LoadBalancerElector loadBalancerElector = null;
	protected TransactionFactory transactionFactory = null;
	protected SipProviderFactory sipProviderFactory = null;
	protected boolean sendTryingRightAway;
	private boolean replicateApplicationData = false;
	
	public ClusteredSipStackImpl(Properties configurationProperties) throws PeerUnavailableException {
		
		super(configurationProperties);
		
		String lbHbServiceClassName = configurationProperties.getProperty(LoadBalancerHeartBeatingService.LB_HB_SERVICE_CLASS_NAME);
		if(lbHbServiceClassName != null) {
			try {
	            loadBalancerHeartBeatingService = (LoadBalancerHeartBeatingService) Class.forName(lbHbServiceClassName).newInstance();	            
	        } catch (Exception e) {
	            String errmsg = "The loadBalancerHeartBeatingService class name: "
	                    + lbHbServiceClassName
	                    + " could not be instantiated. Ensure the " + LoadBalancerHeartBeatingService.LB_HB_SERVICE_CLASS_NAME + " property has been set correctly and that the class is on the classpath.";
	            throw new PeerUnavailableException(errmsg, e);
	        }
	        // create the load balancer elector if specified
	        String lbElectorClassName = configurationProperties.getProperty(LoadBalancerElector.IMPLEMENTATION_CLASS_NAME_PROPERTY);
			if(lbElectorClassName != null) {
				try {
		            loadBalancerElector = (LoadBalancerElector) Class.forName(lbElectorClassName).newInstance();	            
		        } catch (Exception e) {
		            String errmsg = "The loadBalancerElector class name: "
		                    + lbElectorClassName
		                    + " could not be instantiated. Ensure the " + LoadBalancerElector.IMPLEMENTATION_CLASS_NAME_PROPERTY + " property has been set correctly and that the class is on the classpath.";
		            throw new PeerUnavailableException(errmsg, e);
		        }
		        loadBalancerElector.setAddressFactory(SipFactory.getInstance().createAddressFactory());
		        loadBalancerElector.setStackLogger(getStackLogger());
		        loadBalancerElector.setService(loadBalancerHeartBeatingService);
			}
		}
		
		// allow the stack to provide its own SIPServerTransaction/SIPClientTransaction extension instances
		String transactionFactoryClassName = configurationProperties.getProperty(TRANSACTION_FACTORY_CLASS_NAME);
		if(transactionFactoryClassName != null) {
			try {
	            transactionFactory = (TransactionFactory) Class.forName(transactionFactoryClassName).newInstance();
	            transactionFactory.setSipStack(this);	            
	        } catch (Exception e) {
	            String errmsg = "The TransactionFactory class name: "
	                    + transactionFactoryClassName
	                    + " could not be instantiated. Ensure the " + TRANSACTION_FACTORY_CLASS_NAME + " property has been set correctly and that the class is on the classpath.";
	            throw new PeerUnavailableException(errmsg, e);
	        }
	    }
		// allow the stack to provide its own SipProviderImpl extension instances
	    String sipProviderFactoryClassName = configurationProperties.getProperty(SIP_PROVIDER_FACTORY_CLASS_NAME);
		if(sipProviderFactoryClassName != null) {
			try {
	            sipProviderFactory = (SipProviderFactory) Class.forName(sipProviderFactoryClassName).newInstance();
	            sipProviderFactory.setSipStack(this);	            
	        } catch (Exception e) {
	            String errmsg = "The SipProviderFactory class name: "
	                    + sipProviderFactoryClassName
	                    + " could not be instantiated. Ensure the " + SIP_PROVIDER_FACTORY_CLASS_NAME + " property has been set correctly and that the class is on the classpath.";
	            throw new PeerUnavailableException(errmsg, e);
	        }
	    }
		
		this.sendTryingRightAway = Boolean.valueOf(
			configurationProperties.getProperty(SEND_TRYING_RIGHT_AWAY,"false")).booleanValue();
		
		// get/create the jboss cache instance to store all sip stack related data into it
		sipCache = SipCacheFactory.createSipCache(this, configurationProperties);
		try {
			sipCache.init();
		} catch (Exception e) {
			throw new PeerUnavailableException("Unable to initialize the SipCache", e);
		}
		if(loadBalancerHeartBeatingService != null) {
			loadBalancerHeartBeatingService.init(this, configurationProperties);
		}
		String replicationStrategyProperty = configurationProperties.getProperty(ClusteredSipStack.REPLICATION_STRATEGY_PROPERTY);
		if(replicationStrategyProperty != null) {
			replicationStrategy = ReplicationStrategy.valueOf(replicationStrategyProperty);
			if(replicationStrategy == ReplicationStrategy.EarlyDialog && transactionFactory == null) {
				// when using EarlyDialog replication strategy we need to have an HA transaction factory to replicate transactions
				transactionFactory = new MobicentsHATransactionFactory();
				transactionFactory.setSipStack(this);
			}
		}
		String replicateApplicationDataProperty = configurationProperties.getProperty(ClusteredSipStack.REPLICATE_APPLICATION_DATA);
		if(replicateApplicationDataProperty != null) {			
			replicateApplicationData = Boolean.valueOf(replicateApplicationDataProperty);
		}
		// backward compatible hack to make sure old applications still replicate the app data if they don't use the new property
		if(replicateApplicationDataProperty == null && replicationStrategy == ReplicationStrategy.ConfirmedDialog) {
			replicateApplicationData = true;
		}
		if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_INFO)) {
			getStackLogger().logInfo("Replication Strategy is " + replicationStrategy + " replicating application data " + replicateApplicationData);
		}
	}		
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.SipStackImpl#start()
	 */
	@Override
	public void start() throws ProviderDoesNotExistException, SipException {
		try {
			sipCache.start();
		} catch (Exception e) {
			throw new SipException("Unable to start the SipCache", e);
		}
		if(loadBalancerHeartBeatingService != null) {
			loadBalancerHeartBeatingService.start();
		}
		super.start();		
	}
		
	public void closeAllTcpSockets() {
		
		ioHandler.closeAll();
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.SipStackImpl#stop()
	 */
	@Override
	public void stop() {		
		super.stop();
		try {
			sipCache.stop();
		} catch (Exception e) {
			getStackLogger().logError("Unable to stop the SipCache", e);
		}
		if(loadBalancerHeartBeatingService != null) {
			loadBalancerHeartBeatingService.stop();
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#createDialog(gov.nist.javax.sip.stack.SIPTransaction)
	 */
	@Override
	public SIPDialog createDialog(SIPTransaction transaction) {
		if (sipCache.inLocalMode()) {
			return super.createDialog(transaction);
		}
		else {
			SIPDialog retval = null;
			if (transaction instanceof SIPClientTransaction) {
				final String dialogId = ((SIPRequest) transaction.getRequest()).getDialogId(false);
				retval = this.earlyDialogTable.get(dialogId);
				if (retval == null || (retval.getState() != null && retval.getState() != DialogState.EARLY)) {
					retval = (SIPDialog) HASipDialogFactory.createHASipDialog(replicationStrategy, transaction);
					this.earlyDialogTable.put(dialogId, retval);
				}
			} else {
				retval = (SIPDialog) HASipDialogFactory.createHASipDialog(replicationStrategy, transaction);
			}
			return retval;
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#createDialog(gov.nist.javax.sip.stack.SIPClientTransaction, gov.nist.javax.sip.message.SIPResponse)
	 */
	@Override
	public SIPDialog createDialog(SIPClientTransaction transaction, SIPResponse sipResponse) {
		if (sipCache.inLocalMode()) {
			return super.createDialog(transaction,sipResponse);
		}
		else {
			final String dialogId = ((SIPRequest) transaction.getRequest()).getDialogId(false);
			SIPDialog retval = this.earlyDialogTable.get(dialogId);
			if (retval != null && sipResponse.isFinalResponse()) {
				this.earlyDialogTable.remove(dialogId);
			} else {
				retval = (SIPDialog) HASipDialogFactory.createHASipDialog(replicationStrategy, transaction, sipResponse);
			}
			return retval;
		}
    }
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#createDialog(gov.nist.javax.sip.SipProviderImpl, gov.nist.javax.sip.message.SIPResponse)
	 */
	@Override
	public SIPDialog createDialog(SipProviderImpl sipProvider,
			SIPResponse sipResponse) {
		if (sipCache.inLocalMode()) {
			return super.createDialog(sipProvider, sipResponse);
		}
		else {
			return (SIPDialog) HASipDialogFactory.createHASipDialog(replicationStrategy, sipProvider, sipResponse);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#getDialog(java.lang.String)
	 */
	@Override
	public SIPDialog getDialog(String dialogId) {
		if (sipCache.inLocalMode()) {
			return super.getDialog(dialogId);
		}
		else {
			if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStackLogger().logDebug("checking if the dialog " + dialogId + " is present in the local cache");
			}		
			SIPDialog sipDialog = super.getDialog(dialogId);
			int nbToken = new StringTokenizer(dialogId, Separators.COLON).countTokens();
			// we should only check the cache for dialog Id where the remote tag is set since we support only established dialog failover
			// Issue 1378 : http://code.google.com/p/mobicents/issues/detail?id=1378
			// there can be more than 3 tokens if the callid part of the dialog id contains a COLON as well
			if(nbToken >= 3) {
				if(sipDialog == null ) {
					if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
						getStackLogger().logDebug("local dialog " + dialogId + " is null, checking in the distributed cache");
					}
					sipDialog = getDialogFromDistributedCache(dialogId);
					if(sipDialog != null) {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("dialog " + dialogId + " found in the distributed cache, storing it locally");
						}
						SIPDialog existingDialog = super.putDialog(sipDialog);
						// avoid returning wrong dialog if 2 threads try to recreate
						// the dialog after failover, we use the one that won the race
						if(existingDialog != null) {
							sipDialog = existingDialog;
						}
					} else {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("dialog " + dialogId + " not found in the distributed cache");
						}
					}
				} else {
					// we check for updates only if the dialog is confirmed
					if(sipDialog.getState() == DialogState.CONFIRMED) {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("local dialog " + dialogId + " is present locally " + sipDialog + " checking if it needs to be updated from the cache");
						}
						try {
							sipCache.updateDialog(sipDialog);
						} catch (SipCacheException e) {
							getStackLogger().logError("sipStack " + this + " problem updating dialog " + dialogId + " from the distributed cache", e);
						}	
					}
				}
			}
			return sipDialog;
		}
	}		

	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#putDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	@Override
	public SIPDialog putDialog(SIPDialog dialog) {
		return super.putDialog(dialog);
		// not needed it was causing the dialog to be put in the cache even for 1xx with a to tag
//		if (!sipCache.inLocalMode() && DialogState.CONFIRMED == dialog.getState()) {
//			// only replicate dialogs in confirmed state
//			putDialogIntoDistributedCache(dialog);
//		}			
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#removeDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	@Override
	public void removeDialog(SIPDialog dialog) {
		if (!sipCache.inLocalMode()) {
			removeDialogFromDistributedCache(dialog.getDialogId());
		}
		super.removeDialog(dialog);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#remoteDialogRemoval(java.lang.String)
	 */
	public void remoteDialogRemoval(String dialogId) {
		// note we don't want a dialog terminated event, thus we need to go directly to map removal
		// assuming it's a confirmed dialog there is no chance it is on early dialogs too
		if (getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + 
					" remote Dialog Removal of dialogId : " + dialogId);
		}
		SIPDialog sipDialog = super.dialogTable.remove(dialogId);
		if (sipDialog != null) {
			String mergeId = sipDialog.getMergeId();
			if (mergeId != null) {
				super.serverDialogMergeTestTable.remove(mergeId);
			}			
		}
	}
	
	/**
	 * Retrieve the dialog from the distributed cache
	 * @param dialogId the id of the dialog to fetch
	 * @return the SIPDialog from the distributed cache, null if nothing has been found in the cache
	 */
	protected  SIPDialog getDialogFromDistributedCache(String dialogId) {
		if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + " checking if the dialog " + dialogId + " is present in the distributed cache");
		}	
		// fetch the corresponding dialog from the cache instance
		SIPDialog sipDialog = null;
		try {
			sipDialog = sipCache.getDialog(dialogId);
		} catch (SipCacheException e) {
			getStackLogger().logError("sipStack " + this + " problem getting dialog " + dialogId + " from the distributed cache", e);
		}
		if(sipDialog != null) {			
			if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStackLogger().logDebug("sipStack " + this + " dialog " + dialogId + " was present in the distributed cache, initializing it after the load");
			}
			((HASipDialog)sipDialog).initAfterLoad(this);
		}
		return sipDialog;
	}
	/**
	 * Store the dialog into the distributed cache
	 * @param dialog the dialog to store
	 */
	protected  void putDialogIntoDistributedCache(SIPDialog dialog) {
		String dialogId = dialog.getDialogId();	
		if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + " storing the dialog " + dialogId + " in the distributed cache");
		}
		// put the corresponding dialog into the cache instance
		try {
			sipCache.putDialog(dialog);
		} catch (SipCacheException e) {
			getStackLogger().logError("sipStack " + this + " problem storing the dialog " + dialogId + " into the distributed cache", e);
		}
	}
	
	/**
	 * Remove the dialog from the distributed cache
	 * @param dialogId the id of the dialog to remove
	 */
	protected  void removeDialogFromDistributedCache(String dialogId) {
		if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + " removing the dialog " + dialogId + " from the distributed cache");
		}
		// remove the corresponding dialog from the cache instance
		// put the corresponding dialog into the cache instance
		try {
			sipCache.removeDialog(dialogId);
		} catch (SipCacheException e) {
			getStackLogger().logError("sipStack " + this + " problem removing dialog " + dialogId + " from the distributed cache", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see gov.nist.javax.sip.stack.SIPTransactionStack#findTransaction(java.lang.String, boolean)
	 */
	@Override
	public SIPTransaction findTransaction(String transactionId, boolean isServer) {
		if(sipCache.inLocalMode() || replicationStrategy != ReplicationStrategy.EarlyDialog) {
			return super.findTransaction(transactionId,isServer);
		}
		final String txId = transactionId.toLowerCase();
		SIPTransaction sipTransaction = super.findTransaction(txId, isServer);
		if(sipTransaction == null && transactionFactory != null) {
			if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStackLogger().logDebug("local transaction " + txId + " server = " + isServer + " is null, checking in the distributed cache");
			}
			if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
				getStackLogger().logDebug("sipStack " + this + " checking if the transaction " + txId + " server = " + isServer + " is present in the distributed cache");
			}	
			if(isServer) {
				// fetch the corresponding server transaction from the cache instance
				try {
					sipTransaction = sipCache.getServerTransaction(txId);
					if(sipTransaction != null) {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("sipStack " + this + " transaction " + txId + " server = " + isServer + " is present in the distributed cache");
						}	
						SIPServerTransaction retval = serverTransactionTable.putIfAbsent(txId, (SIPServerTransaction) sipTransaction);
						if(retval != null) {
							sipTransaction = retval;
						}
					} else {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("sipStack " + this + " transaction " + txId + " server = " + isServer + " is not present in the distributed cache");
						}	
					}
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + txId + " server = " + isServer + " from the distributed cache", e);
				}
			} else {
				// fetch the corresponding client transaction from the cache instance
				try {
					sipTransaction = sipCache.getClientTransaction(txId);
					if(sipTransaction != null) {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("sipStack " + this + " transaction " + txId + " server = " + isServer + " is present in the distributed cache");
						}	
						SIPClientTransaction retval = clientTransactionTable.putIfAbsent(txId, (SIPClientTransaction) sipTransaction);
						if(retval != null) {
							sipTransaction = retval;							
						} else {
							// start the transaction timer only when the transaction has been added to the stack
							// to avoid leaks on retransmissions
							((MobicentsHASIPClientTransaction)sipTransaction).startTransactionTimerOnFailover();
						}
					} else {
						if(getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
							getStackLogger().logDebug("sipStack " + this + " transaction " + txId + " server = " + isServer + " is not present in the distributed cache");
						}	
					}
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + txId + " server = " + isServer + " from the distributed cache", e);
				}
			}
		}
		return sipTransaction;
	}
	
	@Override
	public void removeTransaction(SIPTransaction sipTransaction) {
		super.removeTransaction(sipTransaction);
		if(sipCache.inLocalMode()) {
			return;
		}
		if(transactionFactory != null && sipTransaction != null && replicationStrategy == ReplicationStrategy.EarlyDialog && sipTransaction.getMethod().equalsIgnoreCase(Request.INVITE)) {
			if(sipTransaction instanceof ServerTransaction) {
				// remove the corresponding server transaction from the cache instance
				try {
					sipCache.removeServerTransaction(sipTransaction.getTransactionId());
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + sipTransaction.getTransactionId() + " from the distributed cache", e);
				}
			} else {
				// remove the corresponding client transaction from the cache instance
				try {
					sipCache.removeClientTransaction(sipTransaction.getTransactionId());
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + sipTransaction.getTransactionId() + " from the distributed cache", e);
				}
			}
		}
	}
	
	@Override
	protected void removeTransactionHash(SIPTransaction sipTransaction) {
		super.removeTransactionHash(sipTransaction);
		if(sipCache.inLocalMode()) {
			return;
		}
		if(transactionFactory != null && sipTransaction != null && replicationStrategy == ReplicationStrategy.EarlyDialog && sipTransaction.getMethod().equalsIgnoreCase(Request.INVITE)) {
			if(sipTransaction instanceof ServerTransaction) {
				// remove the corresponding server transaction from the cache instance
				try {
					sipCache.removeServerTransaction(sipTransaction.getTransactionId());
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + sipTransaction.getTransactionId() + " from the distributed cache", e);
				}
			} else {
				// remove the corresponding client transaction from the cache instance
				try {
					sipCache.removeClientTransaction(sipTransaction.getTransactionId());
				} catch (SipCacheException e) {
					getStackLogger().logError("sipStack " + this + " problem getting transaction " + sipTransaction.getTransactionId() + " from the distributed cache", e);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#setSipCache(org.mobicents.ha.javax.sip.cache.SipCache)
	 */
	public void setSipCache(SipCache sipCache) {
		this.sipCache = sipCache;
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#getSipCache()
	 */
	public SipCache getSipCache() {
		return sipCache;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#getLoadBalancerHeartBeatingService()
	 */
	public LoadBalancerHeartBeatingService getLoadBalancerHeartBeatingService() {
		return loadBalancerHeartBeatingService;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#getReplicationStrategy()
	 */
	public ReplicationStrategy getReplicationStrategy() {
		return replicationStrategy;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#getLoadBalancerElector()
	 */
	public LoadBalancerElector getLoadBalancerElector() {
		return loadBalancerElector;
	}
	
	@Override
	public SIPClientTransaction createClientTransaction(SIPRequest sipRequest,
			MessageChannel encapsulatedMessageChannel) {
		if(sipCache.inLocalMode() || transactionFactory == null) {
			return super.createClientTransaction(sipRequest, encapsulatedMessageChannel);
		}
		return transactionFactory.createClientTransaction(sipRequest, encapsulatedMessageChannel);
	}
	
	@Override
	public SIPServerTransaction createServerTransaction(MessageChannel encapsulatedMessageChannel) {
		if(sipCache.inLocalMode() || transactionFactory == null) {
			return super.createServerTransaction(encapsulatedMessageChannel);
		}
		return transactionFactory.createServerTransaction(encapsulatedMessageChannel);
	}

	/**
	 * @param sendTryingRightAway the sendTryingRightAway to set
	 */
	public void setSendTryingRightAway(boolean sendTryingRightAway) {
		this.sendTryingRightAway = sendTryingRightAway;
	}

	/**
	 * @return the sendTryingRightAway
	 */
	public boolean isSendTryingRightAway() {
		return sendTryingRightAway;
	}
	
	public void addSipProvider(SipProviderImpl sipProvider) {
		sipProviders.add(sipProvider);
	}
	
	public void removeSipProvider(SipProviderImpl sipProvider) {
		sipProviders.remove(sipProvider);
	}

	/**
	 * @return the replicateApplicationData
	 */
	public boolean isReplicateApplicationData() {
		return replicateApplicationData;
	}  
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#remoteServerTransactionRemoval(java.lang.String)
	 */
	public void remoteServerTransactionRemoval(String transactionId) {
		final String txId = transactionId.toLowerCase();
		// note we don't want a dialog terminated event, thus we need to go directly to map removal
		// assuming it's a confirmed dialog there is no chance it is on early dialogs too
		if (getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + 
					" remote Server Transaction Removal of transaction Id : " + txId);
		}
		// the transaction id is set to lower case in the cache so it might not remove it correctly
		SIPServerTransaction sipServerTransaction = super.serverTransactionTable.remove(txId);
		if (sipServerTransaction != null) {
			super.removeFromMergeTable(sipServerTransaction);
			super.removePendingTransaction(sipServerTransaction);
			super.removeTransactionPendingAck(sipServerTransaction);
		}
	}
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.ClusteredSipStack#remoteClientTransactionRemoval(java.lang.String)
	 */
	public void remoteClientTransactionRemoval(String transactionId) {
		final String txId = transactionId.toLowerCase();
		// note we don't want a dialog terminated event, thus we need to go directly to map removal
		// assuming it's a confirmed dialog there is no chance it is on early dialogs too
		if (getStackLogger().isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			getStackLogger().logDebug("sipStack " + this + 
					" remote Client Transaction Removal of transaction Id : " + txId);
		}
		// the transaction id is set to lower case in the cache so it might not remove it correctly
		SIPClientTransaction sipClientTransaction = super.clientTransactionTable.remove(txId);		
	}
	
	public SipProvider createSipProvider(ListeningPoint listeningPoint)
			throws ObjectInUseException {
		if (sipProviderFactory != null) {
			return sipProviderFactory.createSipProvider(listeningPoint);
		}
		return super.createSipProvider(listeningPoint);
	}  
}	