注入bean的属性过程中，有一步解析字符串值的过程，这一过程发生在[BeanDefinitionValueResolver]的`resolveValueIfNecessary()`方法解析[TypedStringValue]或其他值为字符串类型的属性时发生，解析这种值时`resolveValueIfNecessary()`方法在返回值之前会调用`evaluate()`方法对字符串进行解析，代码：
```java
@Nullable
protected Object evaluate(TypedStringValue value) {
    Object result = doEvaluate(value.getValue());
    // 如果解析出来的值和原值不一样表示原值是个表达式，则在这里标记成动态类型的值
    if (!ObjectUtils.nullSafeEquals(result, value.getValue())) {
        value.setDynamic();
    }
    return result;
}

@Nullable
private Object doEvaluate(@Nullable String value) {
    return this.beanFactory.evaluateBeanDefinitionString(value, this.beanDefinition);
}
```

解析最终交由[BeanFactory]的`evaluateBeanDefinitionString()`方法完成，代码：
```java
@Nullable
protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
    if (this.beanExpressionResolver == null) {
        return value;
    }

    Scope scope = null;
    if (beanDefinition != null) {
        String scopeName = beanDefinition.getScope();
        if (scopeName != null) {
            scope = getRegisteredScope(scopeName);
        }
    }
    return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
}
```

[BeanFactory]中字符串的解析有委托给了[BeanExpressionResolver]，而默认情况下容器启动时会注册[StandardBeanExpressionResolver]作为[BeanExpressionResolver]的实现，先看[BeanExpressionResolver]接口的定义，代码：
```java
public interface BeanExpressionResolver {
	@Nullable
	Object evaluate(@Nullable String value, BeanExpressionContext evalContext) throws BeansException;
}
```

[BeanExpressionContext]保存了解析时的上下文信息，代码：
```java
public class BeanExpressionContext {

	private final ConfigurableBeanFactory beanFactory;

	@Nullable
	private final Scope scope;


	public BeanExpressionContext(ConfigurableBeanFactory beanFactory, @Nullable Scope scope) {
		Assert.notNull(beanFactory, "BeanFactory must not be null");
		this.beanFactory = beanFactory;
		this.scope = scope;
	}

	public final ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	@Nullable
	public final Scope getScope() {
		return this.scope;
	}


	public boolean containsObject(String key) {
		return (this.beanFactory.containsBean(key) ||
				(this.scope != null && this.scope.resolveContextualObject(key) != null));
	}

	@Nullable
	public Object getObject(String key) {
		if (this.beanFactory.containsBean(key)) {
			return this.beanFactory.getBean(key);
		}
		else if (this.scope != null){
			return this.scope.resolveContextualObject(key);
		}
		else {
			return null;
		}
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof BeanExpressionContext)) {
			return false;
		}
		BeanExpressionContext otherContext = (BeanExpressionContext) other;
		return (this.beanFactory == otherContext.beanFactory && this.scope == otherContext.scope);
	}

	@Override
	public int hashCode() {
		return this.beanFactory.hashCode();
	}
}
```

最后是[StandardBeanExpressionResolver]的代码：
```java
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
```

[BeanDefinitionValueResolver]: aaa
[TypedStringValue]: aaa
[BeanExpressionResolver]: aaa
[BeanFactory]: aaa
[StandardBeanExpressionResolver]: aaa
[BeanExpressionContext]: aaa
