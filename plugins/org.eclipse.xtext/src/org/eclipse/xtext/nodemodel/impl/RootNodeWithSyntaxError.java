/*******************************************************************************
 * Copyright (c) 2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.nodemodel.impl;

import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
public class RootNodeWithSyntaxError extends RootNode {

	private SyntaxErrorMessage syntaxErrorMessage;
	
	@Override
	public SyntaxErrorMessage getSyntaxErrorMessage() {
		return syntaxErrorMessage;
	}

	public void setSyntaxErrorMessage(SyntaxErrorMessage syntaxErrorMessage) {
		this.syntaxErrorMessage = syntaxErrorMessage;
	}
	
}
