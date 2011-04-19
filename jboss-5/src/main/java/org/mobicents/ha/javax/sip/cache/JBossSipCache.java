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

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.stack.SIPClientTransaction;
import gov.nist.javax.sip.stack.SIPDialog;
import gov.nist.javax.sip.stack.SIPServerTransaction;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.UserTransaction;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.config.Configuration.CacheMode;
import org.mobicents.ha.javax.sip.ClusteredSipStack;
import org.mobicents.ha.javax.sip.ReplicationStrategy;

/**
 * Implementation of the SipCache interface, backed by a JBoss Cache 3.X Cache.
 * The configuration of JBoss Cache can be set throught the following Mobicents SIP Stack property :
 * <b>org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH</b>
 * 
 * @author jean.deruelle@gmail.com
 *
 */
public class JBossSipCache implements SipCache {
	public static final String JBOSS_CACHE_CONFIG_PATH = "org.mobicents.ha.javax.sip.JBOSS_CACHE_CONFIG_PATH";
	public static final String DEFAULT_FILE_CONFIG_PATH = "META-INF/cache-configuration.xml"; 
	private static StackLogger clusteredlogger = CommonLogger.getLogger(JBossSipCache.class);
	ClusteredSipStack clusteredSipStack = null;
	Properties configProperties = null;	
	
	protected Cache cache;
	protected JBossJainSipCacheListener cacheListener;
	
	protected Node<String, SIPDialog> dialogRootNode = null;
	protected Node<String, SIPClientTransaction> clientTxRootNode = null;
	protected Node<String, SIPServerTransaction> serverTxRootNode = null;
	
