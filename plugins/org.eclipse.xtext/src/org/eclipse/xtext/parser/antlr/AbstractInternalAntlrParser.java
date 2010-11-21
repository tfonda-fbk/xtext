/*******************************************************************************
 * Copyright (c) 2008 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.xtext.parser.antlr;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.antlr.runtime.BitSet;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.FailedPredicateException;
import org.antlr.runtime.IntStream;
import org.antlr.runtime.MismatchedTokenException;
import org.antlr.runtime.MissingTokenException;
import org.antlr.runtime.Parser;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.RecognizerSharedState;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.UnwantedTokenException;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.xtext.AbstractElement;
import org.eclipse.xtext.AbstractRule;
import org.eclipse.xtext.Action;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.Grammar;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.UnorderedGroup;
import org.eclipse.xtext.XtextPackage;
import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.SyntaxErrorMessage;
import org.eclipse.xtext.nodemodel.impl.InvariantChecker;
import org.eclipse.xtext.nodemodel.impl.NodeModelBuilder;
import org.eclipse.xtext.nodemodel.impl.RootNode;
import org.eclipse.xtext.parser.IAstFactory;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.ParseException;
import org.eclipse.xtext.parser.ParseResult;
import org.eclipse.xtext.parser.antlr.ISyntaxErrorMessageProvider.IParserErrorContext;
import org.eclipse.xtext.parser.antlr.ISyntaxErrorMessageProvider.IUnorderedGroupErrorContext;
import org.eclipse.xtext.parser.antlr.ISyntaxErrorMessageProvider.IValueConverterErrorContext;
import org.eclipse.xtext.parsetree.AbstractNode;
import org.eclipse.xtext.parsetree.CompositeNode;
import org.eclipse.xtext.parsetree.LeafNode;
import org.eclipse.xtext.parsetree.NodeAdapter;
import org.eclipse.xtext.parsetree.NodeAdapterFactory;
import org.eclipse.xtext.parsetree.NodeUtil;
import org.eclipse.xtext.parsetree.ParsetreeFactory;
import org.eclipse.xtext.parsetree.SyntaxError;
import org.eclipse.xtext.util.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.internal.Maps;

/**
 * TODO Javadoc
 */
public abstract class AbstractInternalAntlrParser extends Parser {

	protected class ErrorContext {
		public EObject getCurrentContext() {
			if (lwCurrentNode != null)
				return lwCurrentNode.getSemanticElement();
			if (currentNode != null)
				return NodeUtil.getNearestSemanticObject(currentNode);
			return null;
		}
		
		public INode getCurrentNode2() {
			return lwCurrentNode;
		}	
		
		public AbstractNode getCurrentNode() {
			return currentNode;
		}
	}
	
	protected class ParserErrorContext extends ErrorContext implements IParserErrorContext {

		private final RecognitionException recognitionException;

		protected ParserErrorContext(RecognitionException recognitionException) {
			this.recognitionException = recognitionException;
		}
		
		public String getDefaultMessage() {
			return superGetErrorMessage(getRecognitionException(), getTokenNames());
		}

		public RecognitionException getRecognitionException() {
			return recognitionException;
		}

		public String[] getTokenNames() {
			return readableTokenNames;
		}
	}
	
	private static final Class<?>[] emptyClassArray = new Class[0];
	private static final Object[] emptyObjectArray = new Object[0];
	
	protected class UnorderedGroupErrorContext extends ParserErrorContext implements IUnorderedGroupErrorContext {
		
		private List<AbstractElement> missingMandatoryElements;
		
		protected UnorderedGroupErrorContext(FailedPredicateException exception) {
			super(exception);
		}
		
		@Override
		public FailedPredicateException getRecognitionException() {
			return (FailedPredicateException) super.getRecognitionException();
		}
		
