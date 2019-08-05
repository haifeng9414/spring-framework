[SimpleUrlHandlerMapping]用于根据请求返回[HandlerExecutionChain]，[HandlerExecutionChain]对应的是controller，如：
```xml
<bean id="simpleUrlMapping" class="org.springframework.web.servlet.handler.SimpleUrlHandlerMapping">
    <property name="mappings">
        <props>
            <prop key="/userlist">userController</prop>
        </props>
    </property>
</bean>

<bean id="userController" class="com.dhf.UserController"/>
```

下面分析[SimpleUrlHandlerMapping]的实现原理，先看[SimpleUrlHandlerMapping]的继承结构：
[SimpleUrlHandlerMapping继承结构](../../img/SimpleUrlHandlerMapping.png)

[ServletContextAware]接口用于在bean创建时设置[ServletContext]，这使得[SimpleUrlHandlerMapping]在创建出来后就能够访问到[ServletContext]，而为[ServletContextAware]设置[ServletContext]是[ServletContextAwareProcessor]实现的，该[BeanPostProcessor]在[AbstractRefreshableWebApplicationContext]中被添加到容器的，[ServletContextAware]代码：
```java
public interface ServletContextAware extends Aware {
	void setServletContext(ServletContext servletContext);
}
```
[ApplicationObjectSupport]类实现了[ApplicationContextAware]接口，在设置[ApplicationContext]的同时创建一个将[ApplicationContext]作为数据源的[MessageSourceAccessor]，代码：
```java
public abstract class ApplicationObjectSupport implements ApplicationContextAware {

	/** Logger that is available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());

	/** ApplicationContext this object runs in */
	@Nullable
	private ApplicationContext applicationContext;

	/** MessageSourceAccessor for easy message access */
	@Nullable
	private MessageSourceAccessor messageSourceAccessor;

	@Override
	public final void setApplicationContext(@Nullable ApplicationContext context) throws BeansException {
		// 如果context为空并且applicationContext是非必须的，则清空下面两个属性
		if (context == null && !isContextRequired()) {
			// Reset internal context state.
			this.applicationContext = null;
			this.messageSourceAccessor = null;
		}
		else if (this.applicationContext == null) {
			// Initialize with passed-in context.
			// 如果context不是需要的context类型，则报错
			if (!requiredContextClass().isInstance(context)) {
				throw new ApplicationContextException(
						"Invalid application context: needs to be of type [" + requiredContextClass().getName() + "]");
			}
			this.applicationContext = context;
			// MessageSourceAccessor能够从MessageSource中获取属性，而ApplicationContext接口继承了MessageSource接口，所以这里的
			// MessageSourceAccessor相当于能够从context中获取属性
			this.messageSourceAccessor = new MessageSourceAccessor(context);
			// 供子类实现
			initApplicationContext(context);
		}
		else {
			// Ignore reinitialization if same context passed in.
			// context不能重复设置
			if (this.applicationContext != context) {
				throw new ApplicationContextException(
						"Cannot reinitialize with different application context: current one is [" +
						this.applicationContext + "], passed-in one is [" + context + "]");
			}
		}
	}
  
	protected boolean isContextRequired() {
		return false;
	}
  
	protected Class<?> requiredContextClass() {
		return ApplicationContext.class;
	}
  
	protected void initApplicationContext(ApplicationContext context) throws BeansException {
		initApplicationContext();
	}
  
	protected void initApplicationContext() throws BeansException {
	}
  
	@Nullable
	public final ApplicationContext getApplicationContext() throws IllegalStateException {
		if (this.applicationContext == null && isContextRequired()) {
			throw new IllegalStateException(
					"ApplicationObjectSupport instance [" + this + "] does not run in an ApplicationContext");
		}
		return this.applicationContext;
	}
  
	protected final ApplicationContext obtainApplicationContext() {
		ApplicationContext applicationContext = getApplicationContext();
		Assert.state(applicationContext != null, "No ApplicationContext");
		return applicationContext;
	}
  
	@Nullable
	protected final MessageSourceAccessor getMessageSourceAccessor() throws IllegalStateException {
		if (this.messageSourceAccessor == null && isContextRequired()) {
			throw new IllegalStateException(
					"ApplicationObjectSupport instance [" + this + "] does not run in an ApplicationContext");
		}
		return this.messageSourceAccessor;
	}

}
```

