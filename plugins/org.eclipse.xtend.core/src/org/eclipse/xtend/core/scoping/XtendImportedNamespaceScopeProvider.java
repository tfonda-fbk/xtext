/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtend.core.scoping;

import static com.google.common.collect.Lists.*;

import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtend.core.xtend.XtendFile;
import org.eclipse.xtend.core.xtend.XtendImport;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.ImportNormalizer;
import org.eclipse.xtext.util.Strings;
import org.eclipse.xtext.xbase.scoping.XbaseImportedNamespaceScopeProvider;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * @author Jan Koehnlein - Initial contribution and API
 * @author Sebastian Zarnekow - Improved support for nested types in connection with imports
 */
public class XtendImportedNamespaceScopeProvider extends XbaseImportedNamespaceScopeProvider {

	@Inject
	private IQualifiedNameConverter nameConverter;
	
	@Override
	protected IScope internalGetScope(IScope parent, IScope globalScope, EObject context, EReference reference) {
		if (context instanceof XtendImport) {
			return globalScope;
		} else {
			return super.internalGetScope(parent, globalScope, context, reference);
		}
	}
	
	@Override
	protected List<ImportNormalizer> internalGetImportedNamespaceResolvers(EObject context, boolean ignoreCase) {
		if (!(context instanceof XtendFile))
			return Collections.emptyList();
		XtendFile file = (XtendFile) context;
		List<ImportNormalizer> importedNamespaceResolvers = Lists.newArrayList();
		for (XtendImport imp : file.getImports()) {
			if (!imp.isStatic()) {
				String value = imp.getImportedNamespace();
				if (value == null)
					value = imp.getImportedTypeName();
				ImportNormalizer resolver = createImportedNamespaceResolver(value, ignoreCase);
				if (resolver != null)
					importedNamespaceResolvers.add(resolver);
			}
		}
		if (!Strings.isEmpty(((XtendFile) context).getPackage())) {
			importedNamespaceResolvers.add(new ImportNormalizer(nameConverter.toQualifiedName(((XtendFile) context)
					.getPackage()), true, ignoreCase));
		}
		return importedNamespaceResolvers;
	}
	
	@Override
	protected List<ImportNormalizer> getImplicitImports(boolean ignoreCase) {
		return newArrayList(new ImportNormalizer(QualifiedName.create("java","lang"), true, false),
				new ImportNormalizer(QualifiedName.create("org","eclipse","xtend","lib"), true, false));
	}

}