		public List<AbstractElement> getMissingMandatoryElements() {
			List<AbstractElement> result = missingMandatoryElements;
			if (result == null) {
				String predicate = getRecognitionException().toString();
				int idx = predicate.indexOf("grammarAccess");
				int lastIdx = predicate.lastIndexOf('(');
				predicate = predicate.substring(idx + "grammarAccess.".length(), lastIdx);
				String ruleMethodGetter = predicate.substring(0, predicate.indexOf('('));
				String elementGetter = predicate.substring(predicate.indexOf('.') + 1);
				IGrammarAccess grammarAccess = getGrammarAccess();
				Object ruleAccess = invokeNoArgMethod(ruleMethodGetter, grammarAccess);
				UnorderedGroup group = (UnorderedGroup) invokeNoArgMethod(elementGetter, ruleAccess);
				List<AbstractElement> missingElements = Lists.newArrayList();
				for(int i = 0; i < group.getElements().size(); i++) {
					AbstractElement element = group.getElements().get(i);
					if (!GrammarUtil.isOptionalCardinality(element) && unorderedGroupHelper.canSelect(group, i)) {
						missingElements.add(element);
					}
				}
				result = ImmutableList.copyOf(missingElements);
				missingElements = result;
			}
			return result;
		}
		
		private Object invokeNoArgMethod(String name, Object target) {
			try {
				Method method = target.getClass().getMethod(name, emptyClassArray);
				return method.invoke(target, emptyObjectArray);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	protected class ValueConverterErrorContext extends ErrorContext implements IValueConverterErrorContext {

		private final ValueConverterException valueConverterException;

		protected ValueConverterErrorContext(ValueConverterException valueConverterException) {
			this.valueConverterException = valueConverterException;
		}

		public String getDefaultMessage() {
			return getValueConverterExceptionMessage(getValueConverterException());
		}

		public ValueConverterException getValueConverterException() {
			return valueConverterException;
		}

	}
	
	private static final Logger logger = Logger.getLogger(AbstractInternalAntlrParser.class);
	
	private CompositeNode currentNode;
	
	private INode lwLastConsumedNode;
	
	protected IAstFactory semanticModelBuilder;
	
	private int lastConsumedIndex = -1;

	private AbstractNode lastConsumedNode;

	private final Map<String, AbstractRule> allRules;
	
	private ISyntaxErrorMessageProvider syntaxErrorProvider;
	
	private IUnorderedGroupHelper unorderedGroupHelper;
	
	private NodeModelBuilder lwNodeBuilder = new NodeModelBuilder();
	private ICompositeNode lwCurrentNode;
	
	private final Map<Token, List<CompositeNode>> deferredLookaheadMap = Maps.newHashMap();
	private final Map<Token, LeafNode> token2NodeMap = Maps.newHashMap();

	protected AbstractInternalAntlrParser(TokenStream input) {
		super(input);
		allRules = Maps.newHashMap();
	}
	
	protected AbstractInternalAntlrParser(TokenStream input, RecognizerSharedState state) {
		super(input, state);
		allRules = Maps.newHashMap();
	}

	protected void registerRules(Grammar grammar) {
		for (AbstractRule rule: GrammarUtil.allRules(grammar)) {
			allRules.put(rule.getName(), rule);
		}
	}

	public TokenStream getInput() {
		return input;
	}

	protected abstract IGrammarAccess getGrammarAccess();
	
	protected void associateNodeWithAstElement(CompositeNode node, EObject astElement) {
		if (astElement == null)
			throw new NullPointerException("passed astElement was null");
		if (node == null)
			throw new NullPointerException("passed node was null");
		if (node.getElement() != null && node.getElement() != astElement) {
			throw new ParseException("Reassignment of astElement in parse tree node");
		}
		if (node.getElement() != astElement) {
			node.setElement(astElement);
			NodeAdapter adapter = (NodeAdapter) NodeAdapterFactory.INSTANCE.adapt(astElement, AbstractNode.class);
			adapter.setParserNode(node);
		}
	}
	
	protected void associateNodeWithAstElement(ICompositeNode node, EObject astElement) {
		if (astElement == null)
			throw new NullPointerException("passed astElement was null");
		if (node == null)
			throw new NullPointerException("passed node was null");
		lwNodeBuilder.associateWithSemanticElement(node, astElement);
	}

	protected void createLeafNode(Token token, EObject grammarElement, String feature) {
		if (token != null && token.getTokenIndex() > lastConsumedIndex) {
			int indexOfTokenBefore = lastConsumedIndex;
			if (indexOfTokenBefore + 1 < token.getTokenIndex()) {
				for (int x = indexOfTokenBefore + 1; x < token.getTokenIndex(); x++) {
					Token hidden = input.get(x);
					LeafNode leafNode = createLeafNode(hidden, hidden.getChannel() == HIDDEN);
					setLexerRule(leafNode, hidden);
					
					createLWLeafNode(hidden, leafNode.getSyntaxError(), null);
				}
			}
			LeafNode leafNode = createLeafNode(token, false);
			leafNode.setGrammarElement(grammarElement);
			leafNode.setFeature(feature);
			lastConsumedIndex = token.getTokenIndex();
			lastConsumedNode = leafNode;
			tokenConsumed(token, leafNode);
			
			createLWLeafNode(token, leafNode.getSyntaxError(), grammarElement);
		}
	}
	
	private INode createLWLeafNode(Token token, SyntaxError error, EObject grammarElement) {
		boolean isHidden = token.getChannel() == HIDDEN;
		String errorMessage = null;
		if (error != null)
			errorMessage = error.getMessage();
		if (token.getType() == Token.INVALID_TOKEN_TYPE) {
			if (errorMessage != null) {
				String lexerErrorMessage = ((XtextTokenStream) input).getLexerErrorMessage(token);
				errorMessage = errorMessage + "; " + lexerErrorMessage;
			}
		}
		if (grammarElement == null) {
			String ruleName = antlrTypeToLexerName.get(token.getType());
			grammarElement = allRules.get(ruleName);
		}
		CommonToken commonToken = (CommonToken) token;
		return lwNodeBuilder.newLeafNode(
				commonToken.getStartIndex(), 
				commonToken.getStopIndex() - commonToken.getStartIndex() + 1, 
				grammarElement, 
				isHidden, 
				errorMessage, 
				lwCurrentNode);
	}
	
	private Map<Integer, String> antlrTypeToLexerName = null;
	
	private String[] readableTokenNames = null;

	public void setTokenTypeMap(Map<Integer, String> tokenTypeMap) {
		antlrTypeToLexerName = Maps.newHashMap();
		for(Entry<Integer, String> mapEntry: tokenTypeMap.entrySet()) {
			String value = mapEntry.getValue();
			if(TokenTool.isLexerRule(value)) {
				antlrTypeToLexerName.put(mapEntry.getKey(), TokenTool.getLexerRuleName(value));
			}
		}
		String[] tokenNames = getTokenNames();
		readableTokenNames = new String[tokenNames.length];
		for(int i = 0; i < tokenNames.length; i++) {
			if (tokenTypeMap.containsKey(i)) {
				readableTokenNames[i] = tokenTypeMap.get(i);
			} else {
				readableTokenNames[i] = tokenNames[i];
			}
		}
	}
	
	protected void setLexerRule(LeafNode leafNode, Token hidden) {
		String ruleName = antlrTypeToLexerName.get(hidden.getType());
		AbstractRule rule = allRules.get(ruleName);
		if (rule != null)
			leafNode.setGrammarElement(rule);
	}
	
	public void setSyntaxErrorProvider(ISyntaxErrorMessageProvider syntaxErrorProvider) {
		this.syntaxErrorProvider = syntaxErrorProvider;
	}
	
	public ISyntaxErrorMessageProvider getSyntaxErrorProvider() {
		return syntaxErrorProvider;
	}

	protected void set(EObject _this, String feature, Object value, String lexerRule, AbstractNode node) {
		try {
			semanticModelBuilder.set(_this, feature, value, lexerRule, node);
		} catch(ValueConverterException vce) {
			handleValueConverterException(vce);
		}
	}
	
	protected void set(EObject _this, String feature, Object value, String lexerRule) {
		set(_this, feature, value, lexerRule, currentNode);
	}
	
	protected void setWithLastConsumed(EObject _this, String feature, Object value, String lexerRule) {
		set(_this, feature, value, lexerRule, lastConsumedNode);
	}

	protected void add(EObject _this, String feature, Object value, String lexerRule, AbstractNode node) {
		try {
			semanticModelBuilder.add(_this, feature, value, lexerRule, node);
		} catch(ValueConverterException vce) {
			handleValueConverterException(vce);
		}
	}
	
	protected void add(EObject _this, String feature, Object value, String lexerRule) {
		add(_this, feature, value, lexerRule, currentNode);
	}
	
	protected void addWithLastConsumed(EObject _this, String feature, Object value, String lexerRule) {
		add(_this, feature, value, lexerRule, lastConsumedNode);
	}
	
	protected CompositeNode createCompositeNode(EObject grammarElement, CompositeNode parentNode) {
		CompositeNode compositeNode = ParsetreeFactory.eINSTANCE.createCompositeNode();
		if (parentNode != null)
			((InternalEList<AbstractNode>) parentNode.getChildren()).addUnique(compositeNode);
		compositeNode.setGrammarElement(grammarElement);
		return compositeNode;
	}

	private void appendError(AbstractNode node, INode lwNode) {
		if (currentError != null) {
			SyntaxError error = ParsetreeFactory.eINSTANCE.createSyntaxError();
			error.setMessage(currentError.getMessage());
			error.setIssueCode(currentError.getIssueCode());
			node.setSyntaxError(error);
			if (lwNode != null) {
				INode newNode = lwNodeBuilder.setSyntaxError(lwNode, currentError);
				if (lwNode == currentNode)
					currentNode = (CompositeNode) newNode;
			}
			currentError = null;
		}
	}

	private LeafNode createLeafNode(Token token, boolean isHidden) {
		LeafNode leafNode = ParsetreeFactory.eINSTANCE.createLeafNode();
		leafNode.setText(token.getText());
		leafNode.setHidden(isHidden);
		if (token.getChannel() != HIDDEN)
			appendError(leafNode, null);
		if (token.getType() == Token.INVALID_TOKEN_TYPE) {
			SyntaxError error = ParsetreeFactory.eINSTANCE.createSyntaxError();
			String lexerErrorMessage = ((XtextTokenStream) input).getLexerErrorMessage(token);
			error.setMessage(lexerErrorMessage);
			leafNode.setSyntaxError(error);
		}
		((InternalEList<AbstractNode>) currentNode.getChildren()).addUnique(leafNode);
		return leafNode;
	}

	protected void appendAllTokens() {
		for (int x = lastConsumedIndex + 1; input.size() > x; input.consume(), x++) {
			Token hidden = input.get(x);
			LeafNode leafNode = createLeafNode(hidden, hidden.getChannel() == HIDDEN);
			setLexerRule(leafNode, hidden);
			
			createLWLeafNode(hidden, leafNode.getSyntaxError(), null);
		}
		if (currentError != null) {
			EList<LeafNode> leafNodes = currentNode.getLeafNodes();
			if (leafNodes.isEmpty()) {
				appendError(currentNode, lwCurrentNode);
			} else {
				appendError(leafNodes.get(leafNodes.size() - 1), lwCurrentNode.getLastChild());
			}
		}
	}

	protected List<LeafNode> appendSkippedTokens() {
		List<LeafNode> skipped = new ArrayList<LeafNode>();
		Token currentToken = input.LT(-1);
		int currentTokenIndex = (currentToken == null) ? -1 : currentToken.getTokenIndex();
		Token tokenBefore = (lastConsumedIndex == -1) ? null : input.get(lastConsumedIndex);
		int indexOfTokenBefore = tokenBefore != null ? tokenBefore.getTokenIndex() : -1;
		if (indexOfTokenBefore + 1 < currentTokenIndex) {
			for (int x = indexOfTokenBefore + 1; x < currentTokenIndex; x++) {
				Token hidden = input.get(x);
				LeafNode leafNode = createLeafNode(hidden, hidden.getChannel() == HIDDEN);
				setLexerRule(leafNode, hidden);
				skipped.add(leafNode);
				
				createLWLeafNode(hidden, leafNode.getSyntaxError(), null);
			}
		}
		if (lastConsumedIndex < currentTokenIndex && currentToken != null) {
			LeafNode leafNode = createLeafNode(currentToken, currentToken.getChannel() == HIDDEN);
			setLexerRule(leafNode, currentToken);
			skipped.add(leafNode);
			
			createLWLeafNode(currentToken, leafNode.getSyntaxError(), null);
			
			lastConsumedIndex = currentToken.getTokenIndex();
		}
		return skipped;
	}

	protected void appendTrailingHiddenTokens() {
		Token tokenBefore = input.LT(-1);
		int size = input.size();
		if (tokenBefore != null && tokenBefore.getTokenIndex() < size) {
			for (int x = tokenBefore.getTokenIndex() + 1; x < size; x++) {
				Token hidden = input.get(x);
				LeafNode leafNode = createLeafNode(hidden, hidden.getChannel() == HIDDEN);
				setLexerRule(leafNode, hidden);
				
				createLWLeafNode(hidden, leafNode.getSyntaxError(), null);
				
				lastConsumedIndex = hidden.getTokenIndex();
			}
		}
	}
	
	private SyntaxErrorMessage currentError = null;

	@Override
	public void recover(IntStream input, RecognitionException re) {
		if (currentError == null)
			currentError = getSyntaxErrorMessage(re, getTokenNames());
		super.recover(input, re);
	}
	
	protected String getValueConverterExceptionMessage(ValueConverterException vce) {
		Exception cause = (Exception) vce.getCause();
		String result = cause != null ? cause.getMessage() : vce.getMessage();
		if (result == null)
			result = vce.getMessage();
		if (result == null)
			result = cause != null ? cause.getClass().getSimpleName() : vce.getClass().getSimpleName();
		return result;
	}
	
	protected void handleValueConverterException(ValueConverterException vce) {
		Exception cause = (Exception) vce.getCause();
		if (vce != cause) {
			IValueConverterErrorContext errorContext = createValueConverterErrorContext(vce);
			currentError = syntaxErrorProvider.getSyntaxErrorMessage(errorContext);
			if (vce.getNode() == null && vce.getNode2() == null) {
				final List<AbstractNode> children = currentNode.getChildren();
				if (children.isEmpty()) {
					appendError(currentNode, lwCurrentNode);
				} else {
					appendError(children.get(children.size() - 1), lwCurrentNode.getLastChild());
				}
			} else {
				appendError(vce.getNode(), vce.getNode2());
			}
		} else {
			throw new RuntimeException(vce);
		}
	}

	protected IValueConverterErrorContext createValueConverterErrorContext(ValueConverterException vce) {
		return new ValueConverterErrorContext(vce);
	}

	@Override
	public String getErrorMessage(RecognitionException e, String[] tokenNames) {
		throw new UnsupportedOperationException("getErrorMessage");
	}
	
	@Override
	public void displayRecognitionError(String[] tokenNames, RecognitionException e) {
		throw new UnsupportedOperationException("displayRecognitionError");
	}
	
	@Override
	public void reportError(RecognitionException e) {
		if ( state.errorRecovery ) {
			return;
		}
		state.syntaxErrors++; // don't count spurious
		state.errorRecovery = true;
		if (currentError == null)
			currentError = getSyntaxErrorMessage(e, getTokenNames());
	}

	@Override
	protected Object recoverFromMismatchedToken(IntStream input, int ttype, BitSet follow) throws RecognitionException {
		RecognitionException e = null;
		// if next token is what we are looking for then "delete" this token
		if ( mismatchIsUnwantedToken(input, ttype) ) {
			e = new UnwantedTokenException(ttype, input);
			/*
			System.err.println("recoverFromMismatchedToken deleting "+
							   ((TokenStream)input).LT(1)+
							   " since "+((TokenStream)input).LT(2)+" is what we want");
			 */
			beginResync();
			input.consume(); // simply delete extra token
			endResync();
			reportError(e);  // report after consuming so AW sees the token in the exception
			// we want to return the token we're actually matching
			Object matchedSymbol = getCurrentInputSymbol(input);
			input.consume(); // move past ttype token as if all were ok
			return matchedSymbol;
		}
		// can't recover with single token deletion, try insertion
		if ( mismatchIsMissingToken(input, follow) ) {
			Object inserted = getMissingSymbol(input, e, ttype, follow);
			e = new MissingTokenException(ttype, input, inserted);
			reportError(e);  // report after inserting so AW sees the token in the exception
			return null;
	//		throw e;
		}
		// even that didn't work; must throw the exception
		e = new MismatchedTokenException(ttype, input);
		throw e;
	}
	
	public SyntaxErrorMessage getSyntaxErrorMessage(RecognitionException e, String[] tokenNames) {
		IParserErrorContext parseErrorContext = createErrorContext(e);
		return syntaxErrorProvider.getSyntaxErrorMessage(parseErrorContext);
	}
	
	protected String superGetErrorMessage(RecognitionException e, String[] tokenNames) {
		return super.getErrorMessage(e, tokenNames);
	}
	
	protected IParserErrorContext createErrorContext(RecognitionException e) {
		if (e instanceof FailedPredicateException)
			return new UnorderedGroupErrorContext((FailedPredicateException) e);
		return new ParserErrorContext(e);
	}

	public final IParseResult parse() throws RecognitionException {
		return parse(getFirstRuleName());
	}

	public final IParseResult parse(String entryRuleName) throws RecognitionException {
		IParseResult result = null;
		EObject current = null;
		try {
			lwCurrentNode = lwNodeBuilder.newCompositeNode(null, 0, null);
			String antlrEntryRuleName = normalizeEntryRuleName(entryRuleName);
			try {
				Method method = this.getClass().getMethod(antlrEntryRuleName);
				Object parseResult = method.invoke(this);
				if (parseResult instanceof EObject)
					current = (EObject) parseResult;
			} catch (InvocationTargetException ite) {
				Throwable targetException = ite.getTargetException();
				if (targetException instanceof RecognitionException) {
					throw (RecognitionException) targetException;
				}
				if (targetException instanceof Exception) {
					throw new WrappedException((Exception) targetException);
				}
				throw new RuntimeException(targetException);
			} catch (Exception e) {
				throw new WrappedException(e);
			}
			appendSkippedTokens();
			appendTrailingHiddenTokens();
		} finally {
			try {
				appendAllTokens();
			} finally {
				String completeContent = input.toString();
				if (completeContent == null) // who had the crazy idea to return null from toString() ...
					completeContent = "";
				((RootNode)lwCurrentNode.getParent()).setCompleteContent(completeContent);
				new InvariantChecker().checkInvariant(lwCurrentNode);
				result = new ParseResult(current, currentNode, lwCurrentNode.getParent());
			}
		}
		return result;
	}

	private String normalizeEntryRuleName(String entryRuleName) {
		String antlrEntryRuleName;
		if (!entryRuleName.startsWith("entryRule")) {
			if (!entryRuleName.startsWith("rule")) {
				antlrEntryRuleName = "entryRule" + entryRuleName;
			} else {
				antlrEntryRuleName = "entry" + Strings.toFirstUpper(entryRuleName);
			}
		} else {
			antlrEntryRuleName = entryRuleName;
		}
		return antlrEntryRuleName;
	}

	private void tokenConsumed(Token token, LeafNode leafNode) {
		List<CompositeNode> nodesDecidingOnToken = deferredLookaheadMap.get(token);
		if (nodesDecidingOnToken != null && !nodesDecidingOnToken.isEmpty()) {
			for (CompositeNode nodeDecidingOnToken : nodesDecidingOnToken) {
				nodeDecidingOnToken.getLookaheadLeafNodes().add(leafNode);
			}
			deferredLookaheadMap.remove(token);
		}
		token2NodeMap.put(token, leafNode);
	}

	/**
	 * The current lookahead is the number of tokens that have been matched by
	 * the parent rule to decide that the current rule has to be called.
	 *
	 * @return the currentLookahead
	 */
	protected void setCurrentLookahead() {
		XtextTokenStream xtextTokenStream = (XtextTokenStream) input;
		List<Token> lookaheadTokens = xtextTokenStream.getLookaheadTokens();
		for (Token lookaheadToken : lookaheadTokens) {
			LeafNode leafNode = token2NodeMap.get(lookaheadToken);
			if (leafNode == null) {
				List<CompositeNode> mapValue = deferredLookaheadMap.get(lookaheadToken);
				if (mapValue == null) {
					mapValue = Lists.newArrayListWithExpectedSize(3);
					deferredLookaheadMap.put(lookaheadToken, mapValue);
				}
				mapValue.add(currentNode);
			} else {
				currentNode.getLookaheadLeafNodes().add(leafNode);
			}
		}
	}

	/**
	 * Sets the current lookahead to zero. See
	 * {@link AbstractInternalAntlrParser#setCurrentLookahead()}
	 */
	protected void resetLookahead() {
		XtextTokenStream xtextTokenStream = (XtextTokenStream) input;
		xtextTokenStream.resetLookahead();
		if (!token2NodeMap.isEmpty())
			token2NodeMap.clear();
	}

	protected void moveLookaheadInfo(CompositeNode source, CompositeNode target) {
		EList<LeafNode> sourceLookaheadLeafNodes = source.getLookaheadLeafNodes();
		target.getLookaheadLeafNodes().addAll(sourceLookaheadLeafNodes);
		sourceLookaheadLeafNodes.clear();

		for (Token deferredLookaheadToken : deferredLookaheadMap.keySet()) {
			List<CompositeNode> nodesDecidingOnToken = deferredLookaheadMap.get(deferredLookaheadToken);
			while (nodesDecidingOnToken.indexOf(source) != -1) {
				nodesDecidingOnToken.set(nodesDecidingOnToken.indexOf(source), target);
			}
		}
	}

	/**
	 * Match is called to consume unambiguous tokens. It calls input.LA() and
	 * therefore increases the currentLookahead. We need to compensate. See
	 * {@link AbstractInternalAntlrParser#setCurrentLookahead()}
	 *
	 * @see org.antlr.runtime.BaseRecognizer#match(org.antlr.runtime.IntStream,
	 *      int, org.antlr.runtime.BitSet)
	 */
	@Override
	public Object match(IntStream input, int ttype, BitSet follow) throws RecognitionException {
		XtextTokenStream xtextTokenStream = (XtextTokenStream) input;
		int numLookaheadBeforeMatch = xtextTokenStream.getLookaheadTokens().size();
		Object result = super.match(input, ttype, follow);
		if (xtextTokenStream.getLookaheadTokens().size() > numLookaheadBeforeMatch) {
			xtextTokenStream.removeLastLookaheadToken();
		}
		return result;
	}
	
	@Override
	public void emitErrorMessage(String msg) {
		// don't call super, since it would do a plain vanilla
		// System.err.println(msg);
		if (logger.isTraceEnabled())
			logger.trace(msg);
	}

	/**
	 * @return the name of the entry rule.
	 */
	protected abstract String getFirstRuleName();

	public void setUnorderedGroupHelper(IUnorderedGroupHelper unorderedGroupHelper) {
		this.unorderedGroupHelper = unorderedGroupHelper;
	}

	public IUnorderedGroupHelper getUnorderedGroupHelper() {
		return unorderedGroupHelper;
	}

	// externalized usage patterns from generated sources
	
	// currentNode = currentNode.getParent();
    protected void afterParserOrEnumRuleCall() {
    	currentNode = currentNode.getParent();
    	
    	lwNodeBuilder.compress(lwCurrentNode);
    	lwCurrentNode = lwCurrentNode.getParent();
    }
	
    // if (current==null) {
    //========
	//    current = factory.create(grammarAccess.getModelRule().getType().getClassifier());
	//    associateNodeWithAstElement(currentNode.getParent(), current);
    //========
	//}
    protected EObject createModelElementForParent(AbstractRule rule) {
    	return createModelElement(rule.getType().getClassifier(), currentNode.getParent(), lwCurrentNode.getParent());
    }

    protected EObject createModelElement(AbstractRule rule) {
    	return createModelElement(rule.getType().getClassifier(), currentNode, lwCurrentNode);
    }
    
    protected EObject createModelElementForParent(EClassifier classifier) {
    	return createModelElement(classifier, currentNode.getParent(), lwCurrentNode.getParent());
    }

    protected EObject createModelElement(EClassifier classifier) {
    	return createModelElement(classifier, currentNode, lwCurrentNode);
    }
    
    protected EObject createModelElement(EClassifier classifier, CompositeNode compositeNode, ICompositeNode lwCompositeNode) {
    	EObject result = semanticModelBuilder.create(classifier);
    	associateNodeWithAstElement(compositeNode, result);
    	associateNodeWithAstElement(lwCompositeNode, result);
    	return result;
    }
    
    // Assigned action code
    protected EObject forceCreateModelElementAndSet(Action action, EObject value) {
    	EObject result = semanticModelBuilder.create(action.getType().getClassifier());
    	semanticModelBuilder.set(result, action.getFeature(), value, null /* ParserRule */, currentNode);
    	insertCompositeNode(action);
    	associateNodeWithAstElement(currentNode, result);
    	associateNodeWithAstElement(lwCurrentNode, result);
    	return result;
    }
    
    protected EObject forceCreateModelElementAndAdd(Action action, EObject value) {
    	EObject result = semanticModelBuilder.create(action.getType().getClassifier());
    	semanticModelBuilder.add(result, action.getFeature(), value, null /* ParserRule */, currentNode);
    	insertCompositeNode(action);
    	associateNodeWithAstElement(currentNode, result);
    	associateNodeWithAstElement(lwCurrentNode, result);
    	return result;
    }
    
    protected EObject forceCreateModelElement(Action action, EObject value) {
    	EObject result = semanticModelBuilder.create(action.getType().getClassifier());
    	insertCompositeNode(action);
    	associateNodeWithAstElement(currentNode, result);
    	associateNodeWithAstElement(lwCurrentNode, result);
    	return result;
    }

	protected void insertCompositeNode(Action action) {
		CompositeNode newNode = createCompositeNode(action, currentNode.getParent());
    	newNode.getChildren().add(currentNode);
    	moveLookaheadInfo(currentNode, newNode);
    	currentNode = newNode;
    	
    	ICompositeNode newLwCurrentNode = lwNodeBuilder.newCompositeNodeAsParentOf(action, lwCurrentNode.getLookAhead(), lwCurrentNode);
    	lwCurrentNode = newLwCurrentNode;
	}
	
	protected void enterRule() {
		setCurrentLookahead(); 
		resetLookahead();
	}
	
	protected void leaveRule() {
		resetLookahead(); 
    	lastConsumedNode = currentNode;
    	lwLastConsumedNode = lwCurrentNode;
	}
    
	// currentNode = createCompositeNode()
	protected void newCompositeNode(EObject grammarElement) {
		currentNode = createCompositeNode(grammarElement, currentNode);
		XtextTokenStream input = (XtextTokenStream) this.input;
		int lookAhead = input.getCurrentLookAhead();
		lwCurrentNode = lwNodeBuilder.newCompositeNode(grammarElement, lookAhead, lwCurrentNode);
	}
	
	// createLeafNode(token, grammarElement, featureName)
	protected void newLeafNode(Token token, EObject grammarElement) {
		createLeafNode(token, grammarElement, getFeatureName(grammarElement));
	}

	private String getFeatureName(EObject grammarElement) {
		while(grammarElement.eClass() == XtextPackage.Literals.RULE_CALL && grammarElement.eClass() == XtextPackage.Literals.ALTERNATIVES) {
			grammarElement = grammarElement.eContainer();
		}
		if (grammarElement.eClass() == XtextPackage.Literals.ASSIGNMENT)
			return ((Assignment) grammarElement).getFeature();
		return null;
	}

	public void setNodeModelBuilder(NodeModelBuilder nodeModelBuilder) {
		this.lwNodeBuilder = nodeModelBuilder;
	}

	public NodeModelBuilder getNodeModelBuilder() {
		return lwNodeBuilder;
	}
	
	public void setSemanticModelBuilder(IAstFactory semanticModelBuilder) {
		this.semanticModelBuilder = semanticModelBuilder;
	}
	
	public IAstFactory getSemanticModelBuilder() {
		return semanticModelBuilder;
	}
}