[WebApplicationObjectSupport]接口继承[ApplicationObjectSupport]并实现[ServletContextAware]接口，既将[ApplicationContext]和[ServletContext]做了一个组合，代码：
```java
public abstract class WebApplicationObjectSupport extends ApplicationObjectSupport implements ServletContextAware {

	@Nullable
	private ServletContext servletContext;

	@Override
	public final void setServletContext(ServletContext servletContext) {
		// initApplicationContext中可能会设置servletContext，这里的判断防止对同一个servletContext重复调用initServletContext方法
		if (servletContext != this.servletContext) {
			this.servletContext = servletContext;
			// 供子类实现
			initServletContext(servletContext);
		}
	}
  
	@Override
	protected boolean isContextRequired() {
		return true;
	}
  
	@Override
	protected void initApplicationContext(ApplicationContext context) {
		super.initApplicationContext(context);
		// 如果servletContext为空则尝试从WebApplicationContext中获取servletContext
		if (this.servletContext == null && context instanceof WebApplicationContext) {
			this.servletContext = ((WebApplicationContext) context).getServletContext();
			if (this.servletContext != null) {
				// 供子类实现
				initServletContext(this.servletContext);
			}
		}
	}
  
	protected void initServletContext(ServletContext servletContext) {
	}
  
	@Nullable
	protected final WebApplicationContext getWebApplicationContext() throws IllegalStateException {
		ApplicationContext ctx = getApplicationContext();
		if (ctx instanceof WebApplicationContext) {
			return (WebApplicationContext) getApplicationContext();
		}
		else if (isContextRequired()) {
			throw new IllegalStateException("WebApplicationObjectSupport instance [" + this +
					"] does not run in a WebApplicationContext but in: " + ctx);
		}
		else {
			return null;
		}
	}
  
	@Nullable
	protected final ServletContext getServletContext() throws IllegalStateException {
		if (this.servletContext != null) {
			return this.servletContext;
		}
		ServletContext servletContext = null;
		WebApplicationContext wac = getWebApplicationContext();
		if (wac != null) {
			servletContext = wac.getServletContext();
		}
		if (servletContext == null && isContextRequired()) {
			throw new IllegalStateException("WebApplicationObjectSupport instance [" + this +
					"] does not run within a ServletContext. Make sure the object is fully configured!");
		}
		return servletContext;
	}
  
	protected final File getTempDir() throws IllegalStateException {
		// 获取临时目录
		ServletContext servletContext = getServletContext();
		Assert.state(servletContext != null, "ServletContext is required");
		return WebUtils.getTempDir(servletContext);
	}

}
```

[HandlerMapping]定义了若干个常量值，将作为[HttpServletRequest]的各种属性的key，[HandlerMapping]还定义了`getHandler()`方法从[HttpServletRequest]获取[HandlerExecutionChain]，代码：
```java
public interface HandlerMapping {
	String PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE = HandlerMapping.class.getName() + ".pathWithinHandlerMapping";

	String BEST_MATCHING_PATTERN_ATTRIBUTE = HandlerMapping.class.getName() + ".bestMatchingPattern";

	String INTROSPECT_TYPE_LEVEL_MAPPING = HandlerMapping.class.getName() + ".introspectTypeLevelMapping";

	String URI_TEMPLATE_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".uriTemplateVariables";

	String MATRIX_VARIABLES_ATTRIBUTE = HandlerMapping.class.getName() + ".matrixVariables";

	String PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE = HandlerMapping.class.getName() + ".producibleMediaTypes";

	/**
	 * Return a handler and any interceptors for this request. The choice may be made
	 * on request URL, session state, or any factor the implementing class chooses.
	 * <p>The returned HandlerExecutionChain contains a handler Object, rather than
	 * even a tag interface, so that handlers are not constrained in any way.
	 * For example, a HandlerAdapter could be written to allow another framework's
	 * handler objects to be used.
	 * <p>Returns {@code null} if no match was found. This is not an error.
	 * The DispatcherServlet will query all registered HandlerMapping beans to find
	 * a match, and only decide there is an error if none can find a handler.
	 * @param request current HTTP request
	 * @return a HandlerExecutionChain instance containing handler object and
	 * any interceptors, or {@code null} if no mapping found
	 * @throws Exception if there is an internal error
	 */
	@Nullable
	HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception;

}
```

