package org.mobicents.ha.javax.sip.cache;

import org.mobicents.cache.MobicentsCache;

/**
 * Instead of relying in JBoss MC to get a direct reference to MobicentsCache bean, this class can be injected 
 * and expose it. 
 * @author martins
 *
 */
public class MobicentsCacheBeanLocator {

	/**
	 * {@link MobicentsCache} instance injected by JBoss MC
	 */
	static MobicentsCache BEAN;
	
	/**
	 * Setter for {@link MobicentsCache} instance injected by JBoss MC
	 * @param mobicentsCache
	 */
	public void setMobicentsCacheBean(MobicentsCache mobicentsCache) {
		BEAN = mobicentsCache;
	}
		
}
