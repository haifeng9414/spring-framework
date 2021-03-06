/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.expression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanExpressionException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Standard implementation of the
 * {@link org.springframework.beans.factory.config.BeanExpressionResolver}
 * interface, parsing and evaluating Spring EL using Spring's expression module.
 *
 * @author Juergen Hoeller
 * @since 3.0
 * @see org.springframework.expression.ExpressionParser
 * @see org.springframework.expression.spel.standard.SpelExpressionParser
 * @see org.springframework.expression.spel.support.StandardEvaluationContext
 */
public class StandardBeanExpressionResolver implements BeanExpressionResolver {

	/** Default expression prefix: "#{" */
	// 表达式前缀
	public static final String DEFAULT_EXPRESSION_PREFIX = "#{";

	/** Default expression suffix: "}" */
	// 表达式后缀
	public static final String DEFAULT_EXPRESSION_SUFFIX = "}";

	private String expressionPrefix = DEFAULT_EXPRESSION_PREFIX;

	private String expressionSuffix = DEFAULT_EXPRESSION_SUFFIX;

	// ExpressionParser用于解析表达式，如+-*/等表达式，默认实现是SpelExpressionParser
	private ExpressionParser expressionParser;

	// 缓存表达式的解析结果
	private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(256);

	// 缓存从指定BeanExpressionContext创建出来的StandardEvaluationContext
	private final Map<BeanExpressionContext, StandardEvaluationContext> evaluationCache = new ConcurrentHashMap<>(8);

	private final ParserContext beanExpressionParserContext = new ParserContext() {
		@Override
		public boolean isTemplate() {
			return true;
		}
		@Override
		public String getExpressionPrefix() {
			return expressionPrefix;
		}
		@Override
		public String getExpressionSuffix() {
			return expressionSuffix;
		}
	};


	/**
	 * Create a new {@code StandardBeanExpressionResolver} with default settings.
	 */
	public StandardBeanExpressionResolver() {
		this.expressionParser = new SpelExpressionParser();
	}

	/**
	 * Create a new {@code StandardBeanExpressionResolver} with the given bean class loader,
	 * using it as the basis for expression compilation.
	 * @param beanClassLoader the factory's bean class loader
	 */
	public StandardBeanExpressionResolver(@Nullable ClassLoader beanClassLoader) {
		this.expressionParser = new SpelExpressionParser(new SpelParserConfiguration(null, beanClassLoader));
	}


	/**
	 * Set the prefix that an expression string starts with.
	 * The default is "#{".
	 * @see #DEFAULT_EXPRESSION_PREFIX
	 */
	public void setExpressionPrefix(String expressionPrefix) {
		Assert.hasText(expressionPrefix, "Expression prefix must not be empty");
		this.expressionPrefix = expressionPrefix;
	}

	/**
	 * Set the suffix that an expression string ends with.
	 * The default is "}".
	 * @see #DEFAULT_EXPRESSION_SUFFIX
	 */
	public void setExpressionSuffix(String expressionSuffix) {
		Assert.hasText(expressionSuffix, "Expression suffix must not be empty");
		this.expressionSuffix = expressionSuffix;
	}

	/**
	 * Specify the EL parser to use for expression parsing.
	 * <p>Default is a {@link org.springframework.expression.spel.standard.SpelExpressionParser},
	 * compatible with standard Unified EL style expression syntax.
	 */
	public void setExpressionParser(ExpressionParser expressionParser) {
		Assert.notNull(expressionParser, "ExpressionParser must not be null");
		this.expressionParser = expressionParser;
	}


	@Override
	@Nullable
	public Object evaluate(@Nullable String value, BeanExpressionContext evalContext) throws BeansException {
		if (!StringUtils.hasLength(value)) {
			return value;
		}
		try {
			// 先查看缓存中是否有
			Expression expr = this.expressionCache.get(value);
			if (expr == null) {
				// 如果缓存中没有则创建Expression，默认实现是SpelExpressionParser，SpelExpressionParser根据字符串是否包含#{}来判断返回何种Expression，对于普通字符串，
				// 返回的是LiteralExpression，如果是spel表达式则返回SpelExpression，如果表达式即包含普通字符串又包含spel表达是，如abc#{foo}def，则返回CompositeStringExpression，
				// CompositeStringExpression内部维护了所有的表达式
				expr = this.expressionParser.parseExpression(value, this.beanExpressionParserContext);
				// 缓存解析结果
				this.expressionCache.put(value, expr);
			}
			// 同样从缓存中获取StandardEvaluationContext，没有则创建
			StandardEvaluationContext sec = this.evaluationCache.get(evalContext);
			if (sec == null) {
				sec = new StandardEvaluationContext(evalContext);
				// 添加若干个PropertyAccessor，PropertyAccessor用于对象属性的读写
				// BeanExpressionContextAccessor实现了对BeanExpressionContext的读写，即将BeanExpressionContext视为对象，beanName视为属性名，
				// BeanExpressionContextAccessor包含了BeanFactory和scope，进行读操作实际上就是获取bean，BeanExpressionContextAccessor不支持写操作
				sec.addPropertyAccessor(new BeanExpressionContextAccessor());
				// 和BeanExpressionContextAccessor类型，不过操作的是BeanFactory，同样只支持读不支持写
				sec.addPropertyAccessor(new BeanFactoryAccessor());
				// 对Map类型的对象进行读写
				sec.addPropertyAccessor(new MapAccessor());
				// 对Environment类型的对象进行读写，Environment中包含了Property，实际上操作的就是这些Property，EnvironmentAccessor不支持写操作
				sec.addPropertyAccessor(new EnvironmentAccessor());
				// BeanFactoryResolver解析beanName字符串，解析操作实际上就是从BeanFactory获取bean
				sec.setBeanResolver(new BeanFactoryResolver(evalContext.getBeanFactory()));
				// TypeLocator用于根据字符串获取类，StandardTypeLocator的实现就是从classLoader中解析类名返回类
				sec.setTypeLocator(new StandardTypeLocator(evalContext.getBeanFactory().getBeanClassLoader()));
				// ConversionService用于属性的类型转换
				ConversionService conversionService = evalContext.getBeanFactory().getConversionService();
				if (conversionService != null) {
					sec.setTypeConverter(new StandardTypeConverter(conversionService));
				}
				// 空方法，供子类实现
				customizeEvaluationContext(sec);
				// 缓存StandardEvaluationContext
				this.evaluationCache.put(evalContext, sec);
			}
			// 进行表达式解析，根据expr类型将会执行不同的动作，如最简单的普通字符串的表达式，则getValue方法直接返回字符串，如果是spel表达式则执行spel的解析逻辑，如果是
			// CompositeStringExpression则遍历内部的表达式，组合所有的解析结果返回，spel表达式的解析过程不详细说明，像#{foo}这种简单的表达式的实现，实际上就是从上面注册的
			// 若干个PropertyAccessor中获取值而已
			return expr.getValue(sec);
		}
		catch (Throwable ex) {
			throw new BeanExpressionException("Expression parsing failed", ex);
		}
	}

	/**
	 * Template method for customizing the expression evaluation context.
	 * <p>The default implementation is empty.
	 */
	protected void customizeEvaluationContext(StandardEvaluationContext evalContext) {
	}

}