从`getHandler()`方法上的注释可以看到[HandlerExecutionChain]的作用，这里也顺便放一下[HandlerExecutionChain]的代码：
```java
public class HandlerExecutionChain {

	private static final Log logger = LogFactory.getLog(HandlerExecutionChain.class);

	// 表示能够执行请求的handler，这里是Object而不是某种接口，所以handler的实现形式是任意的
	private final Object handler;

	@Nullable
	// HandlerInterceptor接口定义了3个方法，分别是preHandle、postHandle和afterCompletion，在处理请求过程中的不同时间点执行
	private HandlerInterceptor[] interceptors;

	@Nullable
	// 这里还定义一个List<HandlerInterceptor>是为了在添加HandlerInterceptor时方便
	private List<HandlerInterceptor> interceptorList;

	private int interceptorIndex = -1;

	public HandlerExecutionChain(Object handler) {
		this(handler, (HandlerInterceptor[]) null);
	}
  
	public HandlerExecutionChain(Object handler, @Nullable HandlerInterceptor... interceptors) {
		if (handler instanceof HandlerExecutionChain) {
			HandlerExecutionChain originalChain = (HandlerExecutionChain) handler;
			this.handler = originalChain.getHandler();
			this.interceptorList = new ArrayList<>();
			CollectionUtils.mergeArrayIntoCollection(originalChain.getInterceptors(), this.interceptorList);
			CollectionUtils.mergeArrayIntoCollection(interceptors, this.interceptorList);
		}
		else {
			this.handler = handler;
			this.interceptors = interceptors;
		}
	}
  
	public Object getHandler() {
		return this.handler;
	}

	public void addInterceptor(HandlerInterceptor interceptor) {
		initInterceptorList().add(interceptor);
	}

	public void addInterceptors(HandlerInterceptor... interceptors) {
		if (!ObjectUtils.isEmpty(interceptors)) {
			CollectionUtils.mergeArrayIntoCollection(interceptors, initInterceptorList());
		}
	}

	private List<HandlerInterceptor> initInterceptorList() {
		if (this.interceptorList == null) {
			this.interceptorList = new ArrayList<>();
			if (this.interceptors != null) {
				// An interceptor array specified through the constructor
				CollectionUtils.mergeArrayIntoCollection(this.interceptors, this.interceptorList);
			}
		}
		this.interceptors = null;
		return this.interceptorList;
	}

	@Nullable
	public HandlerInterceptor[] getInterceptors() {
		if (this.interceptors == null && this.interceptorList != null) {
			this.interceptors = this.interceptorList.toArray(new HandlerInterceptor[0]);
		}
		return this.interceptors;
	}
  
	// 遍历HandlerInterceptor调用preHandle方法
	boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = 0; i < interceptors.length; i++) {
				HandlerInterceptor interceptor = interceptors[i];
				if (!interceptor.preHandle(request, response, this.handler)) {
					// 如果preHandle方法返回false表示终止遍历，同时也表示终止请求的执行，所以这里需要调用triggerAfterCompletion
					triggerAfterCompletion(request, response, null);
					return false;
				}
				this.interceptorIndex = i;
			}
		}
		return true;
	}
  
	void applyPostHandle(HttpServletRequest request, HttpServletResponse response, @Nullable ModelAndView mv)
			throws Exception {

		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = interceptors.length - 1; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				interceptor.postHandle(request, response, this.handler, mv);
			}
		}
	}
  
	void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, @Nullable Exception ex)
			throws Exception {

		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = this.interceptorIndex; i >= 0; i--) {
				HandlerInterceptor interceptor = interceptors[i];
				try {
					interceptor.afterCompletion(request, response, this.handler, ex);
				}
				catch (Throwable ex2) {
					// 遍历过程中发生异常记录日志，但是日志中没有强调发生的异常是哪个HandlerInterceptor抛出的，只是把错误信息打印，
					// 实际上错误信息中就包含了HandlerInterceptor的信息，所以以后自己记录日志时也不用纠结应该如果输出抛出错误的对象
					logger.error("HandlerInterceptor.afterCompletion threw exception", ex2);
				}
			}
		}
	}
  
	// 调用AsyncHandlerInterceptor，AsyncHandlerInterceptor继承自HandlerInterceptor，添加了一个afterConcurrentHandlingStarted方法，
	// 当开始并发执行一个请求时被调用，当并发执行请求时HandlerInterceptor的postHandle和afterCompletion不会被调用，因为并发执行请求时什么时候
	// 请求执行完成是不确定的
	void applyAfterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response) {
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			for (int i = interceptors.length - 1; i >= 0; i--) {
				if (interceptors[i] instanceof AsyncHandlerInterceptor) {
					try {
						AsyncHandlerInterceptor asyncInterceptor = (AsyncHandlerInterceptor) interceptors[i];
						asyncInterceptor.afterConcurrentHandlingStarted(request, response, this.handler);
					}
					catch (Throwable ex) {
						logger.error("Interceptor [" + interceptors[i] + "] failed in afterConcurrentHandlingStarted", ex);
					}
				}
			}
		}
	}

	@Override
	public String toString() {
		Object handler = getHandler();
		StringBuilder sb = new StringBuilder();
		sb.append("HandlerExecutionChain with handler [").append(handler).append("]");
		HandlerInterceptor[] interceptors = getInterceptors();
		if (!ObjectUtils.isEmpty(interceptors)) {
			sb.append(" and ").append(interceptors.length).append(" interceptor");
			if (interceptors.length > 1) {
				sb.append("s");
			}
		}
		return sb.toString();
	}

}
```

