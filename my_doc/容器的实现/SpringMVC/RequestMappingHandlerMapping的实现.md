[RequestMappingHandlerMapping]的继承结构：
![RequestMappingHandlerMapping的继承结构](../../img/RequestMappingHandlerMapping.png)

上面大部分接口在其他笔记都说过了，可以看笔记[如何实现请求的分发和响应](如何实现请求的分发和响应.md)和[SimpleUrlHandlerMapping的实现](SimpleUrlHandlerMapping的实现.md)，这里直接从没讲过的[AbstractHandlerMethodMapping]类开始分析，[AbstractHandlerMethodMapping]用模版方法默认定义了好了自动解析mapping和根据request获取mapping和对应的bean的基本逻辑，代码：
```java
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {
	// 如果beanName以scopedTarget.开头则表示该bean被代理了
	private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

	// 针对preflight request的HandlerMethod
	private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH =
			new HandlerMethod(new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

	private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

	static {
		ALLOW_CORS_CONFIG.addAllowedOrigin("*");
		ALLOW_CORS_CONFIG.addAllowedMethod("*");
		ALLOW_CORS_CONFIG.addAllowedHeader("*");
		ALLOW_CORS_CONFIG.setAllowCredentials(true);
	}

	// 获取handler时是否考虑parent beanFactory
	private boolean detectHandlerMethodsInAncestorContexts = false;

	@Nullable
	private HandlerMethodMappingNamingStrategy<T> namingStrategy;

	// 保存解析到的mapping配置
	private final MappingRegistry mappingRegistry = new MappingRegistry();

	public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
		this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
	}
  
	public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
		this.namingStrategy = namingStrategy;
	}

	@Nullable
	public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
		return this.namingStrategy;
	}

	public Map<T, HandlerMethod> getHandlerMethods() {
		this.mappingRegistry.acquireReadLock();
		try {
			return Collections.unmodifiableMap(this.mappingRegistry.getMappings());
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	@Nullable
	public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
		return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
	}

	MappingRegistry getMappingRegistry() {
		return this.mappingRegistry;
	}

	public void registerMapping(T mapping, Object handler, Method method) {
		this.mappingRegistry.register(mapping, handler, method);
	}

	public void unregisterMapping(T mapping) {
		this.mappingRegistry.unregister(mapping);
	}


	// Handler method detection

	@Override
	public void afterPropertiesSet() {
		initHandlerMethods();
	}

	protected void initHandlerMethods() {
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for request mappings in application context: " + getApplicationContext());
		}
		// 获取所有的bean
		String[] beanNames = (this.detectHandlerMethodsInAncestorContexts ?
				BeanFactoryUtils.beanNamesForTypeIncludingAncestors(obtainApplicationContext(), Object.class) :
				obtainApplicationContext().getBeanNamesForType(Object.class));

		for (String beanName : beanNames) {
			// 跳过被代理的bean
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
				Class<?> beanType = null;
				try {
					beanType = obtainApplicationContext().getType(beanName);
				}
				catch (Throwable ex) {
					// An unresolvable bean type, probably from a lazy bean - let's ignore it.
					if (logger.isDebugEnabled()) {
						logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
					}
				}
				// isHandler判断指定类型是否是一个handler，供子类实现
				if (beanType != null && isHandler(beanType)) {
					detectHandlerMethods(beanName);
				}
			}
		}
		// 空方法，供子类实现
		handlerMethodsInitialized(getHandlerMethods());
	}

	protected void detectHandlerMethods(final Object handler) {
		Class<?> handlerType = (handler instanceof String ?
				obtainApplicationContext().getType((String) handler) : handler.getClass());

		if (handlerType != null) {
			// 如果是内部类，既类名包含$$，则返回其父类，既外部类
			final Class<?> userType = ClassUtils.getUserClass(handlerType);
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							// 供子类实现，根据handler的类型和当前遍历到的方法返回T，也就是mapping
							return getMappingForMethod(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isDebugEnabled()) {
				logger.debug(methods.size() + " request handler methods found on " + userType + ": " + methods);
			}
			methods.forEach((method, mapping) -> {
				// 返回真正定义了method的那个类上的method对象
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
				// 保存解析到的结果
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}

	protected void registerHandlerMethod(Object handler, Method method, T mapping) {
		this.mappingRegistry.register(mapping, handler, method);
	}

	protected HandlerMethod createHandlerMethod(Object handler, Method method) {
		HandlerMethod handlerMethod;
		if (handler instanceof String) {
			String beanName = (String) handler;
			handlerMethod = new HandlerMethod(beanName,
					obtainApplicationContext().getAutowireCapableBeanFactory(), method);
		}
		else {
			handlerMethod = new HandlerMethod(handler, method);
		}
		return handlerMethod;
	}

	@Nullable
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
		return null;
	}

	protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
	}


	// Handler method lookup

	@Override
	protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up handler method for path " + lookupPath);
		}
		this.mappingRegistry.acquireReadLock();
		try {
			// 尝试根据lookupPath获取mapping，再根据mapping和request判断mapping是否符合当前请求（由子类实现），最后根据mapping从mappingRegistry
			// 的mappingLookup获取HandlerMethod
			HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
			if (logger.isDebugEnabled()) {
				if (handlerMethod != null) {
					logger.debug("Returning handler method [" + handlerMethod + "]");
				}
				else {
					logger.debug("Did not find handler method for [" + lookupPath + "]");
				}
			}
			// createWithResolvedBean方法根据handlerMethod保存的beanName从beanFactory获取bean保存下来
			return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
		}
		finally {
			this.mappingRegistry.releaseReadLock();
		}
	}

	@Nullable
	protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
		// Match对象持有HandlerMethod对象和mapping对象
		List<Match> matches = new ArrayList<>();
		// 先尝试从mappingRegistry中的urlLookup获取mapping
		List<T> directPathMatches = this.mappingRegistry.getMappingsByUrl(lookupPath);
		if (directPathMatches != null) {
			// 遍历请求路径对应的Mapping，符合条件则创建Match对象并保存到matches中，是否符合条件由子类实现
			addMatchingMappings(directPathMatches, matches, request);
		}
		// 如果没有找到符合条件的则在所有的mapping中再找一次
		if (matches.isEmpty()) {
			// No choice but to go through all mappings...
			addMatchingMappings(this.mappingRegistry.getMappings().keySet(), matches, request);
		}

		if (!matches.isEmpty()) {
			// getMappingComparator方法返回一个能够针对mapping，也就是T的Comparator
			Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
			matches.sort(comparator);
			if (logger.isTraceEnabled()) {
				logger.trace("Found " + matches.size() + " matching mapping(s) for [" + lookupPath + "] : " + matches);
			}
			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				// preflight request的请求什么都不用做，返回EmptyHandler
				if (CorsUtils.isPreFlightRequest(request)) {
					return PREFLIGHT_AMBIGUOUS_MATCH;
				}
				Match secondBestMatch = matches.get(1);
				// 如果有多个匹配则判断是否存在相等优先级的Match
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {
					Method m1 = bestMatch.handlerMethod.getMethod();
					Method m2 = secondBestMatch.handlerMethod.getMethod();
					throw new IllegalStateException("Ambiguous handler methods mapped for HTTP path '" +
							request.getRequestURL() + "': {" + m1 + ", " + m2 + "}");
				}
			}
			// 排序后默认使用第一个作为结果，这里的handleMatch用于在找到匹配结果时执行回调，默认实现是将lookupPath保存到request的属性中
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.handlerMethod;
		}
		else {
			// 如果没有匹配的则执行下面的方法返回一个默认值，默认为空
			return handleNoMatch(this.mappingRegistry.getMappings().keySet(), lookupPath, request);
		}
	}

	private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
		for (T mapping : mappings) {
			// 供子类实现，用于判断mapping是否匹配当前请求
			T match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, this.mappingRegistry.getMappings().get(mapping)));
			}
		}
	}

	protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
		request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
	}

	@Nullable
	protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath, HttpServletRequest request)
			throws Exception {

		return null;
	}

	@Override
	protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
		CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = (HandlerMethod) handler;
			// 和lookupHandlerMethod方法想对应，当HandlerMethod是PREFLIGHT_AMBIGUOUS_MATCH时，表示允许所有的cors访问
			if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
				return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
			}
			else {
				// 对于其他HandlerMethod，获取mappingRegistry中保存的cors配置
				CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
				corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
			}
		}
		return corsConfig;
	}


	// Abstract template methods

	protected abstract boolean isHandler(Class<?> beanType);

	@Nullable
	protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

	protected abstract Set<String> getMappingPathPatterns(T mapping);

	@Nullable
	protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

	protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);

	class MappingRegistry {

		private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

		private final Map<T, HandlerMethod> mappingLookup = new LinkedHashMap<>();

		private final MultiValueMap<String, T> urlLookup = new LinkedMultiValueMap<>();

		private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

		private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

		private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

		public Map<T, HandlerMethod> getMappings() {
			return this.mappingLookup;
		}
    
		@Nullable
		public List<T> getMappingsByUrl(String urlPath) {
			return this.urlLookup.get(urlPath);
		}
    
		public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
			return this.nameLookup.get(mappingName);
		}
    
		public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
			HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
			return this.corsLookup.get(original != null ? original : handlerMethod);
		}
    
		public void acquireReadLock() {
			this.readWriteLock.readLock().lock();
		}
    
		public void releaseReadLock() {
			this.readWriteLock.readLock().unlock();
		}

		public void register(T mapping, Object handler, Method method) {
			this.readWriteLock.writeLock().lock();
			try {
				// HandlerMethod维护了handler和method
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
				// 确保HandlerMethod对象唯一
				assertUniqueMethodMapping(handlerMethod, mapping);

				if (logger.isInfoEnabled()) {
					logger.info("Mapped \"" + mapping + "\" onto " + handlerMethod);
				}
				// 维护了一个mapping和handlerMethod的映射
				this.mappingLookup.put(mapping, handlerMethod);

				// 获取mapping对应的直接请求路径，即其能够直接处理的请求路径，如/user，而/user/*这样的请求路径不会返回
				List<String> directUrls = getDirectUrls(mapping);
				for (String url : directUrls) {
					// 维护了一个直接请求路径和mapping的映射
					this.urlLookup.add(url, mapping);
				}

				String name = null;
				if (getNamingStrategy() != null) {
					name = getNamingStrategy().getName(handlerMethod, mapping);
					// 名称与handlerMethod的关联关系到nameLookup，既
					// 维护了一个名称和handlerMethod
					addMappingName(name, handlerMethod);
				}

				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					// 维护了一个handlerMethod和cors配置的映射
					this.corsLookup.put(handlerMethod, corsConfig);
				}

				// MappingRegistration对象维护了上面获取到的所有当前handler和method的相关信息
				// 这里维护了一个mapping和MappingRegistration的映射
				this.registry.put(mapping, new MappingRegistration<>(mapping, handlerMethod, directUrls, name));
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void assertUniqueMethodMapping(HandlerMethod newHandlerMethod, T mapping) {
			HandlerMethod handlerMethod = this.mappingLookup.get(mapping);
			if (handlerMethod != null && !handlerMethod.equals(newHandlerMethod)) {
				throw new IllegalStateException(
						"Ambiguous mapping. Cannot map '" +	newHandlerMethod.getBean() + "' method \n" +
						newHandlerMethod + "\nto " + mapping + ": There is already '" +
						handlerMethod.getBean() + "' bean method\n" + handlerMethod + " mapped.");
			}
		}

		private List<String> getDirectUrls(T mapping) {
			List<String> urls = new ArrayList<>(1);
			// getMappingPathPatterns解析mapping对象对应的请求路径，供子类实现
			for (String path : getMappingPathPatterns(mapping)) {
				// 如果获取到的路径不是个pattern，则添加到结果集
				if (!getPathMatcher().isPattern(path)) {
					urls.add(path);
				}
			}
			return urls;
		}

		private void addMappingName(String name, HandlerMethod handlerMethod) {
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				oldList = Collections.emptyList();
			}

			for (HandlerMethod current : oldList) {
				if (handlerMethod.equals(current)) {
					return;
				}
			}

			if (logger.isTraceEnabled()) {
				logger.trace("Mapping name '" + name + "'");
			}

			List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
			newList.addAll(oldList);
			newList.add(handlerMethod);
			this.nameLookup.put(name, newList);

			if (newList.size() > 1) {
				if (logger.isTraceEnabled()) {
					logger.trace("Mapping name clash for handlerMethods " + newList +
							". Consider assigning explicit names.");
				}
			}
		}

		public void unregister(T mapping) {
			this.readWriteLock.writeLock().lock();
			try {
				MappingRegistration<T> definition = this.registry.remove(mapping);
				if (definition == null) {
					return;
				}

				this.mappingLookup.remove(definition.getMapping());

				for (String url : definition.getDirectUrls()) {
					List<T> list = this.urlLookup.get(url);
					if (list != null) {
						list.remove(definition.getMapping());
						if (list.isEmpty()) {
							this.urlLookup.remove(url);
						}
					}
				}

				removeMappingName(definition);

				this.corsLookup.remove(definition.getHandlerMethod());
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}

		private void removeMappingName(MappingRegistration<T> definition) {
			String name = definition.getMappingName();
			if (name == null) {
				return;
			}
			HandlerMethod handlerMethod = definition.getHandlerMethod();
			List<HandlerMethod> oldList = this.nameLookup.get(name);
			if (oldList == null) {
				return;
			}
			if (oldList.size() <= 1) {
				this.nameLookup.remove(name);
				return;
			}
			List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
			for (HandlerMethod current : oldList) {
				if (!current.equals(handlerMethod)) {
					newList.add(current);
				}
			}
			this.nameLookup.put(name, newList);
		}
	}


	private static class MappingRegistration<T> {

		private final T mapping;

		private final HandlerMethod handlerMethod;

		private final List<String> directUrls;

		@Nullable
		private final String mappingName;

		public MappingRegistration(T mapping, HandlerMethod handlerMethod,
				@Nullable List<String> directUrls, @Nullable String mappingName) {

			Assert.notNull(mapping, "Mapping must not be null");
			Assert.notNull(handlerMethod, "HandlerMethod must not be null");
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
			this.directUrls = (directUrls != null ? directUrls : Collections.emptyList());
			this.mappingName = mappingName;
		}

		public T getMapping() {
			return this.mapping;
		}

		public HandlerMethod getHandlerMethod() {
			return this.handlerMethod;
		}

		public List<String> getDirectUrls() {
			return this.directUrls;
		}

		@Nullable
		public String getMappingName() {
			return this.mappingName;
		}
	}
  
	private class Match {

		private final T mapping;

		private final HandlerMethod handlerMethod;

		public Match(T mapping, HandlerMethod handlerMethod) {
			this.mapping = mapping;
			this.handlerMethod = handlerMethod;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}


	private class MatchComparator implements Comparator<Match> {

		private final Comparator<T> comparator;

		public MatchComparator(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}


	private static class EmptyHandler {

		@SuppressWarnings("unused")
		public void handle() {
			throw new UnsupportedOperationException("Not implemented");
		}
	}

}
```

