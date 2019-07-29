/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.ha.javax.sip;

import gov.nist.core.CommonLogger;
import gov.nist.core.StackLogger;
import gov.nist.javax.sip.ListeningPointExt;

import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sip.ListeningPoint;

import org.mobicents.tools.sip.balancer.NodeRegisterRMIStub;
import org.mobicents.tools.sip.balancer.SIPNode;

/**
 *  <p>implementation of the <code>LoadBalancerHeartBeatingService</code> interface.</p>
 *     
 *  <p>
 *  It sends heartbeats and health information to the sip balancers configured per connector so it can support multiple network interfaces and transports at the same time for outbound traffic
 *  See https://github.com/RestComm/jain-sip.ha/issues/9 
 *  </p>
 * 
 * @author <A HREF="mailto:jean.deruelle@telestax.com">Jean Deruelle</A> 
 *
 */
public class MultiNetworkLoadBalancerHeartBeatingServiceImpl implements LoadBalancerHeartBeatingService, MultiNetworkLoadBalancerHeartBeatingServiceImplMBean {

	private static StackLogger logger = CommonLogger.getLogger(MultiNetworkLoadBalancerHeartBeatingServiceImpl.class);
	public static String LB_HB_SERVICE_MBEAN_NAME = "org.mobicents.jain.sip:type=load-balancer-heartbeat-service,name=";
    public final static String REACHABLE_CHECK = "org.mobicents.ha.javax.sip.REACHABLE_CHECK";
    public final static String LOCAL_HTTP_PORT = "org.mobicents.ha.javax.sip.LOCAL_HTTP_PORT";
    public final static String LOCAL_SSL_PORT = "org.mobicents.ha.javax.sip.LOCAL_SSL_PORT";
    public final static String LOCAL_SMPP_PORT = "org.mobicents.ha.javax.sip.LOCAL_SMPP_PORT";
    public final static String LOCAL_SMPP_SSL_PORT = "org.mobicents.ha.javax.sip.LOCAL_SMPP_SSL_PORT";
    public final static String SOCKET_BINDING_GROUP = "org.mobicents.ha.javax.sip.SOCKET_BINDING_GROUP";
	
	public static final int DEFAULT_RMI_PORT = 2000;
	public static final String BALANCER_SIP_PORT_CHAR_SEPARATOR = ":";
	public static final String BALANCERS_CHAR_SEPARATOR = ";";
	public static final int DEFAULT_LB_SIP_PORT = 5065;		
	public static final int DEFAULT_LB_HTTP_PORT = 2080;
	
	ClusteredSipStack sipStack = null;
    Properties sipStackProperties = null;
	//the jvmRoute for this node
	protected String jvmRoute;
    //the balancers names to send heartbeat to and our health info
	// https://github.com/RestComm/jain-sip.ha/issues/4 : 
	// Caching the sipNodes to send to the LB as there is no reason for them to change often or at all after startup
	protected Map<SipLoadBalancer, ConcurrentHashMap<String, SIPNode>> register = new ConcurrentHashMap<SipLoadBalancer, ConcurrentHashMap<String, SIPNode>>();
	protected Map<SipLoadBalancer, Set<ListeningPoint>> connectors = new ConcurrentHashMap<SipLoadBalancer, Set<ListeningPoint>>();
	//heartbeat interval, can be modified through JMX
	protected long heartBeatInterval = 5000;
	protected Timer heartBeatTimer = new Timer();
	protected TimerTask hearBeatTaskToRun = null;
	protected List<String> cachedAnyLocalAddresses = new ArrayList<String>();
	protected boolean started = false;
    protected boolean gracefullyShuttingDown = false;
    protected boolean reachableCheck = true;

	protected Set<LoadBalancerHeartBeatingListener> loadBalancerHeartBeatingListeners;
	// https://github.com/RestComm/jain-sip.ha/issues/3
	protected boolean useLoadBalancerForAllConnectors;
	
	ObjectName oname = null;
	
    public MultiNetworkLoadBalancerHeartBeatingServiceImpl() {
		loadBalancerHeartBeatingListeners = new CopyOnWriteArraySet<LoadBalancerHeartBeatingListener>();
		useLoadBalancerForAllConnectors = true;
	}
    
