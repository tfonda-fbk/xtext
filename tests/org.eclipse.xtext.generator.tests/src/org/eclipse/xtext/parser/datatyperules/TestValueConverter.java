/*******************************************************************************
 * Copyright (c) 2008 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.parser.datatyperules;

import java.math.BigDecimal;

import junit.framework.TestCase;

import org.eclipse.xtext.conversion.IValueConverter;

/**
 * @author Sebastian Zarnekow - Initial contribution and API
 */
public class TestValueConverter extends TestCase {

	private IValueConverter valueConverter;

	protected void setUp() throws Exception {
		valueConverter = new DatatypeRulesTestLanguageValueConverters().Fraction();
	}
	
	public void testSimpleToObject() {
		String s = "123";
		BigDecimal bd = (BigDecimal) valueConverter.toValue(s);
		BigDecimal expected = new BigDecimal("123");
		assertEquals(expected, bd);
	}
	
	public void testFractionObject() {
		String s = "123/246";
		BigDecimal bd = (BigDecimal) valueConverter.toValue(s);
		BigDecimal expected = new BigDecimal("0.5");
		assertEquals(expected, bd);
	}
	
	public void testZeroDenominator() {
		String s = "123/0";
		try {
			valueConverter.toValue(s);
			fail("expected ArithmeticException");
		} catch(ArithmeticException ae) {
			// expected
		}
	}
	
	public void testSimpleToString() {
		String expected = "123";
		BigDecimal bd = BigDecimal.valueOf(123);
		assertEquals(expected, valueConverter.toString(bd));
	}
	
	public void testFractionToString_01() {
		String expected = "5/10";
		BigDecimal bd = new BigDecimal("0.5");
		assertEquals(expected, valueConverter.toString(bd));
	}
	
	public void testFractionToString_02() {
		String expected = "1557/1000";
		BigDecimal bd = new BigDecimal("1.557");
		assertEquals(expected, valueConverter.toString(bd));
	}
}
