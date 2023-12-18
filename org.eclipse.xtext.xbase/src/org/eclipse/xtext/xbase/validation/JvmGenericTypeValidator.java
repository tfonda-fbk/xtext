/*******************************************************************************
 * Copyright (c) 2023 itemis AG (http://www.itemis.eu) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.xbase.validation;

import static org.eclipse.xtext.util.Strings.*;
import static org.eclipse.xtext.xbase.validation.IssueCodes.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.common.types.JvmAnnotationType;
import org.eclipse.xtext.common.types.JvmDeclaredType;
import org.eclipse.xtext.common.types.JvmGenericType;
import org.eclipse.xtext.common.types.JvmParameterizedTypeReference;
import org.eclipse.xtext.common.types.JvmSpecializedTypeReference;
import org.eclipse.xtext.common.types.JvmType;
import org.eclipse.xtext.common.types.JvmTypeReference;
import org.eclipse.xtext.common.types.JvmWildcardTypeReference;
import org.eclipse.xtext.validation.AbstractDeclarativeValidator;
import org.eclipse.xtext.validation.Check;
import org.eclipse.xtext.validation.EValidatorRegistrar;
import org.eclipse.xtext.xbase.jvmmodel.IJvmModelAssociations;

import com.google.inject.Inject;

/**
 * @author Lorenzo Bettini - Initial contribution and API
 * @author Sebastian Zarnekow - Author of the original Java code in XtendValidator extracted here.
 */
public class JvmGenericTypeValidator extends AbstractDeclarativeValidator {
	@Inject
	private IJvmModelAssociations associations;

	@Override
	public void register(EValidatorRegistrar registrar) {
		// library validator is not registered for a specific language
	}

	@Check
	public void interceptRoot(EObject o) {
		var contents = o.eResource().getContents();
		if (contents.get(0) != o)
			return;
		checkJvmGenericTypes(contents);
	}

	protected void checkJvmGenericTypes(List<? extends EObject> contents) {
		contents.stream()
				.filter(e -> !associations.getSourceElements(e).isEmpty())
				.filter(JvmGenericType.class::isInstance)
				.map(JvmGenericType.class::cast)
				.forEach(this::checkJvmGenericType);
	}

	protected void checkJvmGenericType(JvmGenericType type) {
		checkSuperTypes(type);
		checkJvmGenericTypes(type.getMembers());
	}

	protected void checkSuperTypes(JvmGenericType type) {
		var primarySourceElement = associations.getPrimarySourceElement(type);
		if (primarySourceElement == null)
			return;
		if (hasCycleInHierarchy(type, new HashSet<>())) {
			error("The inheritance hierarchy of " + notNull(type.getSimpleName()) + " contains cycles",
					primarySourceElement,
					getFeatureForIssue(primarySourceElement), CYCLIC_INHERITANCE);
		}
		if (type.isInterface()) {
			var superTypes = type.getSuperTypes();
			for (int i = 0; i < superTypes.size(); ++i) {
				JvmTypeReference extendedType = superTypes.get(i);
				if (!isInterface(extendedType.getType()) && !isAnnotation(extendedType.getType())) {
					var associated = associations.getPrimarySourceElement(extendedType);
					var eContainingFeature = associated.eContainingFeature();
					error("Extended interface must be an interface",
						primarySourceElement,
						eContainingFeature, i, INTERFACE_EXPECTED);
				}
//				checkWildcardSupertype(xtendInterface, extendedType, XTEND_INTERFACE__EXTENDS, i);
			}
		}
	}

	private boolean hasCycleInHierarchy(JvmGenericType type, Set<JvmGenericType> processedSuperTypes) {
		JvmDeclaredType container = type;
		do {
			if (processedSuperTypes.contains(container))
				return true;
			container = container.getDeclaringType();
		} while (container != null);
		processedSuperTypes.add(type);
		for (JvmTypeReference superTypeRef : type.getSuperTypes()) {
			if (superTypeRef.getType() instanceof JvmGenericType) {
				if (hasCycleInHierarchy((JvmGenericType) superTypeRef.getType(), processedSuperTypes))
					return true;
			}
		}
		processedSuperTypes.remove(type);
		return false;
	}

	protected boolean isInterface(JvmType type) {
		return type instanceof JvmGenericType && ((JvmGenericType) type).isInterface();
	}

	protected boolean isAnnotation(JvmType jvmType) {
		return jvmType instanceof JvmAnnotationType;
	}

	protected boolean isInvalidWildcard(JvmTypeReference typeRef) {
		if (typeRef instanceof JvmWildcardTypeReference)
			return true;
		else if (typeRef instanceof JvmParameterizedTypeReference) {
			for(JvmTypeReference typeArgument: ((JvmParameterizedTypeReference) typeRef).getArguments()) {
				if(typeArgument instanceof JvmWildcardTypeReference) 
					return true;
			}
		} else if (typeRef instanceof JvmSpecializedTypeReference) {
			return isInvalidWildcard(((JvmSpecializedTypeReference) typeRef).getEquivalent());
		}
		return false;
	}

	/**
	 * The default implementation returns the feature "name".
	 */
	protected EStructuralFeature getFeatureForIssue(EObject object) {
		return object.eClass().getEStructuralFeature("name");
	}
}