	public void init(ClusteredSipStack clusteredSipStack,
			Properties stackProperties) {
		sipStack = clusteredSipStack;
        sipStackProperties = stackProperties;
		heartBeatInterval = Integer.parseInt(stackProperties.getProperty(HEARTBEAT_INTERVAL, "5000"));
        String reachableCheckString = sipStackProperties.getProperty(REACHABLE_CHECK);
        if(reachableCheckString != null) {
            reachableCheck = Boolean.parseBoolean(reachableCheckString);
        }
        logger.logInfo("Reachable checks " + reachableCheck);
	}
	
	public void stopBalancer() {
		stop();
	}
    
    public void start() {
      	Runtime.getRuntime().addShutdownHook(new Thread() {
    	    public void run() {
    	    	stopBalancer();
    	    	logger.logInfo("Shutting down the Load Balancer Link");
            }
    	});

    	if (!started) {
			started = true;
		}
//    	if(sipNodes.isEmpty()) {
//    		logger.logInfo("Computing SIP Nodes to be sent to the LB");
//    		updateConnectorsAsSIPNode();
//		}
		this.hearBeatTaskToRun = new BalancerPingTimerTask();
		
		// Delay the start with 2 seconds so nodes joining under load are really ready to serve requests
		// Otherwise one of the listeneing points comes a bit later and results in errors.
//		this.heartBeatTimer.scheduleAtFixedRate(this.hearBeatTaskToRun, this.heartBeatInterval,
//				this.heartBeatInterval);
//		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
//			logger.logDebug("Created and scheduled tasks for sending heartbeats to the sip balancer every " + heartBeatInterval + "ms.");
//		}
		
		registerMBean();
		
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Load Balancer Heart Beating Service has been started");
		}
    }
    
    public void startLoadBalancerHeartBeatingService()
    {
    	this.heartBeatTimer.scheduleAtFixedRate(this.hearBeatTaskToRun, this.heartBeatInterval,
				this.heartBeatInterval);
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Created and scheduled tasks for sending heartbeats to the sip balancer every " + heartBeatInterval + "ms.");
		}
    }
    
    public void stop() {
    	// Force removal from load balancer upon shutdown 
    	// added for Issue 308 (http://code.google.com/p/mobicents/issues/detail?id=308)
    	removeNodesFromBalancers();
    	//cleaning 
//    	balancerNames.clear();
    	register.clear();
    	if(hearBeatTaskToRun != null) {
    		this.hearBeatTaskToRun.cancel();
    	}
		this.hearBeatTaskToRun = null;
		loadBalancerHeartBeatingListeners.clear();
		started = false;
		
		heartBeatTimer.cancel();
		
		unRegisterMBean();
		
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Load Balancer Heart Beating Service has been stopped");
		}
    }
    
    protected void registerMBean() {
    	String mBeanName = LB_HB_SERVICE_MBEAN_NAME + sipStack.getStackName();
		try {
			oname = new ObjectName(mBeanName);
			if (sipStack.getMBeanServer() != null && !sipStack.getMBeanServer().isRegistered(oname)) {
				sipStack.getMBeanServer().registerMBean(this, oname);				
			}
		} catch (Exception e) {
			logger.logError("Could not register the Load Balancer Service as an MBean under the following name " + mBeanName, e);			
		}		
	}
	
	protected void unRegisterMBean() {
		String mBeanName = LB_HB_SERVICE_MBEAN_NAME + sipStack.getStackName();
		try {
			if (oname != null && sipStack.getMBeanServer() != null && sipStack.getMBeanServer().isRegistered(oname)) {
				sipStack.getMBeanServer().unregisterMBean(oname);
			}
		} catch (Exception e) {
			logger.logError("Could not unregister the stack as an MBean under the following name" + mBeanName);
		}		
	}	
	
    /**
     * {@inheritDoc}
     */
	public long getHeartBeatInterval() {
		return heartBeatInterval;
	}
	/**
     * {@inheritDoc}
     */
	public void setHeartBeatInterval(long heartBeatInterval) {
		if (heartBeatInterval < 100)
			return;
		
		if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
			logger.logDebug("Setting HeartBeatInterval from " + this.heartBeatInterval + " to " + heartBeatInterval);
		}
		
		this.heartBeatInterval = heartBeatInterval;
