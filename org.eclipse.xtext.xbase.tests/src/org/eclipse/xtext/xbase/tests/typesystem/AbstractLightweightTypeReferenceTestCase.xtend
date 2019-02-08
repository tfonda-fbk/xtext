/*******************************************************************************
 * Copyright (c) 2019 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.xbase.tests.typesystem

import com.google.inject.Inject
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.xbase.tests.AbstractXbaseTestCase
import org.eclipse.xtext.xbase.typesystem.references.StandardTypeReferenceOwner
import org.eclipse.xtext.xbase.typesystem.util.CommonTypeComputationServices
import org.eclipse.xtext.common.types.JvmType
import org.eclipse.xtext.xbase.typesystem.references.ITypeReferenceOwner
import org.eclipse.xtext.xbase.typesystem.references.LightweightTypeReference

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
class AbstractLightweightTypeReferenceTestCase extends AbstractXbaseTestCase {
	
	@Inject CommonTypeComputationServices services

	@Inject XtextResourceSet resourceSet
	
	protected def ITypeReferenceOwner getOwner() {
		return new StandardTypeReferenceOwner(services, resourceSet)
	}	
	
	protected def LightweightTypeReference typeRef(Class<?> type) {
		return owner.toLightweightTypeReference(type.type)
	}
	
	protected def JvmType type(Class<?> type) {
		return services.typeReferences.findDeclaredType(type, resourceSet)
	}
}