/**
 * 
 */
package org.mobicents.ha.javax.sip;

import gov.nist.core.StackLogger;

import javax.sip.address.Address;
import javax.sip.address.AddressFactory;

/**
 * Module for the SIP Load Balancer Client, which manages the active
 * {@link SipLoadBalancer}s, caching {@link Address} objects and providing a
 * concrete strategy on election.
 * 
 * @author martins
 * 
 */
public interface LoadBalancerElector {

	public static String IMPLEMENTATION_CLASS_NAME_PROPERTY = LoadBalancerElector.class.getName();
	
	/**
	 * 
	 * Retrieves the {@link Address} of an active {@link SipLoadBalancer}, to be
	 * cloned and used in outbonud JAIN SIP Messages. Use {@link getLoadBalancerExt} instead.
	 * 
	 * @return
	 */
	@Deprecated
	public Address getLoadBalancer();
	
	/**
	 * Retrieves a load balancer structure {@link SipLoadBalancer} with cached reusable route headers.
	 * 
	 * @return
	 */
	public SipLoadBalancer getLoadBalancerExt();

	/**
	 * Sets the {@link AddressFactory} to be used when creating {@link Address}
	 * objects.
	 * 
	 * @param addressFactory
	 * @throws NullPointerException
	 *             if the addressFactory specified is null
	 */
	public void setAddressFactory(AddressFactory addressFactory)
			throws NullPointerException;

	/**
	 * Sets the service to be used by the elector. The elector's {@link Address}
	 * objects will be reset with the balancers provided by the service.
	 * 
	 * @param service
	 * @throws IllegalStateException
	 *             if the address factory or the stack logger is null
	 * @throws NullPointerException
	 *             if the service specified is null
	 */
	public void setService(LoadBalancerHeartBeatingService service)
			throws IllegalStateException, NullPointerException;

	/**
	 * Sets the {@link StackLogger} logger.
	 * 
	 * @param logger
	 * @throws NullPointerException
	 *             if the logger specified is null
	 */
	public void setStackLogger(StackLogger logger) throws NullPointerException;

}
