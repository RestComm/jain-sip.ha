package org.mobicents.ha.javax.sip.cache;

import org.mobicents.cache.MobicentsCache;
import org.mobicents.ha.javax.sip.cache.SipCacheException;

/**
 * This {@link SipCache} relies on the {@link MobicentsCache} injected into the
 * locator. It should be used to reuse Mobicents Cluster default cache.
 * 
 * @author martins
 * 
 */
public class InjectedMobicentsSipCache extends MobicentsSipCache {

	/**
	 * 
	 */
	public InjectedMobicentsSipCache() {
		super();
	}
	
	@Override
	public void init() throws SipCacheException {
		cache = MobicentsCacheBeanLocator.BEAN;
	}

	@Override
	public void start() throws SipCacheException {
		// nothing to do
	}

	@Override
	public void stop() throws SipCacheException {
		// nothing to do
	}

}
