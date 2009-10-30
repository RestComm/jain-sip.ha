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

import gov.nist.javax.sip.stack.SIPDialog;

import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.mobicents.cache.CacheData;
import org.mobicents.cache.MobicentsCache;

/**
 * @author jean.deruelle@gmail.com
 *
 */
public class SIPDialogCacheData extends CacheData {

	private static final Boolean MAP_KEY = Boolean.TRUE;
	
	public SIPDialogCacheData(Fqn nodeFqn, MobicentsCache mobicentsCache) {
		super(nodeFqn, mobicentsCache);
	}
	
	public SIPDialog getSIPDialog(String dialogId) {
		final Node<Boolean,SIPDialog> childNode = getNode().getChild(dialogId);
		return childNode != null ? childNode.get(MAP_KEY) : null;
	}
	
	public void putSIPDialog(SIPDialog dialog) throws SipCacheException {
		getNode().addChild(Fqn.fromElements(dialog.getDialogId())).put(MAP_KEY, dialog);
	}

	public boolean removeSIPDialog(String dialogId) {
		return getNode().removeChild(dialogId);
	}
}