[AbstractHandlerMapping]用模版方法模式定义了执行请求的基本过程，对cors请求提供支持，代码：
```java
public abstract class AbstractHandlerMapping extends WebApplicationObjectSupport implements HandlerMapping, Ordered {

	@Nullable
	// 默认handler
	private Object defaultHandler;

	private UrlPathHelper urlPathHelper = new UrlPathHelper();

	private PathMatcher pathMatcher = new AntPathMatcher();

	private final List<Object> interceptors = new ArrayList<>();

	private final List<HandlerInterceptor> adaptedInterceptors = new ArrayList<>();

	// UrlBasedCorsConfigurationSource用于根据请求返回对应的cors配置，实现逻辑就是用请求路径作为key，CorsConfiguration作为value
  // 保存cors配置，每次获取请求的CorsConfiguration时，直接从UrlBasedCorsConfigurationSource的cors配置中获取请求对应的CorsConfiguration
	private final UrlBasedCorsConfigurationSource globalCorsConfigSource = new UrlBasedCorsConfigurationSource();

	// CorsProcessor用于处理cors相关请求，为cors response添加cors相关的请求头
	private CorsProcessor corsProcessor = new DefaultCorsProcessor();

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered

	public void setDefaultHandler(@Nullable Object defaultHandler) {
		this.defaultHandler = defaultHandler;
	}
  
	@Nullable
	public Object getDefaultHandler() {
		return this.defaultHandler;
	}
  
	public void setAlwaysUseFullPath(boolean alwaysUseFullPath) {
		this.urlPathHelper.setAlwaysUseFullPath(alwaysUseFullPath);
		this.globalCorsConfigSource.setAlwaysUseFullPath(alwaysUseFullPath);
	}
  
	public void setUrlDecode(boolean urlDecode) {
		this.urlPathHelper.setUrlDecode(urlDecode);
		this.globalCorsConfigSource.setUrlDecode(urlDecode);
	}
  
	public void setRemoveSemicolonContent(boolean removeSemicolonContent) {
		this.urlPathHelper.setRemoveSemicolonContent(removeSemicolonContent);
		this.globalCorsConfigSource.setRemoveSemicolonContent(removeSemicolonContent);
	}
  
	public void setUrlPathHelper(UrlPathHelper urlPathHelper) {
		Assert.notNull(urlPathHelper, "UrlPathHelper must not be null");
		this.urlPathHelper = urlPathHelper;
		this.globalCorsConfigSource.setUrlPathHelper(urlPathHelper);
	}
  
	public UrlPathHelper getUrlPathHelper() {
		return urlPathHelper;
	}
  
	public void setPathMatcher(PathMatcher pathMatcher) {
		Assert.notNull(pathMatcher, "PathMatcher must not be null");
		this.pathMatcher = pathMatcher;
		this.globalCorsConfigSource.setPathMatcher(pathMatcher);
	}
  
	public PathMatcher getPathMatcher() {
		return this.pathMatcher;
	}
  
	public void setInterceptors(Object... interceptors) {
		this.interceptors.addAll(Arrays.asList(interceptors));
	}
  
	public void setCorsConfigurations(Map<String, CorsConfiguration> corsConfigurations) {
		this.globalCorsConfigSource.setCorsConfigurations(corsConfigurations);
	}
  
	public Map<String, CorsConfiguration> getCorsConfigurations() {
		return this.globalCorsConfigSource.getCorsConfigurations();
	}
  
	public void setCorsProcessor(CorsProcessor corsProcessor) {
		Assert.notNull(corsProcessor, "CorsProcessor must not be null");
		this.corsProcessor = corsProcessor;
	}
  
	public CorsProcessor getCorsProcessor() {
		return this.corsProcessor;
	}
  
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}
  
	@Override
	protected void initApplicationContext() throws BeansException {
		// 供子类实现
		extendInterceptors(this.interceptors);
		// 将所有实现了MappedInterceptor接口的bean添加到adaptedInterceptors中
		detectMappedInterceptors(this.adaptedInterceptors);
		// 将interceptors中的对象添加到adaptedInterceptors中
		initInterceptors();
	}
  
	protected void extendInterceptors(List<Object> interceptors) {
	}
  
	protected void detectMappedInterceptors(List<HandlerInterceptor> mappedInterceptors) {
		mappedInterceptors.addAll(
				BeanFactoryUtils.beansOfTypeIncludingAncestors(
						obtainApplicationContext(), MappedInterceptor.class, true, false).values());
	}
  
	protected void initInterceptors() {
		if (!this.interceptors.isEmpty()) {
			for (int i = 0; i < this.interceptors.size(); i++) {
				Object interceptor = this.interceptors.get(i);
				if (interceptor == null) {
					throw new IllegalArgumentException("Entry number " + i + " in interceptors array is null");
				}
				this.adaptedInterceptors.add(adaptInterceptor(interceptor));
			}
		}
	}
  
	protected HandlerInterceptor adaptInterceptor(Object interceptor) {
		if (interceptor instanceof HandlerInterceptor) {
			return (HandlerInterceptor) interceptor;
		}
		// WebRequestInterceptor接口的方法和HandlerInterceptor接口的一样，但是没有继承HandlerInterceptor接口，这里的
		// WebRequestHandlerInterceptorAdapter就是作为一个适配器
		else if (interceptor instanceof WebRequestInterceptor) {
			return new WebRequestHandlerInterceptorAdapter((WebRequestInterceptor) interceptor);
		}
		else {
			throw new IllegalArgumentException("Interceptor type not supported: " + interceptor.getClass().getName());
		}
	}
  
	@Nullable
	protected final HandlerInterceptor[] getAdaptedInterceptors() {
		return (!this.adaptedInterceptors.isEmpty() ?
				this.adaptedInterceptors.toArray(new HandlerInterceptor[0]) : null);
	}
  
	@Nullable
	protected final MappedInterceptor[] getMappedInterceptors() {
		List<MappedInterceptor> mappedInterceptors = new ArrayList<>(this.adaptedInterceptors.size());
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				mappedInterceptors.add((MappedInterceptor) interceptor);
			}
		}
		return (!mappedInterceptors.isEmpty() ? mappedInterceptors.toArray(new MappedInterceptor[0]) : null);
	}
  
	@Override
	@Nullable
	public final HandlerExecutionChain getHandler(HttpServletRequest request) throws Exception {
		// 从request获取handler，供子类实习
		Object handler = getHandlerInternal(request);
		if (handler == null) {
			// 获取默认handler
			handler = getDefaultHandler();
		}
		if (handler == null) {
			return null;
		}
		// Bean name or resolved handler?
		if (handler instanceof String) {
			String handlerName = (String) handler;
			handler = obtainApplicationContext().getBean(handlerName);
		}

		HandlerExecutionChain executionChain = getHandlerExecutionChain(handler, request);
		// 如果是cors请求
		if (CorsUtils.isCorsRequest(request)) {
			// 从globalCorsConfigSource中获取CorsConfiguration
			CorsConfiguration globalConfig = this.globalCorsConfigSource.getCorsConfiguration(request);
			// 如果handler是CorsConfigurationSource类型的，则从handler中获取CorsConfiguration
			CorsConfiguration handlerConfig = getCorsConfiguration(handler, request);
			// 结合两个CorsConfiguration
			CorsConfiguration config = (globalConfig != null ? globalConfig.combine(handlerConfig) : handlerConfig);
			// 将cors逻辑添加到HandlerExecutionChain中
			executionChain = getCorsHandlerExecutionChain(request, executionChain, config);
		}
		return executionChain;
	}
  
	@Nullable
	protected abstract Object getHandlerInternal(HttpServletRequest request) throws Exception;

	protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
		// 创建HandlerExecutionChain实例
		HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
				(HandlerExecutionChain) handler : new HandlerExecutionChain(handler));

		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request);
		for (HandlerInterceptor interceptor : this.adaptedInterceptors) {
			if (interceptor instanceof MappedInterceptor) {
				MappedInterceptor mappedInterceptor = (MappedInterceptor) interceptor;
				// 判断请求的路径是否满足条件，如果满足则添加到HandlerExecutionChain中
				if (mappedInterceptor.matches(lookupPath, this.pathMatcher)) {
					chain.addInterceptor(mappedInterceptor.getInterceptor());
				}
			}
			else {
				// 普通类型的HandlerInterceptor直接添加
				chain.addInterceptor(interceptor);
			}
		}
		return chain;
	}
  
	@Nullable
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		Object resolvedHandler = handler;
		if (handler instanceof HandlerExecutionChain) {
			resolvedHandler = ((HandlerExecutionChain) handler).getHandler();
		}
		if (resolvedHandler instanceof CorsConfigurationSource) {
			return ((CorsConfigurationSource) resolvedHandler).getCorsConfiguration(request);
		}
		return null;
	}
  
	protected HandlerExecutionChain getCorsHandlerExecutionChain(HttpServletRequest request,
			HandlerExecutionChain chain, @Nullable CorsConfiguration config) {

		// 判断是否为preflight request，如果是则返回一个preflight response而不是普通的业务响应
		if (CorsUtils.isPreFlightRequest(request)) {
			HandlerInterceptor[] interceptors = chain.getInterceptors();
			chain = new HandlerExecutionChain(new PreFlightHandler(config), interceptors);
		}
		else {
			// 如果是普通cors请求，则添加一个CorsInterceptor，因为cors请求的响应必须包含cors相关的header
			chain.addInterceptor(new CorsInterceptor(config));
		}
		return chain;
	}


	private class PreFlightHandler implements HttpRequestHandler, CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public PreFlightHandler(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
			corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}


	private class CorsInterceptor extends HandlerInterceptorAdapter implements CorsConfigurationSource {

		@Nullable
		private final CorsConfiguration config;

		public CorsInterceptor(@Nullable CorsConfiguration config) {
			this.config = config;
		}

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
				throws Exception {

			return corsProcessor.processRequest(this.config, request, response);
		}

		@Override
		@Nullable
		public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
			return this.config;
		}
	}

}
```

[MatchableHandlerMapping]接口在[HandlerMapping]接口的基础上定义了`match()`方法，使得其实现能够在
```java
public interface MatchableHandlerMapping extends HandlerMapping {
	@Nullable
	RequestMatchResult match(HttpServletRequest request, String pattern);

}
```

[SimpleUrlHandlerMapping]: aaa
[HandlerExecutionChain]: aaa
[ServletContextAware]: aaa
[ServletContext]: aaa
[ServletContextAwareProcessor]: aaa
[BeanPostProcessor]: aaa
[AbstractRefreshableWebApplicationContext]: aaa