[AbstractHandlerMethodMapping]类在定义了自动解析mapping和获取mapping和对应的bean的基本逻辑的同时，定义了几个重要的抽象方法：
```java
// 判断指定类是否是一个handler
protected abstract boolean isHandler(Class<?> beanType);
// 获取mapping
protected abstract T getMappingForMethod(Method method, Class<?> handlerType);
// 获取mapping对应的请求路径
protected abstract Set<String> getMappingPathPatterns(T mapping);
// 判断mapping是否匹配当前请求，匹配则不返回空
protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);
```

[RequestMappingInfoHandlerMapping]类实现了上面除了`isHandler()`和`getMappingForMethod()`方法以外的其他抽象方法，将[AbstractHandlerMethodMapping]类中还未确定的范型设置为[RequestMappingInfo]，并重写了`handleMatch()`和`handleNoMatch()`方法，对MatrixVariable注解相关功能提供了支持，代码：
```java
public abstract class RequestMappingInfoHandlerMapping extends AbstractHandlerMethodMapping<RequestMappingInfo> {

	private static final Method HTTP_OPTIONS_HANDLE_METHOD;

	static {
		try {
			HTTP_OPTIONS_HANDLE_METHOD = HttpOptionsHandler.class.getMethod("handle");
		}
		catch (NoSuchMethodException ex) {
			// Should never happen
			throw new IllegalStateException("Failed to retrieve internal handler method for HTTP OPTIONS", ex);
		}
	}


	protected RequestMappingInfoHandlerMapping() {
		setHandlerMethodMappingNamingStrategy(new RequestMappingInfoHandlerMethodMappingNamingStrategy());
	}

	@Override
	protected Set<String> getMappingPathPatterns(RequestMappingInfo info) {
		return info.getPatternsCondition().getPatterns();
	}

	@Override
	protected RequestMappingInfo getMatchingMapping(RequestMappingInfo info, HttpServletRequest request) {
		return info.getMatchingCondition(request);
	}

	@Override
	protected Comparator<RequestMappingInfo> getMappingComparator(final HttpServletRequest request) {
		return (info1, info2) -> info1.compareTo(info2, request);
	}

	@Override
	// 在找到匹配到的mapping时执行
	protected void handleMatch(RequestMappingInfo info, String lookupPath, HttpServletRequest request) {
		super.handleMatch(info, lookupPath, request);

		String bestPattern;
		Map<String, String> uriVariables;
		Map<String, String> decodedUriVariables;

		Set<String> patterns = info.getPatternsCondition().getPatterns();
		// 如果mapping配置的路径为空
		if (patterns.isEmpty()) {
			bestPattern = lookupPath;
			uriVariables = Collections.emptyMap();
			decodedUriVariables = Collections.emptyMap();
		}
		else {
			bestPattern = patterns.iterator().next();
			// 按照ant风格解析mapping路径，获取路径中的变量，如这里的为参数/{test}/addUser和/demo/addUser，则返回test -> demo
			uriVariables = getPathMatcher().extractUriTemplateVariables(bestPattern, lookupPath);
			// 对参数进行url解码，默认不进行解码
			decodedUriVariables = getUrlPathHelper().decodePathVariables(request, uriVariables);
		}

		request.setAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE, bestPattern);
		request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, decodedUriVariables);

		// 是否移除请求路径中的分号，默认为true，对于为true的情况下，可以设置enable-matrix-variables为true以开启MatrixVariable的支持，如
		// <mvc:annotation-driven enable-matrix-variables="true"/>
		if (isMatrixVariableContentAvailable()) {
			// extractMatrixVariables方法解析请求路径中的matrixVariables，保存每个请求路径后面的matrixVariables到map，并将请求路径
			// 参数名作为key，map作为value返回
			Map<String, MultiValueMap<String, String>> matrixVars = extractMatrixVariables(request, uriVariables);
			request.setAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE, matrixVars);
		}

		if (!info.getProducesCondition().getProducibleMediaTypes().isEmpty()) {
			// 如果RequestMapping注解设置了produces属性限制请求的MediaType，则在这里保存到request中
			Set<MediaType> mediaTypes = info.getProducesCondition().getProducibleMediaTypes();
			request.setAttribute(PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE, mediaTypes);
		}
	}

	private boolean isMatrixVariableContentAvailable() {
		return !getUrlPathHelper().shouldRemoveSemicolonContent();
	}

	private Map<String, MultiValueMap<String, String>> extractMatrixVariables(
			HttpServletRequest request, Map<String, String> uriVariables) {

		Map<String, MultiValueMap<String, String>> result = new LinkedHashMap<>();
		uriVariables.forEach((uriVarKey, uriVarValue) -> {
			int equalsIndex = uriVarValue.indexOf('=');
			if (equalsIndex == -1) {
				return;
			}

			String matrixVariables;

			int semicolonIndex = uriVarValue.indexOf(';');
			if ((semicolonIndex == -1) || (semicolonIndex == 0) || (equalsIndex < semicolonIndex)) {
				matrixVariables = uriVarValue;
			}
			else {
				// 保存请求路径参数后面的matrixVariables
				matrixVariables = uriVarValue.substring(semicolonIndex + 1);
				// 截掉请求路径参数后面的matrixVariables
				uriVariables.put(uriVarKey, uriVarValue.substring(0, semicolonIndex));
			}

			MultiValueMap<String, String> vars = WebUtils.parseMatrixVariables(matrixVariables);
			result.put(uriVarKey, getUrlPathHelper().decodeMatrixVariables(request, vars));
		});
		return result;
	}

	@Override
	// handleNoMatch方法在没有找到合适的mapping时被执行，执行的主要目的是告诉客户端，为什么没有找到匹配的mapping
	protected HandlerMethod handleNoMatch(
			Set<RequestMappingInfo> infos, String lookupPath, HttpServletRequest request) throws ServletException {

		/*
		 PartialMatchHelper对象在构造函数中遍历所有的RequestMappingInfo，如果RequestMappingInfo对应的路径能够匹配当前的请求路径
		 则添加到PartialMatchHelper对象的partialMatches中，在判断是否匹配的过程中，会将RequestMappingInfo对应的路径，既pattern
		 分别用4中判断是否匹配：
		 1.直接匹配，既pattern是否等于请求路径
		 2.在pattern后加上.*，需要useSuffixPatternMatch属性为true
		 3.按照ant风格解析进行匹配 既将pattern理解为ant表达式
		 4.在pattern后加上/进行匹配 需要useTrailingSlashMatch属性为true

		 每个RequestMappingInfo的所有pattern都会用上面4尝试匹配（如果useSuffixPatternMatch和useTrailingSlashMatch都为true），如果某种情况成功
		 匹配上了，则跟其RequestMappingInfo一块保存到PartialMatchHelper对象的partialMatches属性中
		 */

		PartialMatchHelper helper = new PartialMatchHelper(infos, request);
		if (helper.isEmpty()) {
			return null;
		}

		// 如果partialMatches中包含的RequestMappingInfo中没有和当前请求的method相对应的
		if (helper.hasMethodsMismatch()) {
			// 取所有RequestMappingInfo的method属性的并集
			Set<String> methods = helper.getAllowedMethods();
			// 如果当前请求是option请求，则直接返回HttpOptionsHandler，并将从RequestMappingInfo中找到的method属性的并集保存到HttpOptionsHandler
			// 中用于响应
			if (HttpMethod.OPTIONS.matches(request.getMethod())) {
				HttpOptionsHandler handler = new HttpOptionsHandler(methods);
				return new HandlerMethod(handler, HTTP_OPTIONS_HANDLE_METHOD);
			}
			// 如果当前请求是不option的，而partialMatches中包含的RequestMappingInfo中又没有method与当前请求的method相匹配，则抛出异常
			throw new HttpRequestMethodNotSupportedException(request.getMethod(), methods);
		}

		/*
		HTTP协议Header中有两个东西：ContentType和Accept，在请求中，ContentType用来告诉服务器当前发送的数据是什么格式，
		Accept用来告诉服务器，客户端能认识哪些格式，而在RequestMapping注解中可以对请求的这两个参数进行限制，consumes用来限制ContentType，produces用来限制Accept
		 */

		// 如果partialMatches中包含的RequestMappingInfo中存在method和consumes指定的ContentType与当前请求的method和ContentType相匹配，则这里返回false
		// 如果这里返回true，说明没有RequestMappingInfo的consumes与当前请求的ContentType相匹配，也就是不支持当前请求的ContentType中定义的格式
		if (helper.hasConsumesMismatch()) {
			// 获取method与当前请求的method相匹配的RequestMappingInfo配置的consumes属性值，也就是其支持的ContentType对应的MediaType列表
			Set<MediaType> mediaTypes = helper.getConsumableMediaTypes();
			MediaType contentType = null;
			if (StringUtils.hasLength(request.getContentType())) {
				try {
					contentType = MediaType.parseMediaType(request.getContentType());
				}
				catch (InvalidMediaTypeException ex) {
					throw new HttpMediaTypeNotSupportedException(ex.getMessage());
				}
			}
			// 将所有找到的MediaType作为异常抛出，提示客户端应该使用什么MediaType作为ContentType
			throw new HttpMediaTypeNotSupportedException(contentType, new ArrayList<>(mediaTypes));
		}

		// 同上，这里针对的是Accept
		if (helper.hasProducesMismatch()) {
			Set<MediaType> mediaTypes = helper.getProducibleMediaTypes();
			throw new HttpMediaTypeNotAcceptableException(new ArrayList<>(mediaTypes));
		}

		// 最后是判断RequestMappingInfo中是否存在param与当前请求的请求路径参数是否匹配，如有如下RequestMapping注解配置：
		// @GetMapping(value = "/show-value", params = {"a=1", "c=3"})，对于请求show-value?a=1&b=2将会被拒绝，show-value?a=1&c=3才可以
		if (helper.hasParamsMismatch()) {
			List<String[]> conditions = helper.getParamConditions();
			throw new UnsatisfiedServletRequestParameterException(conditions, request.getParameterMap());
		}

		return null;
	}

	private static class PartialMatchHelper {

		private final List<PartialMatch> partialMatches = new ArrayList<>();

		public PartialMatchHelper(Set<RequestMappingInfo> infos, HttpServletRequest request) {
			for (RequestMappingInfo info : infos) {
				if (info.getPatternsCondition().getMatchingCondition(request) != null) {
					this.partialMatches.add(new PartialMatch(info, request));
				}
			}
		}

		public boolean isEmpty() {
			return this.partialMatches.isEmpty();
		}

		public boolean hasMethodsMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasMethodsMatch()) {
					return false;
				}
			}
			return true;
		}

		public boolean hasConsumesMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasConsumesMatch()) {
					return false;
				}
			}
			return true;
		}

		public boolean hasProducesMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasProducesMatch()) {
					return false;
				}
			}
			return true;
		}

		public boolean hasParamsMismatch() {
			for (PartialMatch match : this.partialMatches) {
				if (match.hasParamsMatch()) {
					return false;
				}
			}
			return true;
		}

		public Set<String> getAllowedMethods() {
			Set<String> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				for (RequestMethod method : match.getInfo().getMethodsCondition().getMethods()) {
					result.add(method.name());
				}
			}
			return result;
		}

		public Set<MediaType> getConsumableMediaTypes() {
			Set<MediaType> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasMethodsMatch()) {
					result.addAll(match.getInfo().getConsumesCondition().getConsumableMediaTypes());
				}
			}
			return result;
		}

		public Set<MediaType> getProducibleMediaTypes() {
			Set<MediaType> result = new LinkedHashSet<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasConsumesMatch()) {
					result.addAll(match.getInfo().getProducesCondition().getProducibleMediaTypes());
				}
			}
			return result;
		}

		public List<String[]> getParamConditions() {
			List<String[]> result = new ArrayList<>();
			for (PartialMatch match : this.partialMatches) {
				if (match.hasProducesMatch()) {
					Set<NameValueExpression<String>> set = match.getInfo().getParamsCondition().getExpressions();
					if (!CollectionUtils.isEmpty(set)) {
						int i = 0;
						String[] array = new String[set.size()];
						for (NameValueExpression<String> expression : set) {
							array[i++] = expression.toString();
						}
						result.add(array);
					}
				}
			}
			return result;
		}

		private static class PartialMatch {

			private final RequestMappingInfo info;

			private final boolean methodsMatch;

			private final boolean consumesMatch;

			private final boolean producesMatch;

			private final boolean paramsMatch;

			public PartialMatch(RequestMappingInfo info, HttpServletRequest request) {
				this.info = info;
				this.methodsMatch = (info.getMethodsCondition().getMatchingCondition(request) != null);
				this.consumesMatch = (info.getConsumesCondition().getMatchingCondition(request) != null);
				this.producesMatch = (info.getProducesCondition().getMatchingCondition(request) != null);
				this.paramsMatch = (info.getParamsCondition().getMatchingCondition(request) != null);
			}

			public RequestMappingInfo getInfo() {
				return this.info;
			}

			public boolean hasMethodsMatch() {
				return this.methodsMatch;
			}

			public boolean hasConsumesMatch() {
				return (hasMethodsMatch() && this.consumesMatch);
			}

			public boolean hasProducesMatch() {
				return (hasConsumesMatch() && this.producesMatch);
			}

			public boolean hasParamsMatch() {
				return (hasProducesMatch() && this.paramsMatch);
			}

			@Override
			public String toString() {
				return this.info.toString();
			}
		}
	}

	private static class HttpOptionsHandler {

		private final HttpHeaders headers = new HttpHeaders();

		public HttpOptionsHandler(Set<String> declaredMethods) {
			this.headers.setAllow(initAllowedHttpMethods(declaredMethods));
		}

		private static Set<HttpMethod> initAllowedHttpMethods(Set<String> declaredMethods) {
			Set<HttpMethod> result = new LinkedHashSet<>(declaredMethods.size());
			if (declaredMethods.isEmpty()) {
				for (HttpMethod method : HttpMethod.values()) {
					if (method != HttpMethod.TRACE) {
						result.add(method);
					}
				}
			}
			else {
				for (String method : declaredMethods) {
					HttpMethod httpMethod = HttpMethod.valueOf(method);
					result.add(httpMethod);
					if (httpMethod == HttpMethod.GET) {
						result.add(HttpMethod.HEAD);
					}
				}
				result.add(HttpMethod.OPTIONS);
			}
			return result;
		}

		@SuppressWarnings("unused")
		public HttpHeaders handle() {
			return this.headers;
		}
	}

}
```