//		if(sipNodes.isEmpty()) {
//			logger.logInfo("Computing SIP Nodes to be sent to the LB");
//			updateConnectorsAsSIPNode();
//		}
		this.hearBeatTaskToRun.cancel();
		this.hearBeatTaskToRun = new BalancerPingTimerTask();
		this.heartBeatTimer.scheduleAtFixedRate(this.hearBeatTaskToRun, 0,
				this.heartBeatInterval);

	}

	/**
	 * 
	 * @param hostName
	 * @param index
	 * @return
	 */
	private InetAddress fetchHostAddress(String hostName, int index) {
		if (hostName == null)
			throw new NullPointerException("Host name cant be null!!!");

		InetAddress[] hostAddr = null;
		try {
			hostAddr = InetAddress.getAllByName(hostName);
		} catch (UnknownHostException uhe) {
			throw new IllegalArgumentException(
					"HostName is not a valid host name or it doesnt exists in DNS",
					uhe);
		}

		if (index < 0 || index >= hostAddr.length) {
			throw new IllegalArgumentException(
					"Index in host address array is wrong, it should be [0]<x<["
							+ hostAddr.length + "] and it is [" + index + "]");
		}

		InetAddress address = hostAddr[index];
		return address;
	}

	/**
     * {@inheritDoc}
     */
	public String[] getBalancers() {
		return this.register.keySet().toArray(new String[register.keySet().size()]);
	}

	/**
     * {@inheritDoc}
     */
	public boolean addBalancer(String addr, int sipPort, int httpPort, int rmiPort) {
		throw new UnsupportedOperationException("This algorithm only allows to add a Load Balancer tied to a sip connector");
	}

	/**
     * {@inheritDoc}
     */
	public boolean addBalancer(String hostName, int sipPort, int httpPort, int index, int rmiPort) {
		throw new UnsupportedOperationException("This algorithm only allows to add a Load Balancer tied to a sip connector");
	}

	/**
     * {@inheritDoc}
     */
	public boolean removeBalancer(String addr, int sipPort, int httpPort, int rmiPort) {
		throw new UnsupportedOperationException("This algorithm only allows to remove a Load Balancer tied to a sip connector");
	}

	/**
     * {@inheritDoc}
     */
	public boolean removeBalancer(String hostName, int sipPort, int httpPort, int index, int rmiPort) {
		throw new UnsupportedOperationException("This algorithm only allows to remove a Load Balancer tied to a sip connector");
	}

	protected void updateConnectorsAsSIPNode(SipLoadBalancer loadBalancer) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
			logger.logTrace("Gathering all SIP Connectors information. UseLoadBalancer for all connectors: " + useLoadBalancerForAllConnectors);
		}
		// Gathering info about server' sip listening points
		Set<ListeningPoint> listeningPoints = connectors.get(loadBalancer);
		if(listeningPoints == null) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
				logger.logInfo("No listening points defined for " + loadBalancer);
			}
			return;
		}
		Iterator<ListeningPoint> listeningPointIterator = listeningPoints.iterator();
		while (listeningPointIterator.hasNext()) {
			
			Integer sipTcpPort = null;
			Integer sipTlsPort = null;
			Integer sipSctpPort = null;
			Integer sipUdpPort = null;
			Integer sipWsPort = null;
			Integer sipWssPort = null;
			String address = null;
			String hostName = null;

			ListeningPoint listeningPoint = listeningPointIterator.next();
			address = listeningPoint.getIPAddress();
			
			if(address == null) {
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace("Address is null");
				}
			} else {
				// From Vladimir: for some reason I get "localhost" here instead of IP and this confuses the LB
				if(address.equals("localhost")) address = "127.0.0.1";
				
				int port = listeningPoint.getPort();
				String transport = listeningPoint.getTransport();
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace("connector: " + address + ", port: " + port + ", transport: " + transport);
				}
				if(transport.equalsIgnoreCase(ListeningPoint.TCP)) {
					sipTcpPort = port;
				} else if(transport.equalsIgnoreCase(ListeningPoint.UDP)) {
					sipUdpPort = port;
				} else if(transport.equalsIgnoreCase(ListeningPointExt.WS)) {
					sipWsPort = port;
				} else if(transport.equalsIgnoreCase(ListeningPointExt.WSS)) {
					// https://github.com/RestComm/jain-sip.ha/issues/5
					sipWssPort = port;
				} else if(transport.equalsIgnoreCase(ListeningPoint.TLS)) {
					// https://github.com/RestComm/jain-sip.ha/issues/5
					sipTlsPort = port;
				} else if(transport.equalsIgnoreCase(ListeningPoint.SCTP)) {
					// https://github.com/RestComm/jain-sip.ha/issues/5
					sipSctpPort = port;
				}
				
				try {
					if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
						logger.logTrace("Trying to find address array for address " + address);
					}
					InetAddress[] aArray = InetAddress
							.getAllByName(address);
					if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
						for (int i = 0; i < aArray.length; i++) {
							logger.logTrace("Found " + aArray[i].getHostAddress());	
						}
					}
					if (aArray != null && aArray.length > 0) {
						// Damn it, which one we should pick?
						hostName = aArray[0].getCanonicalHostName();
					}
				} catch (UnknownHostException e) {
					logger.logError("An exception occurred while trying to retrieve the hostname of a sip connector", e);
				}
				
				List<String> ipAddresses = new ArrayList<String>();
				boolean isAnyLocalAddress = false;
				try {
					if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
						logger.logTrace("Trying to find if address " + address + " is an AnyLocalAddress");
					}
					isAnyLocalAddress = InetAddress.getByName(address).isAnyLocalAddress();
					if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
						logger.logTrace(address + " is an AnyLocalAddress ? " + isAnyLocalAddress);
					}
				} catch (UnknownHostException e) {
					logger.logWarning("Unable to enumerate mapped interfaces. Binding to 0.0.0.0 may not work.");
					isAnyLocalAddress = false;			
				}	
				if(isAnyLocalAddress) {
					if(cachedAnyLocalAddresses.isEmpty()) {
						try{
							if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
								logger.logTrace("Gathering all network interfaces");
							}
							Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
							if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
								logger.logTrace("network interfaces gathered " + networkInterfaces);
							}
							while(networkInterfaces.hasMoreElements()) {
								NetworkInterface networkInterface = networkInterfaces.nextElement();
								Enumeration<InetAddress> bindings = networkInterface.getInetAddresses();
								while(bindings.hasMoreElements()) {
									InetAddress addr = bindings.nextElement();
									String networkInterfaceIpAddress = addr.getHostAddress();
									// we cache the look up to speed up the next time
									cachedAnyLocalAddresses.add(networkInterfaceIpAddress);
								}
							}
						} catch (SocketException e) {
							logger.logWarning("Unable to enumerate network interfaces. Binding to 0.0.0.0 may not work.");
						}
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
							logger.logTrace("Adding " + cachedAnyLocalAddresses + " to the list of IPs to send");
						}
						ipAddresses.addAll(cachedAnyLocalAddresses);
					}
				} else {
					if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
						logger.logTrace("Adding " + address + " to the list of IPs to send");
					}
					ipAddresses.add(address);
				}	
				
				String httpPortString = sipStackProperties.getProperty(LOCAL_HTTP_PORT);
				String sslPortString = sipStackProperties.getProperty(LOCAL_SSL_PORT);
				String smppPortString = sipStackProperties.getProperty(LOCAL_SMPP_PORT);
				String smppSslPortString = sipStackProperties.getProperty(LOCAL_SMPP_SSL_PORT);

				if(httpPortString == null && sslPortString == null) {
					logger.logWarning("HTTP or HTTPS port couldn't be retrieved from System properties, trying with JMX");
					Integer httpPort = null;
					Boolean httpBound = false;
					Integer sslPort = null;
					Boolean sslBound = false;

					MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
					try {
		                // https://telestax.atlassian.net/browse/JSIPHA-4 defaulting to standard-sockets
		                String socketBindingGroup = sipStackProperties.getProperty(SOCKET_BINDING_GROUP, "standard-sockets");
						ObjectName http = new ObjectName("jboss.as:socket-binding-group=" + socketBindingGroup + ",socket-binding=http");
						httpPort = (Integer) mBeanServer.getAttribute(http, "boundPort");
						httpBound = (Boolean) mBeanServer.getAttribute(http, "bound");

						ObjectName https = new ObjectName("jboss.as:socket-binding-group=" + socketBindingGroup + ",socket-binding=https");
						sslPort = (Integer) mBeanServer.getAttribute(https, "boundPort");
						sslBound = (Boolean) mBeanServer.getAttribute(https, "bound");
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
							logger.logTrace("Dound httpPort " + httpPort + " and sslPort " + sslPort);
						}
					} catch (Exception e) {} //Ignore any exceptions

					if(httpBound && httpPort!=null){
						sipStackProperties.setProperty(LOCAL_HTTP_PORT, String.valueOf(httpPort));
					} else if (sslBound && sslPort!=null){
		                sipStackProperties.setProperty(LOCAL_SSL_PORT, String.valueOf(sslPort));
					}
				}
				
				for (String ipAddress : ipAddresses) {
					if(ipAddress == null) {
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
							logger.logTrace("Following IpAddress [" + ipAddress + "] is null not pinging the LB for that null IP or it will cause routing issues");
						}				
					} else {
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
							logger.logTrace("Creating new SIP Node for [" + ipAddress + "] to be added to the list for pinging the LB");
						}	
						SIPNode node = new SIPNode(hostName, ipAddress);
						ConcurrentHashMap<String, SIPNode> sipNodes = register.get(loadBalancer);
						if(sipNodes == null) {
							sipNodes = new ConcurrentHashMap<String, SIPNode>();
							sipNodes.put(ipAddress, node);
							if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
								logger.logTrace("Added a sip Node with the key [" + ipAddress + "]");
							}
							register.put(loadBalancer, sipNodes);
						} else {
							SIPNode previousValue = sipNodes.putIfAbsent(ipAddress, node);
							if(previousValue == null) {
								if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
									logger.logTrace("Added a sip Node with the key [" + ipAddress + "]");
								}
							} else {
								node = previousValue;
								if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
									logger.logTrace("SIPNode " + node +  " was already present");
								}
							}
						}

						int httpPort = 0;
						int sslPort = 0;
						int smppPort = 0;
						int smppSslPort = 0;
					
						if(httpPortString != null) {
							httpPort = Integer.parseInt(httpPortString);
							node.getProperties().put("httpPort", httpPort);
						}
						if(sslPortString != null) {
							sslPort = Integer.parseInt(sslPortString);
							node.getProperties().put("sslPort", sslPort);
						}
						if(smppPortString != null) {
							smppPort = Integer.parseInt(smppPortString);
							node.getProperties().put("smppPort", smppPort);
						}
						if(smppSslPortString != null) {
							smppSslPort = Integer.parseInt(smppSslPortString);
							node.getProperties().put("smppSslPort", smppSslPort);
						}
					
						if(sipTcpPort != null) node.getProperties().put("tcpPort", sipTcpPort);
						if(sipUdpPort != null) node.getProperties().put("udpPort", sipUdpPort);
						if(sipWsPort != null) node.getProperties().put("wsPort", sipWsPort);
						// https://github.com/RestComm/jain-sip.ha/issues/5
						if(sipWssPort != null) node.getProperties().put("wssPort", sipWssPort);
						if(sipTlsPort != null) node.getProperties().put("tlsPort", sipTlsPort);
						if(sipSctpPort != null) node.getProperties().put("sctpPort", sipTlsPort);
						// https://github.com/RestComm/sip-servlets/issues/172
						if(loadBalancer.getCustomInfo() != null && !loadBalancer.getCustomInfo().isEmpty()) {
							for(Entry<Object, Object> entry : loadBalancer.getCustomInfo().entrySet()) {
								if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
									logger.logDebug("Adding custom info with key " + (String)entry.getKey() + " and value " + (String)entry.getValue());
								}
								node.getProperties().put((String)entry.getKey(), (String)entry.getValue());
							}
						}
						
						if(jvmRoute != null) node.getProperties().put("jvmRoute", jvmRoute);
						
						node.getProperties().put("version", System.getProperty("org.mobicents.server.version", "0"));
						
						if(gracefullyShuttingDown) {
							if(logger.isLoggingEnabled(StackLogger.TRACE_DEBUG)) {
								logger.logDebug("Adding GRACEFUL_SHUTDOWN prop to following SIP Node " + node);
							}
							Map<String, Serializable> properties = node.getProperties();
							properties.put("GRACEFUL_SHUTDOWN", "true");
						}
						
						if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
							logger.logTrace("Added node [" + node + "] to the list for pinging the LB");
						}	
					}
				}		
			}
		}
	}
	
	/**
	 * @param info
	 */
	protected void sendKeepAliveToBalancers() {
		
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		for(SipLoadBalancer loadBalancerToPing: register.keySet()) {
			try {
				ConcurrentHashMap<String, SIPNode> sipNodes = register.get(loadBalancerToPing);
				if(sipNodes.isEmpty()) {
		    		logger.logInfo("Computing SIP Nodes to be sent to the LB as the list is currently empty");
		    		updateConnectorsAsSIPNode(loadBalancerToPing);
				}
				ArrayList<SIPNode> info = new ArrayList<SIPNode>(sipNodes.values());
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
		            logger.logTrace("Pinging balancers with info[" + info + "]");
		        }
				
				long startTime = System.currentTimeMillis();
				Registry registry = LocateRegistry.getRegistry(loadBalancerToPing.getAddress().getHostAddress(), loadBalancerToPing.getRmiPort());
				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
                if(reachableCheck) {
                    ArrayList<SIPNode> reachableInfo = getReachableSIPNodeInfo(loadBalancerToPing.getAddress(), info);
                    info = reachableInfo;
                    if(reachableInfo.isEmpty()) {
                        logger.logWarning("All connectors are unreachable from the balancer");
                    }
                }
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
				    logger.logTrace("Pinging " + loadBalancerToPing + " with the following Node Info [" + info + "]");
				}
				// https://github.com/RestComm/jain-sip.ha/issues/14
				// notify the listeners
				for (LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener : loadBalancerHeartBeatingListeners) {
					loadBalancerHeartBeatingListener.pingingloadBalancer(loadBalancerToPing);
				}
				reg.handlePing(info);
				// notify the listeners
				for (LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener : loadBalancerHeartBeatingListeners) {
					loadBalancerHeartBeatingListener.pingedloadBalancer(loadBalancerToPing);
				}
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
				    logger.logTrace("Pinged " + loadBalancerToPing + "  with the following Node Info [" + info + "]");
				}
				loadBalancerToPing.setDisplayWarning(true);
				if(!loadBalancerToPing.isAvailable()) {
					logger.logInfo("Keepalive: SIP Load Balancer Found! " + loadBalancerToPing);
				}
				loadBalancerToPing.setAvailable(true);
				startTime = System.currentTimeMillis() - startTime;
				if(startTime>200)
					logger.logWarning("Heartbeat sent too slow in " + startTime + " millis at " + System.currentTimeMillis());

				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace("Finished gathering, Gathered info[" + info + "]");
				}
			} catch (IOException e) {
				loadBalancerToPing.setAvailable(false);
				if(loadBalancerToPing.isDisplayWarning()) {
					logger.logWarning("sendKeepAlive: Cannot access the " + loadBalancerToPing + "  RMI registry: " + e.getMessage() +
						"\nIf you need a cluster configuration make sure " + loadBalancerToPing + " is running. Host " + loadBalancerToPing.toString());
				}
				loadBalancerToPing.setDisplayWarning(false);
			} catch (Exception e) {
				loadBalancerToPing.setAvailable(false);
				if(loadBalancerToPing.isDisplayWarning()) {
					logger.logError("sendKeepAlive: Cannot access the " + loadBalancerToPing + " RMI registry: " + e.getMessage() +
						"\nIf you need a cluster configuration make sure " + loadBalancerToPing + " is running. Host " + loadBalancerToPing.toString(), e);
				}
				loadBalancerToPing.setDisplayWarning(false);
			}
		}
	}
	
	/**
	 * Contribution from Naoki Nishihara from OKI for Issue 1806 (SIP LB can not forward when node is listening on 0.0.0.0) 
	 * Useful for a multi homed address, tries to reach a given load balancer from the list of ip addresses given in param
	 * @param balancerAddr the load balancer to try to reach 
	 * @param info the list of node info from which we try to access the load balancer
	 * @return the list stripped from the nodes not able to reach the load balancer
	 */
	protected ArrayList<SIPNode> getReachableSIPNodeInfo(InetAddress balancerAddr, ArrayList<SIPNode> info) {
		if (balancerAddr.isLoopbackAddress()) {
			return info;
		}

		ArrayList<SIPNode> rv = new ArrayList<SIPNode>();
		for(SIPNode node: info) {
			if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
				logger.logTrace("Checking if " + node + " is reachable");
			}
			try {
				NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(node.getIp()));
				// FIXME How can I determine the ttl?
				boolean b = balancerAddr.isReachable(ni, 5, 900);
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace(node + " is reachable ? " + b);
				}
				if(b) {
					rv.add(node);
				}
			} catch (IOException e) {
				logger.logError("IOException", e);
			}
		}
		
		if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
			logger.logTrace("Reachable SIP Node:[balancer=" + balancerAddr + "],[node info=" + rv + "]");
		}
		
		return rv;
	}

	/**
	 * @param info
	 */
	protected void removeNodesFromBalancers() {
		Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
		for(SipLoadBalancer loadBalancerToRemove: register.keySet()) {
			try {
				Registry registry = LocateRegistry.getRegistry(loadBalancerToRemove.getAddress().getHostAddress(),loadBalancerToRemove.getRmiPort());
				NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
				ArrayList<SIPNode> info = new ArrayList<SIPNode>(register.get(loadBalancerToRemove).values());
				reg.forceRemoval(info);
				if(!loadBalancerToRemove.isAvailable()) {
					logger.logInfo("Remove: SIP Load Balancer Found! " + loadBalancerToRemove);
					loadBalancerToRemove.setDisplayWarning(true);
				}
				loadBalancerToRemove.setAvailable(true);
				if(logger.isLoggingEnabled(StackLogger.TRACE_TRACE)) {
					logger.logTrace("Finished removed, Gathered info[" + info + "]");
				}
			} catch (IOException e) {
				if(loadBalancerToRemove.isDisplayWarning()) {
					logger.logWarning("remove: Cannot access the SIP load balancer RMI registry: " + e.getMessage() +
							"\nIf you need a cluster configuration make sure the SIP load balancer is running.");
					loadBalancerToRemove.setDisplayWarning(false);
				}
				loadBalancerToRemove.setAvailable(true);
			} catch (Exception e) {
				if(loadBalancerToRemove.isDisplayWarning()) {
					logger.logError("remove: Cannot access the SIP load balancer RMI registry: " + e.getMessage() +
							"\nIf you need a cluster configuration make sure the SIP load balancer is running.", e);
					loadBalancerToRemove.setDisplayWarning(false);
				}
				loadBalancerToRemove.setAvailable(true);
			}
		}
	}
	
	/**
	 * 
	 * @author <A HREF="mailto:jean.deruelle@gmail.com">Jean Deruelle</A> 
	 *
	 */
	protected class BalancerPingTimerTask extends TimerTask {

		@SuppressWarnings("unchecked")
		@Override
		public void run() {			
			sendKeepAliveToBalancers();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#getJvmRoute()
	 */
	public String getJvmRoute() {
		return jvmRoute;
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#setJvmRoute(java.lang.String)
	 */
	public void setJvmRoute(String jvmRoute) {
		this.jvmRoute = jvmRoute;
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#addLoadBalancerHeartBeatingListener(org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener)
	 */
	public void addLoadBalancerHeartBeatingListener(
			LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
		loadBalancerHeartBeatingListeners.add(loadBalancerHeartBeatingListener);
	}

	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#removeLoadBalancerHeartBeatingListener(org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingListener)
	 */
	public void removeLoadBalancerHeartBeatingListener(
			LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener) {
		loadBalancerHeartBeatingListeners.remove(loadBalancerHeartBeatingListener);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#sendSwitchoverInstruction(org.mobicents.ha.javax.sip.SipLoadBalancer, java.lang.String, java.lang.String)
	 */
	public void sendSwitchoverInstruction(SipLoadBalancer sipLoadBalancer, String fromJvmRoute, String toJvmRoute) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)) {
			logger.logInfo("switching over from " + fromJvmRoute + " to " + toJvmRoute);
		}
		if(fromJvmRoute == null || toJvmRoute == null) {
			return;
		}	
		ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		try {			
			Thread.currentThread().setContextClassLoader(NodeRegisterRMIStub.class.getClassLoader());
			Registry registry = LocateRegistry.getRegistry(sipLoadBalancer.getAddress().getHostAddress(),sipLoadBalancer.getRmiPort());
			NodeRegisterRMIStub reg=(NodeRegisterRMIStub) registry.lookup("SIPBalancer");
			reg.switchover(fromJvmRoute, toJvmRoute);
			sipLoadBalancer.setDisplayWarning(true);
			if(logger.isLoggingEnabled(StackLogger.TRACE_INFO) && !sipLoadBalancer.isAvailable()) {
				logger.logInfo("Switchover: SIP Load Balancer Found! " + sipLoadBalancer);
			}
		} catch (IOException e) {
			sipLoadBalancer.setAvailable(false);
			if(sipLoadBalancer.isDisplayWarning()) {
				logger.logWarning("Cannot access the SIP load balancer RMI registry: " + e.getMessage() +
				"\nIf you need a cluster configuration make sure the SIP load balancer is running.");
				sipLoadBalancer.setDisplayWarning(false);
			}
		} catch (Exception e) {
			sipLoadBalancer.setAvailable(false);
			if(sipLoadBalancer.isDisplayWarning()) {
				logger.logError("Cannot access the SIP load balancer RMI registry: " + e.getMessage() +
				"\nIf you need a cluster configuration make sure the SIP load balancer is running.", e);
				sipLoadBalancer.setDisplayWarning(false);
			}
		} finally {
			Thread.currentThread().setContextClassLoader(oldClassLoader);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.ha.javax.sip.LoadBalancerHeartBeatingService#setGracefulShutdown(org.mobicents.ha.javax.sip.SipLoadBalancer, boolean)
	 */
	public void setGracefulShutdown(SipLoadBalancer sipLoadBalancer, boolean gracefullyShuttingDown) {
		this.gracefullyShuttingDown = gracefullyShuttingDown;
		updateConnectorsAsSIPNode(sipLoadBalancer);
		// forcing keep alive sending to update the nodes in the LB with the info
		// that the nodes are shutting down 
		sendKeepAliveToBalancers();
	}

	public void setCustomInfo(SipLoadBalancer sipLoadBalancer) {
		updateConnectorsAsSIPNode(sipLoadBalancer);
	}
	
	public SipLoadBalancer[] getLoadBalancers() {
		// This is slow, but it is called rarely, so no prob
		return register.values().toArray(new SipLoadBalancer[] {});
	}

	public void addSipConnector(ListeningPoint listeningPoint) {
		throw new UnsupportedOperationException("This algorithm doesn't let you specify a load balancer without connector, please use instead " + LoadBalancerHeartBeatingServiceImpl.class.getName());
	}
	
	public void removeSipConnector(ListeningPoint listeningPoint) {
		throw new UnsupportedOperationException("This algorithm doesn't let you specify a load balancer without connector, please use instead " + LoadBalancerHeartBeatingServiceImpl.class.getName());
	}
	
	public void addSipConnector(ListeningPoint listeningPoint, SipLoadBalancer loadBalancer) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)){
			logger.logInfo("Adding Listening Point to be using the Load Balancer " + loadBalancer + " for outbound traffic " + listeningPoint.getIPAddress() + ":" + listeningPoint.getPort() + "/" + listeningPoint.getTransport());
			logger.logInfo("Recomputing SIP Nodes to be sent to the LB");
		}
		// https://github.com/RestComm/jain-sip.ha/issues/3 we restrict only if one connector is passed forcefully this way
		useLoadBalancerForAllConnectors = false;
		Set<ListeningPoint> sipConnectors = connectors.get(loadBalancer);
		if(sipConnectors == null) {
			sipConnectors = new CopyOnWriteArraySet<ListeningPoint>();
			connectors.put(loadBalancer, sipConnectors);
		}
		sipConnectors.add(listeningPoint);
		updateConnectorsAsSIPNode(loadBalancer);
		// notify the listeners
		for (LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener : loadBalancerHeartBeatingListeners) {
			loadBalancerHeartBeatingListener.loadBalancerAdded(loadBalancer);
		}
	}
	
	public void removeSipConnector(ListeningPoint listeningPoint, SipLoadBalancer loadBalancer) {
		if(logger.isLoggingEnabled(StackLogger.TRACE_INFO)){
			logger.logInfo("Removing Listening Point to be using the Load Balancer " + loadBalancer + " for outbound traffic " + listeningPoint.getIPAddress() + ":" + listeningPoint.getPort() + "/" + listeningPoint.getTransport());
			logger.logInfo("Recomputing SIP Nodes to be sent to the LB");
		}
		// https://github.com/RestComm/jain-sip.ha/issues/3 we restrict only if one connector is passed forcefully this way
		useLoadBalancerForAllConnectors = false;
		Set<ListeningPoint> sipConnectors = connectors.get(loadBalancer);
		sipConnectors.remove(listeningPoint);
		updateConnectorsAsSIPNode(loadBalancer);
		// notify the listeners
		for (LoadBalancerHeartBeatingListener loadBalancerHeartBeatingListener : loadBalancerHeartBeatingListeners) {
			loadBalancerHeartBeatingListener.loadBalancerRemoved(loadBalancer);
		}
	}
}
