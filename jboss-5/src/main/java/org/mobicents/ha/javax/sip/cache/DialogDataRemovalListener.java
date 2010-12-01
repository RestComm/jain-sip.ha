package org.mobicents.ha.javax.sip.cache;

import org.jboss.cache.Fqn;
import org.mobicents.ha.javax.sip.ClusteredSipStack;

/**
 * 
 * @author martins
 * 
 */
public class DialogDataRemovalListener implements
		org.mobicents.cluster.DataRemovalListener {

	/**
	 * 
	 */
	private final Fqn baseFqn;

	/**
	 * 
	 */
	private final ClusteredSipStack clusteredSipStack;

	/**
	 * 
	 * @param baseFqn
	 * @param clusteredSipStack
	 */
	public DialogDataRemovalListener(Fqn baseFqn,
			ClusteredSipStack clusteredSipStack) {
		this.baseFqn = baseFqn;
		this.clusteredSipStack = clusteredSipStack;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.cluster.DataRemovalListener#dataRemoved(org.jboss.cache
	 * .Fqn)
	 */
	@SuppressWarnings("unchecked")
	public void dataRemoved(Fqn fqn) {
		clusteredSipStack.remoteServerTransactionRemoval((String) fqn.getLastElement());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.cluster.DataRemovalListener#getBaseFqn()
	 */
	@SuppressWarnings("unchecked")
	public Fqn getBaseFqn() {
		return baseFqn;
	}

}