最后是[RequestMappingHandlerMapping]类，[RequestMappingHandlerMapping]定义了一些决定mapping匹配行为的变量，如`useSuffixPatternMatch`，实现了`isHandler`和`getMappingForMethod()`方法，
```java
public class RequestMappingHandlerMapping extends RequestMappingInfoHandlerMapping
		implements MatchableHandlerMapping, EmbeddedValueResolverAware {

	// 是否在路径匹配模式后添加.*，即/login或者/login.do等请求是否都映射到/login
	private boolean useSuffixPatternMatch = true;

	/*
	使用@PathVariable注解时如果参数中有.点号，那么参数会被截断，如

	@Controller
	@RequestMapping("/example")
	public class ExampleController {

	    @RequestMapping("/{param}")
	    public void test(@PathVariable("param") String param){
	        System.out.println(param);
	    }
	}

	对于不同的url，@PathVariable得到的参数为：

	/example/test          => text
	/example/test.ext      => text
	/example/test.ext.ext2 => text.ext

	将useRegisteredSuffixPatternMatch设置为true即可解决这个问题
	 */
	private boolean useRegisteredSuffixPatternMatch = false;

	// 方法声明为/users时是否也匹配/users/请求
	private boolean useTrailingSlashMatch = true;

	// ContentNegotiationManager用于获取请求对应的MediaType
	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	@Nullable
	private StringValueResolver embeddedValueResolver;

	private RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();

	public void setUseSuffixPatternMatch(boolean useSuffixPatternMatch) {
		this.useSuffixPatternMatch = useSuffixPatternMatch;
	}
  
	public void setUseRegisteredSuffixPatternMatch(boolean useRegisteredSuffixPatternMatch) {
		this.useRegisteredSuffixPatternMatch = useRegisteredSuffixPatternMatch;
		this.useSuffixPatternMatch = (useRegisteredSuffixPatternMatch || this.useSuffixPatternMatch);
	}
  
	public void setUseTrailingSlashMatch(boolean useTrailingSlashMatch) {
		this.useTrailingSlashMatch = useTrailingSlashMatch;
	}
  
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		Assert.notNull(contentNegotiationManager, "ContentNegotiationManager must not be null");
		this.contentNegotiationManager = contentNegotiationManager;
	}

	@Override
	public void setEmbeddedValueResolver(StringValueResolver resolver) {
		this.embeddedValueResolver = resolver;
	}

	@Override
	public void afterPropertiesSet() {
		this.config = new RequestMappingInfo.BuilderConfiguration();
		this.config.setUrlPathHelper(getUrlPathHelper());
		this.config.setPathMatcher(getPathMatcher());
		this.config.setSuffixPatternMatch(this.useSuffixPatternMatch);
		this.config.setTrailingSlashMatch(this.useTrailingSlashMatch);
		this.config.setRegisteredSuffixPatternMatch(this.useRegisteredSuffixPatternMatch);
		this.config.setContentNegotiationManager(getContentNegotiationManager());

		super.afterPropertiesSet();
	}
  
	public boolean useSuffixPatternMatch() {
		return this.useSuffixPatternMatch;
	}
  
	public boolean useRegisteredSuffixPatternMatch() {
		return this.useRegisteredSuffixPatternMatch;
	}
  
	public boolean useTrailingSlashMatch() {
		return this.useTrailingSlashMatch;
	}
  
	public ContentNegotiationManager getContentNegotiationManager() {
		return this.contentNegotiationManager;
	}
  
	@Nullable
	public List<String> getFileExtensions() {
		return this.config.getFileExtensions();
	}
  
	@Override
	protected boolean isHandler(Class<?> beanType) {
		// 如果类上存在下面两个注解中的一个则认为是handler
		return (AnnotatedElementUtils.hasAnnotation(beanType, Controller.class) ||
				AnnotatedElementUtils.hasAnnotation(beanType, RequestMapping.class));
	}
  
	@Override
	@Nullable
	protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
		RequestMappingInfo info = createRequestMappingInfo(method);
		if (info != null) {
			RequestMappingInfo typeInfo = createRequestMappingInfo(handlerType);
			if (typeInfo != null) {
				info = typeInfo.combine(info);
			}
		}
		return info;
	}
  
	@Nullable
	private RequestMappingInfo createRequestMappingInfo(AnnotatedElement element) {
		// 获取方法或类上的RequestMapping注解，如果是方法上的注解，则会合并类上RequestMapping注解信息
		RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(element, RequestMapping.class);
		// 获取自定义的RequestCondition，默认为空
		RequestCondition<?> condition = (element instanceof Class ?
				getCustomTypeCondition((Class<?>) element) : getCustomMethodCondition((Method) element));
		return (requestMapping != null ? createRequestMappingInfo(requestMapping, condition) : null);
	}
  
	@Nullable
	protected RequestCondition<?> getCustomTypeCondition(Class<?> handlerType) {
		return null;
	}
  
	@Nullable
	protected RequestCondition<?> getCustomMethodCondition(Method method) {
		return null;
	}
  
	protected RequestMappingInfo createRequestMappingInfo(
			RequestMapping requestMapping, @Nullable RequestCondition<?> customCondition) {

		// 保存RequestMapping注解对应的相关信息
		RequestMappingInfo.Builder builder = RequestMappingInfo
				.paths(resolveEmbeddedValuesInPatterns(requestMapping.path()))
				.methods(requestMapping.method())
				.params(requestMapping.params())
				.headers(requestMapping.headers())
				.consumes(requestMapping.consumes())
				.produces(requestMapping.produces())
				.mappingName(requestMapping.name());
		if (customCondition != null) {
			builder.customCondition(customCondition);
		}

		// 创建RequestMappingInfo对象，RequestMappingInfo对象包含了RequestMapping注解对应的所有信息，对于RequestMapping的每个属性，
		// RequestMappingInfo对象都会创建一个RequestCondition对象，如ParamsRequestCondition
		return builder.options(this.config).build();
	}
  
	protected String[] resolveEmbeddedValuesInPatterns(String[] patterns) {
		if (this.embeddedValueResolver == null) {
			return patterns;
		}
		else {
			String[] resolvedPatterns = new String[patterns.length];
			for (int i = 0; i < patterns.length; i++) {
				resolvedPatterns[i] = this.embeddedValueResolver.resolveStringValue(patterns[i]);
			}
			return resolvedPatterns;
		}
	}

	@Override
	public RequestMatchResult match(HttpServletRequest request, String pattern) {
		RequestMappingInfo info = RequestMappingInfo.paths(pattern).options(this.config).build();
		RequestMappingInfo matchingInfo = info.getMatchingCondition(request);
		if (matchingInfo == null) {
			return null;
		}
		Set<String> patterns = matchingInfo.getPatternsCondition().getPatterns();
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		return new RequestMatchResult(patterns.iterator().next(), lookupPath, getPathMatcher());
	}

	@Override
	protected CorsConfiguration initCorsConfiguration(Object handler, Method method, RequestMappingInfo mappingInfo) {
		HandlerMethod handlerMethod = createHandlerMethod(handler, method);
		Class<?> beanType = handlerMethod.getBeanType();
		// 获取类和方法上的CrossOrigin注解
		CrossOrigin typeAnnotation = AnnotatedElementUtils.findMergedAnnotation(beanType, CrossOrigin.class);
		CrossOrigin methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, CrossOrigin.class);

		if (typeAnnotation == null && methodAnnotation == null) {
			return null;
		}

		// 合并两个注解信息
		CorsConfiguration config = new CorsConfiguration();
		updateCorsConfig(config, typeAnnotation);
		updateCorsConfig(config, methodAnnotation);

		if (CollectionUtils.isEmpty(config.getAllowedMethods())) {
			for (RequestMethod allowedMethod : mappingInfo.getMethodsCondition().getMethods()) {
				config.addAllowedMethod(allowedMethod.name());
			}
		}
		// 为CorsConfiguration设置默认值
		return config.applyPermitDefaultValues();
	}

	private void updateCorsConfig(CorsConfiguration config, @Nullable CrossOrigin annotation) {
		if (annotation == null) {
			return;
		}
		for (String origin : annotation.origins()) {
			config.addAllowedOrigin(resolveCorsAnnotationValue(origin));
		}
		for (RequestMethod method : annotation.methods()) {
			config.addAllowedMethod(method.name());
		}
		for (String header : annotation.allowedHeaders()) {
			config.addAllowedHeader(resolveCorsAnnotationValue(header));
		}
		for (String header : annotation.exposedHeaders()) {
			config.addExposedHeader(resolveCorsAnnotationValue(header));
		}

		String allowCredentials = resolveCorsAnnotationValue(annotation.allowCredentials());
		if ("true".equalsIgnoreCase(allowCredentials)) {
			config.setAllowCredentials(true);
		}
		else if ("false".equalsIgnoreCase(allowCredentials)) {
			config.setAllowCredentials(false);
		}
		else if (!allowCredentials.isEmpty()) {
			throw new IllegalStateException("@CrossOrigin's allowCredentials value must be \"true\", \"false\", " +
					"or an empty string (\"\"): current value is [" + allowCredentials + "]");
		}

		if (annotation.maxAge() >= 0 && config.getMaxAge() == null) {
			config.setMaxAge(annotation.maxAge());
		}
	}

	private String resolveCorsAnnotationValue(String value) {
		if (this.embeddedValueResolver != null) {
			String resolved = this.embeddedValueResolver.resolveStringValue(value);
			return (resolved != null ? resolved : "");
		}
		else {
			return value;
		}
	}

}
```

结合笔记[如何实现请求的分发和响应](如何实现请求的分发和响应.md)和[SimpleUrlHandlerMapping的实现](SimpleUrlHandlerMapping的实现.md)，再看这里的源码分析，就可以理解[RequestMappingHandlerMapping]类的作用的工作原理，简单来说，[RequestMappingHandlerMapping]类在发现bean的类上有Controller或RequestMapping注解，则认为其可以作为一个请求执行类，既handler，[RequestMappingHandlerMapping]将RequestMapping注解的配置封装为[RequestMappingInfo]对象，其父类[AbstractHandlerMethodMapping]解析所有[RequestMappingInfo]对象，找到每个[RequestMappingInfo]对象对应的方法和其能够处理的请求路径保存下来，在请求到来时返回

[RequestMappingHandlerMapping]: aaa