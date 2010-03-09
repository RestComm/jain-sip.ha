/*
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

import java.io.IOException;
import java.util.Properties;

/**
 * Interface for services that want to ping Mobicents SIP Load Balancers through RMI to send keepalives and health status information to them
 * So that the service is started the stack configuration properties should have the following property org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingServiceClassName
 * with the value of the class name implementing this interface to instantiate (a no arg construcotr will be called)
 * 
 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
 *
 */
public interface LoadBalancerHeartBeatingService {

	final static String BALANCERS = "org.mobicents.ha.javax.sip.BALANCERS";
	final static String HEARTBEAT_INTERVAL = "org.mobicents.ha.javax.sip.HEARTBEAT_INTERVAL";
	final static String LB_HB_SERVICE_CLASS_NAME = "org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingServiceClassName";
	
	void init(ClusteredSipStack clusteredSipStack, Properties stackProperties);
	
	void start();
	
	void stop();
	
	/**
	 * 
	 * @return - list of String objects representing balancer addresses. Example
	 *         content:
	 *         <ul>
	 *         <li>192.168.1.100</li>
	 *         <li>ala.ma.kota.pl</li>
	 *         </ul>
	 */
	String[] getBalancers();

	boolean addBalancer(String addr, int sipPort, int rmiPort)
			throws IllegalArgumentException, NullPointerException, IOException;

	/**
	 * Adds balancer address to distribution list. Tries to connect to it.
	 * 
	 * @param hostName -
	 *            name of the host to be looked up in DNS
	 * @param index -
	 *            possible index of IP address when host has more than one
	 *            address - like InetAddress.getAllByName(..);
	 * @return
	 *            <ul>
	 *            <li><b>true</b> - if address didnt exist and it has been
	 *            injected into list</li>
	 *            <li><b>false</b> - otherwise</li>
	 *            </ul>
	 * @throws IllegalArgumentException if something goes wrong when adding the balancer address or while trying to connect to it
	 */
	boolean addBalancer(String hostName, int sipPort, int index, int rmiPort)
			throws IllegalArgumentException;
	
	/**
	 * Get the load balancers objects that were created based on the balancers string AND
	 * the balancers added later.
	 * 
	 * @return
	 */
	SipLoadBalancer[] getLoadBalancers();

	/**
	 * Tries to remove balancer with name: addr[0].addr[1].addr[2].addr[3]
	 * 
	 * @param addr -
	 *            The argument is address representation in network byte order:
	 *            the highest order byte of the address is in [0].
	 * @param port -
	 *            port on which remote balancer listens
	 * @return
	 *            <ul>
	 *            <li><b>true</b> - if name exists and was removed</li>
	 *            <li><b>false</b> - otherwise</li>
	 *            </ul>
	 * @throws IllegalArgumentException -
	 *             if there is no balancer with that name on the list.
	 */
	boolean removeBalancer(String addr, int sipPort, int rmiPort)
			throws IllegalArgumentException;

	boolean removeBalancer(String hostName, int sipPort, int index, int rmiPort)
			throws IllegalArgumentException;

	
	void sendSwitchoverInstruction(SipLoadBalancer sipLoadBalancer, String fromJvmRoute, String toJvmRoute);
	
	// --------------- GETTERS AND SETTERS

	long getHeartBeatInterval();

	void setHeartBeatInterval(long heartBeatInterval);
	
	void setJvmRoute(String jvmRoute);
	String getJvmRoute();
	
	void addLoadBalancerHeartBeatingListener(LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener);
	void removeLoadBalancerHeartBeatingListener(LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener);
}