	/**
	 * 
	 */
	public JBossSipCache() {}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#getDialog(java.lang.String)
	 */
	public SIPDialog getDialog(String dialogId) throws SipCacheException {		
		try {
			Node dialogNode = ((Node) dialogRootNode.getChild(Fqn.fromString(dialogId)));
			if(dialogNode != null) {
				return (SIPDialog) dialogNode.get(dialogId);
			} else {
				return null;
			}
		} catch (CacheException e) {
			throw new SipCacheException("A problem occured while retrieving the following dialog " + dialogId + " from JBoss Cache", e);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#updateDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void updateDialog(SIPDialog sipDialog) throws SipCacheException {
		Node dialogNode = ((Node) dialogRootNode.getChild(Fqn.fromString(sipDialog.getDialogId())));
		if(dialogNode != null) {
			if(dialogNode != null) {
				sipDialog = (SIPDialog) dialogNode.get(sipDialog.getDialogId());
			}
		}
	}		

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#putDialog(gov.nist.javax.sip.stack.SIPDialog)
	 */
	public void putDialog(SIPDialog dialog) throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			Node dialogNode = dialogRootNode.addChild(Fqn.fromString(dialog.getDialogId()));
			dialogNode.put(dialog.getDialogId(), dialog);
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while putting the following dialog " + dialog.getDialogId() + "  into JBoss Cache", e);
		} 
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#removeDialog(java.lang.String)
	 */
	public void removeDialog(String dialogId) throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			dialogRootNode.removeChild(dialogId);
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while removing the following dialog " + dialogId + " from JBoss Cache", e);
		}
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#setClusteredSipStack(org.mobicents.ha.javax.sip.ClusteredSipStack)
	 */
	public void setClusteredSipStack(ClusteredSipStack clusteredSipStack) {
		this.clusteredSipStack  = clusteredSipStack;
	}

	/* (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#setConfigurationProperties(java.util.Properties)
	 */
	public void setConfigurationProperties(Properties configurationProperties) {
		this.configProperties = configurationProperties;
	}

	public void init() throws SipCacheException {
		String pojoConfigurationPath = configProperties.getProperty(JBOSS_CACHE_CONFIG_PATH, DEFAULT_FILE_CONFIG_PATH);
		if (clusteredlogger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			clusteredlogger.logInfo(
					"Mobicents JAIN SIP JBoss Cache Configuration path is : " + pojoConfigurationPath);
		}
		try {
			cache = new DefaultCacheFactory<String, SIPDialog>().createCache(pojoConfigurationPath);
			cache.create();
			cacheListener = new JBossJainSipCacheListener(clusteredSipStack);
			cache.addCacheListener(cacheListener);			
		} catch (Exception e) {
			throw new SipCacheException("Couldn't init JBoss Cache", e);
		}
	}

	public void start() throws SipCacheException {
		try {
			cache.start();			
		} catch (Exception e) {
			throw new SipCacheException("Couldn't start JBoss Cache", e);
		}
		dialogRootNode = cache.getRoot().getChild(SipCache.DIALOG_PARENT_FQN_ELEMENT);
		if(dialogRootNode == null) {
			dialogRootNode = cache.getRoot().addChild(Fqn.fromElements(SipCache.DIALOG_PARENT_FQN_ELEMENT));	
		}
		if(clusteredSipStack.getReplicationStrategy() == ReplicationStrategy.EarlyDialog) {
			serverTxRootNode = cache.getRoot().getChild(SipCache.SERVER_TX_PARENT_FQN_ELEMENT);
			if(serverTxRootNode == null) {
				serverTxRootNode = cache.getRoot().addChild(Fqn.fromElements(SipCache.SERVER_TX_PARENT_FQN_ELEMENT));	
			}
		}
	}

	public void stop() throws SipCacheException {
		cache.stop();		
		cache.destroy();
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.cache.SipCache#inLocalMode()
	 */
	public boolean inLocalMode() {
		return cache.getConfiguration().getCacheMode() == CacheMode.LOCAL;
	}

	public void evictDialog(String dialogId) {
		cache.evict(Fqn.fromElements(dialogRootNode.getFqn(), Fqn.fromString(dialogId)));
	}

	public SIPServerTransaction getServerTransaction(String transactionId)
			throws SipCacheException {
		try {
			Node serverTransactionNode = ((Node) serverTxRootNode.getChild(Fqn.fromString(transactionId)));
			if(serverTransactionNode != null) {
				return (SIPServerTransaction) serverTransactionNode.get(transactionId);
			} else {
				return null;
			}
		} catch (CacheException e) {
			throw new SipCacheException("A problem occured while retrieving the following server transaction " + transactionId + " from JBoss Cache", e);
		}
	}

	public void putServerTransaction(SIPServerTransaction serverTransaction)
			throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			Node serverTransactionNode = serverTxRootNode.addChild(Fqn.fromString(serverTransaction.getTransactionId()));
			serverTransactionNode.put(serverTransaction.getTransactionId(), serverTransaction);
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while putting the following server transaction " + serverTransaction.getTransactionId() + "  into JBoss Cache", e);
		} 
	}

	public void removeServerTransaction(String transactionId)
			throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop).lookup("UserTransaction");
			if(tx != null) {
				tx.begin();
			}
			serverTxRootNode.removeChild(transactionId);
			if(tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if(tx != null) {
				try { tx.rollback(); } catch(Throwable t) {}
			}
			throw new SipCacheException("A problem occured while removing the following server transaction " + transactionId + " from JBoss Cache", e);
		}
	}
	
	public SIPClientTransaction getClientTransaction(String transactionId)
			throws SipCacheException {
		try {
			Node clientTransactionNode = ((Node) clientTxRootNode.getChild(Fqn
					.fromString(transactionId)));
			if (clientTransactionNode != null) {
				return (SIPClientTransaction) clientTransactionNode
						.get(transactionId);
			} else {
				return null;
			}
		} catch (CacheException e) {
			throw new SipCacheException(
					"A problem occured while retrieving the following client transaction "
							+ transactionId + " from JBoss Cache", e);
		}
	}

	public void putClientTransaction(SIPClientTransaction clientTransaction)
			throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY,
					"org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop)
					.lookup("UserTransaction");
			if (tx != null) {
				tx.begin();
			}
			Node clientTransactionNode = clientTxRootNode.addChild(Fqn
					.fromString(clientTransaction.getTransactionId()));
			clientTransactionNode.put(clientTransaction.getTransactionId(),
					clientTransaction);
			if (tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if (tx != null) {
				try {
					tx.rollback();
				} catch (Throwable t) {
				}
			}
			throw new SipCacheException(
					"A problem occured while putting the following client transaction "
							+ clientTransaction.getTransactionId()
							+ "  into JBoss Cache", e);
		}
	}

	public void removeClientTransaction(String transactionId)
			throws SipCacheException {
		UserTransaction tx = null;
		try {
			Properties prop = new Properties();
			prop.put(Context.INITIAL_CONTEXT_FACTORY,
					"org.jboss.cache.transaction.DummyContextFactory");
			tx = (UserTransaction) new InitialContext(prop)
					.lookup("UserTransaction");
			if (tx != null) {
				tx.begin();
			}
			clientTxRootNode.removeChild(transactionId);
			if (tx != null) {
				tx.commit();
			}
		} catch (Exception e) {
			if (tx != null) {
				try {
					tx.rollback();
				} catch (Throwable t) {
				}
			}
			throw new SipCacheException(
					"A problem occured while removing the following client transaction "
							+ transactionId + " from JBoss Cache", e);
		}
	}	
}
